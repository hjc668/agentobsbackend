package com.icbc.aiops.langfuse.api;

import com.icbc.aiops.langfuse.service.ResourceNotFoundException;
import com.icbc.aiops.langfuse.service.ConflictException;
import com.icbc.aiops.langfuse.service.ForbiddenOperationException;
import com.icbc.aiops.langfuse.service.InvalidRequestException;
import com.icbc.aiops.langfuse.security.AamAuthenticationException;
import javax.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class RestExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(ResourceNotFoundException exception) {
        return new ApiError("NOT_FOUND", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(ConflictException exception) {
        return new ApiError("CONFLICT", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleForbidden(ForbiddenOperationException exception) {
        return new ApiError("FORBIDDEN", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidRequest(InvalidRequestException exception) {
        return new ApiError("INVALID_REQUEST", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(AamAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleAamAuthentication(AamAuthenticationException exception) {
        return new ApiError("AAM_AUTH_FAILED", "AAM authentication failed", Instant.now());
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidQuery(Exception exception) {
        return new ApiError("INVALID_QUERY", "One or more query parameters are invalid.", Instant.now());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleDataSourceUnavailable(DataAccessException exception) {
        LOGGER.error("Langfuse data source request failed", exception);
        return new ApiError(
                "DATA_SOURCE_UNAVAILABLE",
                "Langfuse data source is temporarily unavailable. Check ClickHouse/PolarDB-X connectivity and retry.",
                Instant.now());
    }
}
