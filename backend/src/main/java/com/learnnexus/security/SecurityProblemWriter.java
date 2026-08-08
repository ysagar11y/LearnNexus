package com.learnnexus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnnexus.common.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes error bodies for failures raised inside the filter chain, where
 * {@code @RestControllerAdvice} does not apply. Keeps the shape identical to
 * {@code GlobalExceptionHandler.ErrorResponse} so clients parse one format.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ApiException ex) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("details", ex.getDetails());
        body.put("timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
