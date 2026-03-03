package com.milktea.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Session-level short-term memory with 1-hour default TTL.
 * Data is stored in-memory and automatically cleaned up on expiration.
 */
@Component
public class ShortTermMemory {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemory.class);
    private static final long DEFAULT_TTL_SECONDS = 3600; // 1 hour

    // sessionId -> (key -> MemoryEntry)
    private final Map<String, Map<String, MemoryEntry>> sessionMemories = new ConcurrentHashMap<>();

    public void put(String sessionId, String key, String value, String category) {
        put(sessionId, key, value, category, DEFAULT_TTL_SECONDS);
    }

    public void put(String sessionId, String key, String value, String category, long ttlSeconds) {
        sessionMemories.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(key, new MemoryEntry(key, value, category, ttlSeconds));
        log.debug("ShortTermMemory: saved [{}] for session {}", key, sessionId);
    }

    public Optional<MemoryEntry> get(String sessionId, String key) {
        Map<String, MemoryEntry> memories = sessionMemories.get(sessionId);
        if (memories == null) return Optional.empty();
        MemoryEntry entry = memories.get(key);
        if (entry == null) return Optional.empty();
        if (entry.isExpired()) {
            memories.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public List<MemoryEntry> getAll(String sessionId) {
        Map<String, MemoryEntry> memories = sessionMemories.get(sessionId);
        if (memories == null) return List.of();
        cleanExpired(sessionId);
        return List.copyOf(memories.values());
    }

    public List<MemoryEntry> search(String sessionId, String keyword) {
        return getAll(sessionId).stream()
                .filter(e -> e.key().contains(keyword) || e.value().contains(keyword)
                        || (e.category() != null && e.category().contains(keyword)))
                .collect(Collectors.toList());
    }

    public void remove(String sessionId, String key) {
        Map<String, MemoryEntry> memories = sessionMemories.get(sessionId);
        if (memories != null) {
            memories.remove(key);
        }
    }

    public void clearSession(String sessionId) {
        sessionMemories.remove(sessionId);
    }

    public void cleanExpired(String sessionId) {
        Map<String, MemoryEntry> memories = sessionMemories.get(sessionId);
        if (memories != null) {
            memories.entrySet().removeIf(e -> e.getValue().isExpired());
            if (memories.isEmpty()) {
                sessionMemories.remove(sessionId);
            }
        }
    }

    public void cleanAllExpired() {
        sessionMemories.keySet().forEach(this::cleanExpired);
    }

    public Set<String> getActiveSessions() {
        cleanAllExpired();
        return Set.copyOf(sessionMemories.keySet());
    }
}
