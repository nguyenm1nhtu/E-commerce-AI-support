package com.ecommerce.aicommercesupport.common.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class CacheEviction {

    private final CacheManager cacheManager;

    public void afterCommit(String cacheName, Object key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.evictIfPresent(key);
                }
            }
        });
    }
}
