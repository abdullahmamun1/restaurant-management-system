package com.restaurant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.restaurant.domain.Role;
import com.restaurant.service.AuthService;
import com.restaurant.service.BillingService;
import com.restaurant.service.InventoryService;
import com.restaurant.service.KitchenService;
import com.restaurant.service.MenuService;
import com.restaurant.service.OrderService;
import com.restaurant.service.PingService;
import com.restaurant.service.ReportingService;
import com.restaurant.service.UserService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The NFR-03 RBAC sweep: <strong>every endpoint, every role</strong> (M8 D3).
 *
 * <p>NFR-03 requires the API to "enforce RBAC on every endpoint, rejecting unauthorized requests
 * with an appropriate HTTP error response regardless of client-side controls". The only way to
 * demonstrate <em>every</em> is to drive the check from a table rather than to type the cases out:
 * a hand-written suite silently omits the row somebody forgot, and that is precisely the endpoint
 * likely to be wrong. {@link #ENDPOINTS} below is the SRS §2.1 access matrix as executable code.
 *
 * <p>Three details are load-bearing, and each one produces a green suite that proves nothing if
 * you get it wrong:
 *
 * <ul>
 *   <li><strong>Valid request bodies.</strong> Bean validation runs <em>before</em>
 *       {@code @PreAuthorize}, so a malformed body yields 400 and never reaches the guard (M8 D8).
 *       A body-carrying endpoint tested with junk asserts nothing about authorization — this bit us
 *       for real during M7's follow-up work, where a too-short username made a 400 masquerade as a
 *       passing 403 check.</li>
 *   <li><strong>Assert "not 403", not "200".</strong> The services are mocked, so an allowed call
 *       may legitimately land on 404 or 409. Demanding 200 would force this test to know things
 *       about business behaviour that are not its business.</li>
 *   <li><strong>{@link #everyEndpointIsCovered()}.</strong> Without it the sweep quietly stops
 *       being a sweep the first time somebody adds an endpoint. That test is what makes this
 *       exhaustive rather than a sample.</li>
 * </ul>
 *
 * <p>Services are mocked deliberately: this suite tests authorization and nothing else, so it must
 * not be able to fail — or pass — for reasons of data. It touches no database and runs in seconds.
 */
@SpringBootTest
class EndpointAuthorizationTest {

    /** Endpoints that are public by design (SecurityConfig) — excluded from the role sweep. */
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "POST /auth/login",
            "GET /ping",
            "GET /error");

    /**
     * Every authenticated endpoint and the roles SRS §2.1 grants it.
     *
     * <p>Read this as the access matrix: if a row here disagrees with §2.1, one of them is wrong and
     * it is worth finding out which before changing either.
     */
    private static final List<Endpoint> ENDPOINTS = List.of(
            // ---- Auth: any signed-in user may ask who they are ----
            Endpoint.of(HttpMethod.GET, "/auth/me", null, Role.values()),

            // ---- Menu: Manager full, Waiter read (§2.1 Menu Management) ----
            Endpoint.of(HttpMethod.GET, "/menu/categories", null, Role.MANAGER, Role.WAITER),
            Endpoint.of(HttpMethod.POST, "/menu/categories",
                    """
                    {"name":"Sweeps","sortOrder":99}""", Role.MANAGER),
            Endpoint.of(HttpMethod.PUT, "/menu/categories/1",
                    """
                    {"name":"Sweeps","sortOrder":99}""", Role.MANAGER),
            Endpoint.of(HttpMethod.DELETE, "/menu/categories/1", null, Role.MANAGER),
            Endpoint.of(HttpMethod.GET, "/menu/items", null, Role.MANAGER, Role.WAITER),
            Endpoint.of(HttpMethod.GET, "/menu/items/1", null, Role.MANAGER, Role.WAITER),
            Endpoint.of(HttpMethod.POST, "/menu/items",
                    """
                    {"name":"Sweep Dish","description":"d","price":9.50,"categoryId":1,"available":true}""",
                    Role.MANAGER),
            Endpoint.of(HttpMethod.PUT, "/menu/items/1",
                    """
                    {"name":"Sweep Dish","description":"d","price":9.50,"categoryId":1,"available":true}""",
                    Role.MANAGER),
            Endpoint.of(HttpMethod.PATCH, "/menu/items/1/availability",
                    """
                    {"available":false}""", Role.MANAGER),
            Endpoint.of(HttpMethod.DELETE, "/menu/items/1", null, Role.MANAGER),

            // ---- Recipes: Manager only ----
            Endpoint.of(HttpMethod.GET, "/menu/items/1/recipe", null, Role.MANAGER),
            Endpoint.of(HttpMethod.PUT, "/menu/items/1/recipe",
                    """
                    {"lines":[]}""", Role.MANAGER),

            // ---- Inventory: Manager only (§2.1 Inventory) ----
            Endpoint.of(HttpMethod.GET, "/inventory/ingredients", null, Role.MANAGER),
            Endpoint.of(HttpMethod.GET, "/inventory/ingredients/low-stock", null, Role.MANAGER),
            Endpoint.of(HttpMethod.POST, "/inventory/ingredients",
                    """
                    {"name":"Sweep Salt","unit":"g","initialStock":10,"lowStockThreshold":1}""",
                    Role.MANAGER),
            Endpoint.of(HttpMethod.PUT, "/inventory/ingredients/1",
                    """
                    {"name":"Sweep Salt","unit":"g","lowStockThreshold":1}""", Role.MANAGER),
            Endpoint.of(HttpMethod.DELETE, "/inventory/ingredients/1", null, Role.MANAGER),
            Endpoint.of(HttpMethod.POST, "/inventory/ingredients/1/adjustments",
                    """
                    {"quantityChange":5,"reason":"sweep"}""", Role.MANAGER),
            Endpoint.of(HttpMethod.GET, "/inventory/ingredients/1/adjustments", null, Role.MANAGER),

            // ---- Tables & Orders: Waiter full, everyone else read (§2.1) ----
            Endpoint.of(HttpMethod.GET, "/tables", null, Role.values()),
            Endpoint.of(HttpMethod.PATCH, "/tables/1/service-flag",
                    """
                    {"needsService":true}""", Role.WAITER),
            Endpoint.of(HttpMethod.GET, "/orders", null, Role.values()),
            Endpoint.of(HttpMethod.GET, "/orders/1", null, Role.values()),
            Endpoint.of(HttpMethod.POST, "/orders",
                    """
                    {"tableId":1}""", Role.WAITER),
            Endpoint.of(HttpMethod.POST, "/orders/1/items",
                    """
                    {"menuItemId":1,"quantity":1,"notes":null}""", Role.WAITER),
            Endpoint.of(HttpMethod.PATCH, "/orders/1/items/1",
                    """
                    {"quantity":2}""", Role.WAITER),
            Endpoint.of(HttpMethod.DELETE, "/orders/1/items/1", null, Role.WAITER),
            Endpoint.of(HttpMethod.POST, "/orders/1/confirm", null, Role.WAITER),
            Endpoint.of(HttpMethod.POST, "/orders/1/serve", null, Role.WAITER),
            // The M5 split: the kitchen may advance a ticket but must never serve one.
            Endpoint.of(HttpMethod.POST, "/orders/1/prepare", null, Role.KITCHEN),
            Endpoint.of(HttpMethod.POST, "/orders/1/ready", null, Role.KITCHEN),

            // ---- Kitchen queue: Kitchen ONLY — stricter than Table & Orders (§2.1) ----
            Endpoint.of(HttpMethod.GET, "/kitchen/queue", null, Role.KITCHEN),

            // ---- Billing: Cashier full, Manager read (§2.1) ----
            Endpoint.of(HttpMethod.GET, "/billing/orders", null, Role.MANAGER, Role.CASHIER),
            Endpoint.of(HttpMethod.GET, "/billing/orders/1/bill", null, Role.MANAGER, Role.CASHIER),
            Endpoint.of(HttpMethod.GET, "/billing/orders/1/receipt", null, Role.MANAGER, Role.CASHIER),
            Endpoint.of(HttpMethod.POST, "/billing/orders/1/payment",
                    """
                    {"method":"CASH"}""", Role.CASHIER),

            // ---- Reports & Alerts: Manager only (§2.1) ----
            Endpoint.of(HttpMethod.GET, "/reports/sales?from=2026-01-01&to=2026-01-31", null, Role.MANAGER),
            Endpoint.of(HttpMethod.GET, "/reports/top-items?from=2026-01-01&to=2026-01-31", null, Role.MANAGER),

            // ---- User Management: Manager only (§2.1) ----
            Endpoint.of(HttpMethod.GET, "/users", null, Role.MANAGER),
            Endpoint.of(HttpMethod.POST, "/users",
                    """
                    {"username":"sweepuser","password":"password123","role":"WAITER","fullName":"Sweep"}""",
                    Role.MANAGER),
            Endpoint.of(HttpMethod.PUT, "/users/1",
                    """
                    {"fullName":"Sweep","role":"WAITER"}""", Role.MANAGER),
            Endpoint.of(HttpMethod.POST, "/users/1/password",
                    """
                    {"password":"password123"}""", Role.MANAGER),
            Endpoint.of(HttpMethod.POST, "/users/1/enable", null, Role.MANAGER),
            Endpoint.of(HttpMethod.POST, "/users/1/disable", null, Role.MANAGER));

    @Autowired
    private WebApplicationContext context;

    /** Qualified: the actuator contributes a second {@code RequestMappingHandlerMapping}. */
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    // Mocked so the sweep tests authorization only — never data (see class Javadoc).
    @MockBean private MenuService menuService;
    @MockBean private InventoryService inventoryService;
    @MockBean private OrderService orderService;
    @MockBean private KitchenService kitchenService;
    @MockBean private BillingService billingService;
    @MockBean private ReportingService reportingService;
    @MockBean private UserService userService;
    @MockBean private AuthService authService;
    @MockBean private PingService pingService;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                            .springSecurity())
                    .build();
        }
        return mockMvc;
    }

    // ---- The sweep ---------------------------------------------------------

    static Stream<Object[]> everyEndpointAndRole() {
        List<Object[]> cases = new ArrayList<>();
        for (Endpoint endpoint : ENDPOINTS) {
            for (Role role : Role.values()) {
                cases.add(new Object[] {endpoint, role});
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{1} -> {0}")
    @MethodSource("everyEndpointAndRole")
    @DisplayName("every endpoint admits exactly the roles SRS 2.1 grants it")
    void rbacMatrixHolds(Endpoint endpoint, Role role) throws Exception {
        MvcResult result = mvc()
                .perform(endpoint.build().with(
                        org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(role.name().toLowerCase())
                                .roles(role.name())))
                .andReturn();

        int status = result.getResponse().getStatus();

        if (endpoint.allowed().contains(role)) {
            // Not "200": the services are mocked, so 404/409 are legitimate outcomes here. What
            // matters is that authorization did not stop the request.
            assertThat(status)
                    .as("%s should be allowed to call %s, but was forbidden", role, endpoint)
                    .isNotEqualTo(403);
        } else {
            assertThat(status)
                    .as("%s must NOT be able to call %s", role, endpoint)
                    .isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "unauthenticated -> {0}")
    @MethodSource("everyEndpoint")
    @DisplayName("every endpoint refuses an unauthenticated caller with 401")
    void unauthenticatedIsRejected(Endpoint endpoint) throws Exception {
        MvcResult result = mvc().perform(endpoint.build()).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("%s must reject an unauthenticated caller with 401", endpoint)
                .isEqualTo(401);
    }

    static Stream<Endpoint> everyEndpoint() {
        return ENDPOINTS.stream();
    }

    // ---- The check that keeps the sweep a sweep -----------------------------

    @Test
    @DisplayName("no endpoint exists that this sweep does not cover")
    void everyEndpointIsCovered() {
        Set<String> mapped = new LinkedHashSet<>();
        handlerMapping.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod method) -> {
            if (info.getPathPatternsCondition() == null) {
                return;
            }
            info.getPathPatternsCondition().getPatterns().forEach(pattern -> {
                Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                        info.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    mapped.add("ANY " + pattern.getPatternString());
                } else {
                    methods.forEach(m -> mapped.add(m.name() + " " + pattern.getPatternString()));
                }
            });
        });

        Set<String> covered = new LinkedHashSet<>();
        ENDPOINTS.forEach(e -> covered.add(e.signature()));
        covered.addAll(PUBLIC_ENDPOINTS);

        List<String> uncovered = mapped.stream()
                .filter(m -> !covered.contains(m))
                .filter(m -> !m.startsWith("ANY /error"))
                .sorted()
                .toList();

        assertThat(uncovered)
                .as("""
                    These endpoints exist but are not in the RBAC sweep. Add a row to ENDPOINTS \
                    (or to PUBLIC_ENDPOINTS if the endpoint is deliberately public). NFR-03 \
                    requires RBAC on EVERY endpoint, so an untested one is the one most likely \
                    to be wrong.""")
                .isEmpty();
    }

    // ---- Test fixture ------------------------------------------------------

    /**
     * One row of the access matrix. {@code body} is a <em>valid</em> payload where the endpoint
     * takes one — see the class Javadoc for why that matters.
     */
    record Endpoint(HttpMethod method, String path, String body, Set<Role> allowed) {

        static Endpoint of(HttpMethod method, String path, String body, Role... allowed) {
            Set<Role> roles = allowed.length == 0
                    ? EnumSet.noneOf(Role.class)
                    : EnumSet.copyOf(Arrays.asList(allowed));
            return new Endpoint(method, path, body, roles);
        }

        MockHttpServletRequestBuilder build() {
            MockHttpServletRequestBuilder builder = request(method, path);
            if (body != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return builder;
        }

        /** The form {@link #everyEndpointIsCovered()} compares against the mapped patterns. */
        String signature() {
            String withoutQuery = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
            return method.name() + " " + withoutQuery
                    .replaceAll("/menu/items/\\d+/recipe", "/menu/items/{itemId}/recipe")
                    .replaceAll("/(\\d+)(?=/|$)", "/{id}")
                    .replace("/orders/{id}/items/{id}", "/orders/{id}/items/{itemId}")
                    .replace("/billing/orders/{id}/", "/billing/orders/{id}/");
        }

        @Override
        public String toString() {
            return method.name() + " " + path;
        }
    }
}
