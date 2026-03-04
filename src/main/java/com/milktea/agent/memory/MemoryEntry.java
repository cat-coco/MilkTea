package com.milktea.agent.memory;

import java.time.LocalDateTime;

public record MemoryEntry(
        String key,
        String value,
        String category,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public MemoryEntry(String key, String value, String category, long ttlSeconds) {
        this(key, value, category, LocalDateTime.now(),
                ttlSeconds > 0 ? LocalDateTime.now().plusSeconds(ttlSeconds) : null);
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
