package com.gitinsight.githubservice.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffListResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.OrganizationAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitHub API cache: Redis-backed for multi-instance deployments, with an
 * in-memory mirror that keeps the service working when Redis is unreachable.
 *
 * <p>Every cache key is namespaced by a {@code prefix:} and the prefix maps to
 * a concrete Java type (see {@link #TYPES}), so values are stored as plain
 * JSON and deserialized to the exact type the caller expects. Unknown prefixes
 * degrade to the in-memory mirror only — never a wrong-type deserialization.
 *
 * <p>TTLs are enforced by Redis natively; the {@link #cleanExpired()} sweep
 * (every 5 minutes, wired by {@code @EnableScheduling}) keeps the in-memory
 * mirror bounded for the Redis-down fallback path.
 */
@Service
public class GitHubCacheService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCacheService.class);

    private static final String REDIS_PREFIX = "ghc:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final Map<String, CacheEntry<Object>> local = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TypeFactory typeFactory = TypeFactory.defaultInstance();

    record CacheEntry<T>(T data, Instant expiresAt, Instant createdAt) {}

    // ── Prefix → Java type registry ──
    private static final Map<String, JavaType> TYPES = buildTypeRegistry();

    private static Map<String, JavaType> buildTypeRegistry() {
        TypeFactory tf = TypeFactory.defaultInstance();
        Map<String, JavaType> m = new java.util.HashMap<>();
        m.put("profile:", tf.constructType(GitHubProfileResponse.class));
        m.put("repos:", tf.constructParametricType(List.class, RepositoryResponse.class));
        m.put("score:", tf.constructType(DeveloperScoreResponse.class));
        m.put("orgs:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubOrg.class));
        m.put("prs:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubPR.class));
        m.put("issues:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubIssue.class));
        m.put("commits:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubCommit.class));
        m.put("events:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubEvent.class));
        m.put("events-recv:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubEvent.class));
        m.put("langs:", tf.constructMapType(Map.class, String.class, Long.class));
        m.put("contrib:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubContributor.class));
        m.put("repo-prs:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubPR.class));
        m.put("repo-issues:", tf.constructParametricType(List.class, GitHubIntegrationService.GitHubIssue.class));
        m.put("commit-quality:", tf.constructType(CommitAnalyticsResponse.class));
        m.put("commit-diffs:", tf.constructType(CommitDiffListResponse.class));
        m.put("org-overview:", tf.constructType(OrganizationAnalyticsResponse.class));
        m.put("org-profile:", tf.constructMapType(Map.class, String.class, Object.class));
        m.put("org-repos:", tf.constructParametricType(List.class, Map.class));
        m.put("ai:", tf.constructType(String.class));
        return Map.copyOf(m);
    }

    /** No-arg constructor for unit tests that exercise the in-memory path only. */
    public GitHubCacheService() {
        this(Optional.empty());
    }

    @Autowired
    public GitHubCacheService(Optional<StringRedisTemplate> redisTemplate) {
        this.redis = redisTemplate.orElse(null);
        this.redisEnabled = this.redis != null;
        if (redisEnabled) {
            log.info("GitHub cache initialized with Redis backing");
        } else {
            log.info("GitHub cache running in-memory only (no Redis connection factory)");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (redisEnabled) {
            JavaType type = typeFor(key);
            if (type != null) {
                try {
                    String json = redis.opsForValue().get(REDIS_PREFIX + key);
                    if (json != null) {
                        return (T) objectMapper.readValue(json, type);
                    }
                } catch (Exception e) {
                    log.warn("Redis cache read failed for '{}' — falling back to local mirror: {}",
                            key, e.getMessage());
                }
            }
        }

        CacheEntry<Object> entry = local.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            if (entry != null) local.remove(key);
            return null;
        }
        return (T) entry.data();
    }

    public <T> void put(String key, T data) {
        put(key, data, DEFAULT_TTL);
    }

    public <T> void put(String key, T data, Duration ttl) {
        local.put(key, new CacheEntry<>(data, Instant.now().plus(ttl), Instant.now()));

        if (redisEnabled) {
            JavaType type = typeFor(key);
            if (type == null) {
                // Unknown prefix: never guess a type for Redis round-trips — the
                // in-memory mirror above still serves this instance correctly.
                return;
            }
            try {
                redis.opsForValue().set(REDIS_PREFIX + key, objectMapper.writeValueAsString(data), ttl);
            } catch (Exception e) {
                log.warn("Redis cache write failed for '{}' — local mirror only: {}", key, e.getMessage());
            }
        }
    }

    public void evict(String key) {
        local.remove(key);
        if (redisEnabled) {
            try {
                redis.delete(REDIS_PREFIX + key);
            } catch (Exception e) {
                log.warn("Redis cache evict failed for '{}': {}", key, e.getMessage());
            }
        }
    }

    public void evictByPrefix(String prefix) {
        local.keySet().removeIf(k -> k.startsWith(prefix));
        if (redisEnabled) {
            try {
                Set<String> keys = redis.keys(REDIS_PREFIX + prefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    redis.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Redis cache prefix-evict failed for '{}': {}", prefix, e.getMessage());
            }
        }
    }

    public long size() {
        return local.size();
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanExpired() {
        int before = local.size();
        local.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
        int removed = before - local.size();
        if (removed > 0) {
            log.debug("Cache cleanup: removed {} expired entries, {} remaining", removed, local.size());
        }
    }

    private static JavaType typeFor(String key) {
        for (Map.Entry<String, JavaType> e : TYPES.entrySet()) {
            if (key.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }
}
