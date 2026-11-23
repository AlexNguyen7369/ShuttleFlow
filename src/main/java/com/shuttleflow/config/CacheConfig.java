package com.shuttleflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Honors caching for GET /slots. The cache only speeds up browsing: booking still locks the slot
 * row and relies on the UNIQUE (active_slot_id) constraint, never on cached data.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Evictions wait until the booking transaction commits; evicting earlier would let a concurrent
     * browse re-cache the slot as still open just before the commit.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer transactionAwareRedisCache() {
        return builder -> builder.transactionAware();
    }

    /** A Redis outage must never break browsing or booking: log it and fall through to the database. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache read failed cache={}, reading the database: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache write failed cache={}: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed cache={}: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed cache={}, entries expire within the TTL: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
