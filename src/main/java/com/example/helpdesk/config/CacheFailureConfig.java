package com.example.helpdesk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheFailureConfig {
    private static final Logger log = LoggerFactory.getLogger(CacheFailureConfig.class);

    /** Dashboard metrics are database-backed; a Redis outage must not turn them into HTTP 500s. */
    @Bean
    CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed for {}:{}; continuing without cached data", cache.getName(), key, exception);
            }
            @Override public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache write failed for {}; continuing without cache", cache.getName(), exception);
            }
            @Override public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache eviction failed for {}; cached value may remain until expiry", cache.getName(), exception);
            }
            @Override public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed for {}; cached values may remain until expiry", cache.getName(), exception);
            }
        };
    }
}
