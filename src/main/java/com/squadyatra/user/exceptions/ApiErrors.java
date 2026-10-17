package com.squadyatra.user.exceptions;

import java.util.List;

import com.squadyatra.user.exceptions.IdentityProviderException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException exception) {
        return problem(exception.getStatusCode().value(), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail result = problem(400, "Request validation failed");
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted().toList();
        result.setProperty("errors", fields);
        return result;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ProblemDetail invalid(Exception exception) {
        return problem(400, "Malformed request or invalid parameter");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict(Exception exception) {
        return problem(409, "Conflicting update or existing user; reload and retry");
    }

    @ExceptionHandler(IdentityProviderException.class)
    ProblemDetail unavailable(IdentityProviderException exception) {
        return problem(503, "Identity provider is temporarily unavailable");
    }

    private ProblemDetail problem(int status, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),
                detail == null ? "Request failed" : detail);
        result.setProperty("correlationId", MDC.get("correlationId"));
        return result;
    }
}