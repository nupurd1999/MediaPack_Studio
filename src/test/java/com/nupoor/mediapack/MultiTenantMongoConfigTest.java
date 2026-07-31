package com.nupoor.mediapack;

import com.nupoor.mediapack.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiTenantMongoConfigTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext — Default tenant falls back to DE")
    void testTenantContext_DefaultIsDE() {
        assertEquals("DE", TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName("TenantContext — Switches context for NL and US tenants")
    void testTenantContext_SwitchTenants() {
        TenantContext.setCurrentTenant("NL");
        assertEquals("NL", TenantContext.getCurrentTenant());

        TenantContext.setCurrentTenant("US");
        assertEquals("US", TenantContext.getCurrentTenant());
    }
}
