package com.gitinsight.githubservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubCacheService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCacheService.class);

    private final Map<String, CacheEntry<Object>> cache = new ConcurrentHashMap<>();
    private final Duration defaultTtl = Duration.ofMinutes(15);

    record CacheEntry<T>(T data, Instant expiresAt, Instant createdAt) {}

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry<Object> entry = cache.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            if (entry != null) cache.remove(key);
            return null;
        }
        return (T) entry.data();
    }

    public <T> void put(String key, T data) {
        put(key, data, defaultTtl);
    }

    public <T> void put(String key, T data, Duration ttl) {
        cache.put(key, new CacheEntry<>(data, Instant.now().plus(ttl), Instant.now()));
    }

    public void evict(String key) {
        cache.remove(key);
    }

    public void evictByPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public long size() {
        return cache.size();
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("Cache cleanup: removed {} expired entries, {} remaining", removed, cache.size());
        }
    }
}
