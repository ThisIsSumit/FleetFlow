package com.fleetflow.common;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
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
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ErrorBody> denied(AccessDeniedException e, HttpServletRequest r) { return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions", r, List.of()); }
  @ExceptionHandler(Exception.class) ResponseEntity<ErrorBody> other(Exception e, HttpServletRequest r) {
    log.error("Unhandled", e); return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", r, List.of());
  }
}
