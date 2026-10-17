package com.squadyatra.user.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlation = request.getHeader("X-Correlation-Id");
        if (correlation == null || !correlation.matches("[A-Za-z0-9._-]{1,64}")) {
            correlation = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlation);
        response.setHeader("X-Correlation-Id", correlation);
        response.setHeader("Cache-Control", "no-store");
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}