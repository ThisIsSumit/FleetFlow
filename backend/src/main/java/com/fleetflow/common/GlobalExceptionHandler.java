package com.fleetflow.common;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.*;
/** Every error leaves the API in one shape (ErrorBody). Client mistakes map to 4xx; only genuinely unexpected failures are 500 + ERROR log. */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  public record ErrorBody(Instant timestamp, int status, String error, String message, String path, String correlationId, List<String> details) {}
  private ResponseEntity<ErrorBody> body(HttpStatus s, String code, String msg, HttpServletRequest r, List<String> d) {
    return ResponseEntity.status(s).body(new ErrorBody(Instant.now(), s.value(), code, msg, r.getRequestURI(), MDC.get(CorrelationFilter.KEY), d));
  }
  @ExceptionHandler(ApiException.class) ResponseEntity<ErrorBody> api(ApiException e, HttpServletRequest r) { return body(e.status, e.code, e.getMessage(), r, List.of()); }
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
    var d = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
    return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", r, d);
  }
  @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class}) ResponseEntity<ErrorBody> invalidParams(Exception e, HttpServletRequest r) {
    return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", r, List.of());
  }
  @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e, HttpServletRequest r) {
    return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or is not valid JSON for this endpoint", r, List.of());
  }
  @ExceptionHandler(MethodArgumentTypeMismatchException.class) ResponseEntity<ErrorBody> mismatch(MethodArgumentTypeMismatchException e, HttpServletRequest r) {
    return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid value for '" + e.getName() + "'", r, List.of());
  }
  @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestCookieException.class}) ResponseEntity<ErrorBody> missing(Exception e, HttpServletRequest r) {
    return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), r, List.of());
  }
  @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<ErrorBody> noRoute(NoResourceFoundException e, HttpServletRequest r) {
    return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint", r, List.of());
  }
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class) ResponseEntity<ErrorBody> method(HttpRequestMethodNotSupportedException e, HttpServletRequest r) {
    return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", e.getMessage(), r, List.of());
  }
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class) ResponseEntity<ErrorBody> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest r) {
    return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use Content-Type: application/json", r, List.of());
  }
  /** Two users changed the same row at once (@Version), or a row lock timed out: the client should reload and retry. */
  @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class}) ResponseEntity<ErrorBody> conflict(Exception e, HttpServletRequest r) {
    return body(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "This record was changed by someone else. Reload and try again.", r, List.of());
  }
  /** Unique-constraint races that slipped past an "exists" pre-check (e.g. two signups with the same email at once). */
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorBody> integrity(DataIntegrityViolationException e, HttpServletRequest r) {
    log.warn("Data integrity violation on {}: {}", r.getRequestURI(), e.getMostSpecificCause().getMessage());
    return body(HttpStatus.CONFLICT, "CONFLICT", "This conflicts with existing data (it may already exist)", r, List.of());
  }
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ErrorBody> denied(AccessDeniedException e, HttpServletRequest r) { return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions", r, List.of()); }
  @ExceptionHandler(Exception.class) ResponseEntity<ErrorBody> other(Exception e, HttpServletRequest r) {
    log.error("Unhandled", e); return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", r, List.of());
  }
}
