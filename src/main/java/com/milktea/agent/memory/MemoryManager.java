package com.milktea.agent.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Facade managing both short-term (session) and long-term (user) memory.
 */
@Component
public class MemoryManager {

    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;

    public MemoryManager(ShortTermMemory shortTermMemory, LongTermMemory longTermMemory) {
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
    }

    // ===== Short-term (session-level) =====

    public void rememberSession(String sessionId, String key, String value, String category) {
        shortTermMemory.put(sessionId, key, value, category);
    }

    public Optional<MemoryEntry> recallSession(String sessionId, String key) {
        return shortTermMemory.get(sessionId, key);
    }

    public List<MemoryEntry> listSessionMemories(String sessionId) {
        return shortTermMemory.getAll(sessionId);
    }

    public List<MemoryEntry> searchSessionMemories(String sessionId, String keyword) {
        return shortTermMemory.search(sessionId, keyword);
    }

    public void forgetSession(String sessionId, String key) {
        shortTermMemory.remove(sessionId, key);
    }

    public void clearSession(String sessionId) {
        shortTermMemory.clearSession(sessionId);
    }

    // ===== Long-term (user-level) =====

    public void rememberUser(String userId, String key, String value, String category) {
        longTermMemory.put(userId, key, value, category);
    }

    public Optional<MemoryEntry> recallUser(String userId, String key) {
        return longTermMemory.get(userId, key);
    }

    public List<MemoryEntry> listUserMemories(String userId) {
        return longTermMemory.getAll(userId);
    }

    public List<MemoryEntry> searchUserMemories(String userId, String keyword) {
        return longTermMemory.search(userId, keyword);
    }

    public void forgetUser(String userId, String key) {
        longTermMemory.remove(userId, key);
    }

    public void clearUser(String userId) {
        longTermMemory.clearUser(userId);
    }

    // ===== Preference helpers =====

    public void savePreference(String userId, String prefKey, String prefValue) {
        longTermMemory.put(userId, "pref:" + prefKey, prefValue, "preference");
    }

    public Optional<String> getPreference(String userId, String prefKey) {
        return longTermMemory.get(userId, "pref:" + prefKey).map(MemoryEntry::value);
    }

    // ===== Cleanup =====

    public void cleanExpiredSessions() {
        shortTermMemory.cleanAllExpired();
    }
}
