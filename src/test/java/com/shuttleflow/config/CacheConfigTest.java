package com.shuttleflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;

/** A Redis failure must be logged and swallowed, so the cached method falls through to the database. */
class CacheConfigTest {

    private final CacheErrorHandler handler = new CacheConfig().errorHandler();
    private final ConcurrentMapCache cache = new ConcurrentMapCache("openSlots");
    private final RuntimeException redisDown = new IllegalStateException("Unable to connect to Redis");

    @Test
    void everyCacheErrorIsSwallowed() {
        assertThatCode(() -> {
            handler.handleCacheGetError(redisDown, cache, "key");
            handler.handleCachePutError(redisDown, cache, "key", "value");
            handler.handleCacheEvictError(redisDown, cache, "key");
            handler.handleCacheClearError(redisDown, cache);
        }).doesNotThrowAnyException();
    }
}
