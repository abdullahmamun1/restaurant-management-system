package com.restaurant.controller;

import com.restaurant.domain.IllegalOrderStateException;
import com.restaurant.domain.InsufficientStockException;
import com.restaurant.domain.InvalidDateRangeException;
import com.restaurant.domain.TableUnavailableException;
import com.restaurant.service.exception.ConflictException;
import com.restaurant.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates cross-cutting exceptions into consistent HTTP responses so clients get the right
 * status regardless of where the failure originates:
 * <ul>
 *   <li>Bad/invalid credentials &rarr; 401</li>
 *   <li>Authenticated but wrong role ({@code @PreAuthorize} denial) &rarr; 403</li>
 *   <li>Missing entity &rarr; 404; state conflict (duplicate, non-empty delete) &rarr; 409</li>
 *   <li>Request validation failure &rarr; 400</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * The FR-22 halt: a deduction — manual or automatic — that would take stock below zero.
     *
     * <p>Carries a machine-readable {@code reason} alongside the message because one client
     * genuinely needs to tell this 409 apart from every other one: at the till a stock shortfall is
     * the only refusal the cashier cannot resolve themselves (there are no refunds or
     * cancellations), so it gets its own guidance. Pattern-matching the prose was tried and is
     * exactly as brittle as it sounds.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, ex.getMessage());
        pd.setProperty("reason", "INSUFFICIENT_STOCK");
        return pd;
    }

    /**
     * Domain refusals rooted in current state: an illegal order transition or item edit after
     * confirmation (FR-07, FR-09), or a table that already has an open order (FR-06).
     */
    @ExceptionHandler({IllegalOrderStateException.class, TableUnavailableException.class})
    public ProblemDetail handleIllegalState(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    /**
     * A report date range that cannot mean anything — {@code to} before {@code from} (M7 D8).
     *
     * <p>400 rather than the 409 its sibling domain exceptions get: this is a malformed request, not
     * a conflict with the system's state. Refusing it matters because a reversed range yields an
     * <em>empty</em> interval, so the alternative is answering a nonsensical question with a
     * confident zero.
     */
    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * A query parameter that will not convert — {@code ?from=notadate} (M7 D8).
     *
     * <p>Spring's default resolver already turns this into a 400, but with <em>no</em>
     * {@code ProblemDetail} body, which is inconsistent with every other error this API returns and
     * leaves the client with nothing to display. M7 is the first milestone to take typed query
     * parameters; this applies to every one added after it.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null
                ? "the expected type" : ex.getRequiredType().getSimpleName();
        return problem(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' is not a valid "
                + expected + ": '" + ex.getValue() + "'.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
