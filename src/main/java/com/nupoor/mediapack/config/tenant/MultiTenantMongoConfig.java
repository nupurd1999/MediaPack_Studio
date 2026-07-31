package com.nupoor.mediapack.config.tenant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Wires the custom MultiTenantMongoDatabaseFactory into Spring Data MongoDB.
 *
 * The factory is the single authoritative source of database name resolution;
 * MongoTemplate itself does NOT cache the db name — it delegates to the factory
 * on every operation, which reads TenantContext.getCurrentTenant() at that moment.
 */
@Configuration
public class MultiTenantMongoConfig {

    @Value("${spring.data.mongodb.uri:mongodb://admin:secret@localhost:27017/?authSource=admin}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    @Primary
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new MultiTenantMongoDatabaseFactory(mongoClient);
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MongoMappingContext mappingContext) {
        MappingMongoConverter converter = new MappingMongoConverter(
                new DefaultDbRefResolver(factory), mappingContext);
        // Prevent Spring from storing Java class names in MongoDB _class field
        converter.setMapKeyDotReplacement("_");
        converter.afterPropertiesSet();
        return new MongoTemplate(factory, converter);
    }
}
