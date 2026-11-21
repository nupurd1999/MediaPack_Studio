package com.nupoor.mediapack;

import com.nupoor.mediapack.config.tenant.MultiTenantMongoDatabaseFactory;
import com.nupoor.mediapack.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiTenantRoutingTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("MultiTenantMongoDatabaseFactory — resolves German DB for DE tenant")
    void testResolveDatabaseName_DE() {
        assertEquals("media_catalog_de", MultiTenantMongoDatabaseFactory.resolveDatabaseName("DE"));
    }

    @Test
    @DisplayName("MultiTenantMongoDatabaseFactory — resolves Netherlands DB for NL tenant")
    void testResolveDatabaseName_NL() {
        assertEquals("media_catalog_nl", MultiTenantMongoDatabaseFactory.resolveDatabaseName("NL"));
    }

    @Test
    @DisplayName("MultiTenantMongoDatabaseFactory — resolves US DB for US tenant")
    void testResolveDatabaseName_US() {
        assertEquals("media_catalog_us", MultiTenantMongoDatabaseFactory.resolveDatabaseName("US"));
    }

    @Test
    @DisplayName("MultiTenantMongoDatabaseFactory — falls back to default DE for unknown or null tenant")
    void testResolveDatabaseName_Fallback() {
        assertEquals("media_catalog_de", MultiTenantMongoDatabaseFactory.resolveDatabaseName(null));
        assertEquals("media_catalog_de", MultiTenantMongoDatabaseFactory.resolveDatabaseName(""));
        assertEquals("media_catalog_de", MultiTenantMongoDatabaseFactory.resolveDatabaseName("UNKNOWN"));
    }
}
