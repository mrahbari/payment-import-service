package com.example.payment.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE = "X-Trace-Id";
    public static final String HEADER_REQUEST = "X-Request-Id";

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveOrGenerate(request.getHeader(HEADER_TRACE));
        String requestId = resolveOrGenerate(request.getHeader(HEADER_REQUEST));

        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_REQUEST_ID, requestId);

            response.setHeader(HEADER_TRACE, traceId);
            response.setHeader(HEADER_REQUEST, requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveOrGenerate(String value) {
        return (value == null || value.isBlank())
                ? UUID.randomUUID().toString()
                : value;
    }
}