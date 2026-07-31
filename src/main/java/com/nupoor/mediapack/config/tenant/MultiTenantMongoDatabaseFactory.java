package com.nupoor.mediapack.config.tenant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.util.Map;

/**
 * Custom MongoDatabaseFactory that overrides database name resolution
 * based on the current thread's TenantContext (X-Tenant-Region header).
 *
 * This implements the AbstractMongoFactory pattern described in the technical design.
 * Each incoming HTTP request sets a tenant ID (DE, NL, US) via TenantInterceptor,
 * and all MongoDB operations on that thread are automatically routed to the
 * corresponding region-specific database.
 */
public class MultiTenantMongoDatabaseFactory extends SimpleMongoClientDatabaseFactory {

    private static final Map<String, String> TENANT_DB_MAP = Map.of(
        "DE", "media_catalog_de",
        "NL", "media_catalog_nl",
        "US", "media_catalog_us"
    );

    private static final String DEFAULT_DATABASE = "media_catalog_de";

    public MultiTenantMongoDatabaseFactory(MongoClient mongoClient) {
        super(mongoClient, DEFAULT_DATABASE);
    }

    @Override
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        String tenant = TenantContext.getCurrentTenant();
        String dbName = TENANT_DB_MAP.getOrDefault(tenant, DEFAULT_DATABASE);
        return getMongoClient().getDatabase(dbName);
    }

    @Override
    public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {
        // Honor explicit database name override requests from Spring internals
        return super.getMongoDatabase(dbName);
    }
}
