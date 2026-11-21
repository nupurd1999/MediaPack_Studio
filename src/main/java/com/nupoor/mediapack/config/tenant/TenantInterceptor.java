package com.nupoor.mediapack.config.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String TENANT_HEADER = "X-Tenant-Region";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenant = request.getHeader(TENANT_HEADER);
        if (tenant == null || tenant.isBlank()) {
            tenant = request.getParameter("tenantRegion");
        }
        if (tenant == null || tenant.isBlank()) {
            tenant = request.getParameter("tenant");
        }
        if (tenant != null && !tenant.isBlank()) {
            TenantContext.setCurrentTenant(tenant.trim());
        } else {
            TenantContext.setCurrentTenant("DE");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
