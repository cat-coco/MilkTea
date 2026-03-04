package com.milktea.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * User-level long-term memory persisted to Redis.
 * Falls back to in-memory storage when Redis is unavailable.
 */
@Component
public class LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemory.class);
    private static final String REDIS_KEY_PREFIX = "milktea:memory:user:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean redisAvailable;

    // Fallback in-memory storage when Redis is not available
    private final Map<String, Map<String, String>> fallbackStore = new ConcurrentHashMap<>();

    public LongTermMemory(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisAvailable = checkRedisAvailable();
        if (!redisAvailable) {
            log.warn("Redis not available, using in-memory fallback for long-term memory");
        }
    }

    private boolean checkRedisAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void put(String userId, String key, String value, String category) {
        MemoryEntry entry = new MemoryEntry(key, value, category, 0);
        try {
            String json = objectMapper.writeValueAsString(entry);
            if (redisAvailable) {
                String redisKey = REDIS_KEY_PREFIX + userId;
                redisTemplate.opsForHash().put(redisKey, key, json);
                redisTemplate.expire(redisKey, DEFAULT_TTL);
            } else {
                fallbackStore.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                        .put(key, json);
            }
            log.debug("LongTermMemory: saved [{}] for user {}", key, userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize memory entry", e);
        }
    }

    public Optional<MemoryEntry> get(String userId, String key) {
        try {
            String json;
            if (redisAvailable) {
                json = (String) redisTemplate.opsForHash()
                        .get(REDIS_KEY_PREFIX + userId, key);
            } else {
                Map<String, String> userStore = fallbackStore.get(userId);
                json = userStore != null ? userStore.get(key) : null;
            }
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, MemoryEntry.class));
        } catch (Exception e) {
            log.error("Failed to read memory entry", e);
            return Optional.empty();
        }
    }

    public List<MemoryEntry> getAll(String userId) {
        try {
            Map<Object, Object> entries;
            if (redisAvailable) {
                entries = redisTemplate.opsForHash().entries(REDIS_KEY_PREFIX + userId);
            } else {
                Map<String, String> userStore = fallbackStore.getOrDefault(userId, Map.of());
                entries = new HashMap<>(userStore);
            }
            return entries.values().stream()
                    .map(v -> {
                        try {
                            return objectMapper.readValue((String) v, MemoryEntry.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list memory entries", e);
            return List.of();
        }
    }

    public List<MemoryEntry> search(String userId, String keyword) {
        return getAll(userId).stream()
                .filter(e -> e.key().contains(keyword) || e.value().contains(keyword)
                        || (e.category() != null && e.category().contains(keyword)))
                .collect(Collectors.toList());
    }

    public void remove(String userId, String key) {
        if (redisAvailable) {
            redisTemplate.opsForHash().delete(REDIS_KEY_PREFIX + userId, key);
        } else {
            Map<String, String> userStore = fallbackStore.get(userId);
            if (userStore != null) userStore.remove(key);
        }
    }

    public void clearUser(String userId) {
        if (redisAvailable) {
            redisTemplate.delete(REDIS_KEY_PREFIX + userId);
        } else {
            fallbackStore.remove(userId);
        }
    }
}
