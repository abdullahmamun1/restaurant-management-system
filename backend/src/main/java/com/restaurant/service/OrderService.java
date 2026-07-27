package com.restaurant.service;

import com.restaurant.controller.dto.OrderDto;
import com.restaurant.controller.dto.TableDto;
import com.restaurant.domain.MenuItem;
import com.restaurant.domain.Order;
import com.restaurant.domain.OrderItem;
import com.restaurant.domain.OrderStatus;
import com.restaurant.domain.RestaurantTable;
import com.restaurant.domain.User;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.repository.OrderRepository;
import com.restaurant.repository.RestaurantTableRepository;
import com.restaurant.repository.UserRepository;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.validation.OrderItemValidationChain;
import com.restaurant.service.validation.OrderItemValidationContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Table & order business logic (FR-06–FR-10) plus the kitchen's lifecycle writes (FR-12).
 *
 * <p>All lifecycle orchestration lives here regardless of which role triggers it, so there is one
 * place that loads an order and asks it to change state. The kitchen's <em>read</em> side is
 * separate — see {@link KitchenService} — because the queue is a different projection, not a
 * different rule.
 *
 * <p>This service deliberately holds no lifecycle rules of its own: the {@code State} logic on
 * {@link OrderStatus}/{@link Order} decides what transitions and edits are legal, and the
 * {@link OrderItemValidationChain} (Chain of Responsibility) decides whether an item may be added
 * (FR-08). What lives here is orchestration — loading aggregates, locking the table row, resolving
 * the acting waiter, and the {@code @Transactional} boundary.
 *
 * <p>Every mutating method returns the freshly mapped {@link OrderDto} so the client re-syncs in a
 * single round-trip.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestaurantTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final OrderItemValidationChain validationChain;
    private final OrderMapper mapper;

    public OrderService(OrderRepository orderRepository,
                        RestaurantTableRepository tableRepository,
                        MenuItemRepository menuItemRepository,
                        UserRepository userRepository,
                        OrderItemValidationChain validationChain,
                        OrderMapper mapper) {
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.validationChain = validationChain;
        this.mapper = mapper;
    }

    // ---- Tables -------------------------------------------------------------

    /**
     * The floor view: every table with its own status and the open order on it, if any.
     *
     * <p>One query for the open orders rather than one per table, indexed into by table id. The
     * {@code uq_active_order_per_table} partial unique index is what makes "at most one" safe to
     * assume here (FR-06).
     */
    @Transactional(readOnly = true)
    public List<TableDto> listTables() {
        Map<Long, Order> activeOrderByTable = new HashMap<>();
        for (Order open : orderRepository.findByStatusNot(OrderStatus.PAID)) {
            activeOrderByTable.put(open.getTable().getId(), open);
        }
        return tableRepository.findAllByOrderByLabelAsc().stream()
                .map(table -> mapper.toDto(table, activeOrderByTable.get(table.getId())))
                .toList();
    }

    /** Raises or clears a table's NEEDS_SERVICE flag. */
    @Transactional
    public TableDto setServiceFlag(Long tableId, boolean needsService) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table " + tableId + " not found."));
        table.setServiceFlag(needsService);
        Order activeOrder = orderRepository
                .findByTableIdAndStatusNot(tableId, OrderStatus.PAID)
                .orElse(null);
        return mapper.toDto(table, activeOrder);
    }

    // ---- Orders -------------------------------------------------------------

    /**
     * Opens a PENDING order on a table and marks the table OCCUPIED (FR-06).
     *
     * <p>The table row is locked for the duration so two waiters racing the same table are
     * serialized (NFR-07) — the loser sees the table as already OCCUPIED and is rejected by
     * {@link RestaurantTable#occupy()}. The {@code uq_active_order_per_table} partial unique index
     * is the second line of defence.
     */
    @Transactional
    public OrderDto createOrder(Long tableId, String waiterUsername) {
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table " + tableId + " not found."));
        User waiter = requireUser(waiterUsername);

        table.occupy(); // throws if the table already has an open order
        Order order = orderRepository.save(new Order(table, waiter));
        return mapper.toDto(order);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(Long orderId) {
        return mapper.toDto(requireOrderWithItems(orderId));
    }

    /** Read access for the Manager/Kitchen/Cashier views of Table & Orders (SRS §2.1). */
    @Transactional(readOnly = true)
    public List<OrderDto> listOrders(OrderStatus status, Long tableId) {
        List<Order> orders;
        if (status != null && tableId != null) {
            orders = orderRepository.findByStatusAndTableIdOrderByCreatedAtDesc(status, tableId);
        } else if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if (tableId != null) {
            orders = orderRepository.findByTableIdOrderByCreatedAtDesc(tableId);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orders.stream().map(mapper::toDto).toList();
    }

    /**
     * Adds an item to a PENDING order (FR-07) after the FR-08 validator chain accepts it.
     *
     * <p>Validation runs before the order is touched, so a rejection leaves it unchanged as FR-08
     * requires; the transaction boundary guarantees that even if a later step were to fail.
     */
    @Transactional
    public OrderDto addItem(Long orderId, Long menuItemId, int quantity, String notes) {
        Order order = requireOrderWithItems(orderId);
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item " + menuItemId + " not found."));

        validationChain.validate(new OrderItemValidationContext(order, menuItem, quantity));

        order.addItem(menuItem, quantity, notes);
        // A new line is inserted by cascade at flush, which would otherwise happen after this
        // method returns — leaving its generated id null in the response, so the client could not
        // remove the line it had just added without re-fetching the order.
        orderRepository.flush();
        return mapper.toDto(order);
    }

    /**
     * Sets an existing line's quantity on a PENDING order (FR-07) — the waiter's +/- controls.
     *
     * <p><strong>Validation runs only when the quantity rises</strong>, and only for the
     * <em>difference</em>. That is not an optimisation, it is what makes the check correct: the
     * FR-08 stock validator already adds the order's <em>existing</em> draw (which includes this
     * line at its current quantity) to the quantity it is handed, so passing the delta asks exactly
     * "is there stock for the extra units?". Passing the new total would double-count the units
     * already on the line and refuse orders that are perfectly fulfillable.
     *
     * <p>Reducing a line needs no check at all — it can only release stock pressure, never create it.
     */
    @Transactional
    public OrderDto changeItemQuantity(Long orderId, Long orderItemId, int newQuantity) {
        Order order = requireOrderWithItems(orderId);
        OrderItem line = order.getItems().stream()
                .filter(item -> orderItemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item " + orderItemId + " is not on order " + orderId + "."));

        int delta = newQuantity - line.getQuantity();
        if (delta > 0) {
            validationChain.validate(
                    new OrderItemValidationContext(order, line.getMenuItem(), delta));
        }

        order.changeItemQuantity(orderItemId, newQuantity);
        return mapper.toDto(order);
    }

    /** Removes a line from a PENDING order (FR-07). */
    @Transactional
    public OrderDto removeItem(Long orderId, Long orderItemId) {
        Order order = requireOrderWithItems(orderId);
        if (!order.removeItem(orderItemId)) {
            throw new ResourceNotFoundException(
                    "Item " + orderItemId + " is not on order " + orderId + ".");
        }
        return mapper.toDto(order);
    }

    /** Confirms the order and sends it to the kitchen queue; items lock (FR-09). */
    @Transactional
    public OrderDto confirm(Long orderId) {
        Order order = requireOrderWithItems(orderId);
        order.confirm();
        return mapper.toDto(order);
    }

    /**
     * The kitchen has started cooking (FR-12). Legal only from CONFIRMED — the State machine
     * refuses a second call, which is the right answer when two tablets tap "start" on the same
     * ticket.
     */
    @Transactional
    public OrderDto markPreparing(Long orderId) {
        Order order = requireOrderWithItems(orderId);
        order.markPreparing();
        return mapper.toDto(order);
    }

    /** The kitchen has finished; the ticket leaves the queue (FR-12). Legal only from PREPARING. */
    @Transactional
    public OrderDto markReady(Long orderId) {
        Order order = requireOrderWithItems(orderId);
        order.markReady();
        return mapper.toDto(order);
    }

    /** Marks the order delivered to the table (FR-10). Legal only from READY. */
    @Transactional
    public OrderDto markServed(Long orderId) {
        Order order = requireOrderWithItems(orderId);
        order.markServed();
        return mapper.toDto(order);
    }

    // ---- helpers -----------------------------------------------------------

    private Order requireOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found."));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User '" + username + "' not found."));
    }
}
