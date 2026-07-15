package com.shopmanagement.ledgerservice.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String SHOP_ID_HEADER = "X-Shop-Id";
    public static final String AUTH_ROLE_HEADER = "X-Auth-Role";
    public static final String AUTH_USER_HEADER = "X-Auth-User";
    public static final String AUTH_PERMISSIONS_HEADER = "X-Auth-Permissions";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final ThreadLocal<Long> currentTenantId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentShopId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentRole = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUsername = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> currentPermissions = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            if (!"OPTIONS".equalsIgnoreCase(request.getMethod()) && !request.getRequestURI().startsWith("/actuator")) {
                String tenantId = request.getHeader(TENANT_ID_HEADER);
                String shopId = request.getHeader(SHOP_ID_HEADER);
                if (tenantId == null || tenantId.isBlank() || shopId == null || shopId.isBlank()) {
                    log.warn("Rejecting {} {} — missing tenant/shop headers", request.getMethod(), request.getRequestURI());
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"message\":\"Missing tenant context headers: X-Tenant-Id and X-Shop-Id are required\"}");
                    return;
                }
                try {
                    currentTenantId.set(Long.valueOf(tenantId));
                } catch (NumberFormatException ex) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Invalid X-Tenant-Id header\"}");
                    return;
                }
                currentShopId.set(shopId);
                applyAuthContextHeaders(request);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
            currentTenantId.remove();
            currentShopId.remove();
            currentRole.remove();
            currentUsername.remove();
            currentPermissions.remove();
        }
    }

    private void applyAuthContextHeaders(HttpServletRequest request) {
        currentRole.set(trimToNull(request.getHeader(AUTH_ROLE_HEADER)));
        currentUsername.set(trimToNull(request.getHeader(AUTH_USER_HEADER)));
        String perms = request.getHeader(AUTH_PERMISSIONS_HEADER);
        if (perms != null && !perms.isBlank()) {
            currentPermissions.set(Arrays.stream(perms.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
        } else {
            currentPermissions.set(List.of());
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public static Long getCurrentTenantId() {
        return currentTenantId.get();
    }

    public static String getCurrentShopId() {
        return currentShopId.get();
    }

    public static String getCurrentRole() {
        return currentRole.get();
    }

    public static String getCurrentUsername() {
        return currentUsername.get();
    }

    public static List<String> getCurrentPermissions() {
        List<String> perms = currentPermissions.get();
        return perms != null ? perms : List.of();
    }
}
