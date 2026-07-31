package com.nupoor.mediapack.config.tenant;

public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT_TENANT.set(tenantId.toUpperCase());
        } else {
            CURRENT_TENANT.set("DE");
        }
    }

    public static String getCurrentTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : "DE"; // Default to German tenant
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
