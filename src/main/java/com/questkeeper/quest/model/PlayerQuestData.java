package com.questkeeper.quest.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerQuestData {
    private final UUID playerId;
    private final Map<String, QuestRecord> quests = new HashMap<>();
    private boolean dirty;
    public PlayerQuestData(UUID playerId) { this.playerId = playerId; }
    public UUID playerId() { return playerId; }
    public Map<String, QuestRecord> quests() { return quests; }
    public boolean isDirty() { return dirty; }
    public void dirty() { dirty = true; }
    public void clean() { dirty = false; }
    public QuestRecord record(String id) { return quests.computeIfAbsent(id, ignored -> new QuestRecord()); }
    public static final class QuestRecord {
        private QuestStatus status = QuestStatus.AVAILABLE;
        private long acceptedAt, completedAt, claimedAt, cooldownUntil;
        private final Map<String, Integer> progress = new HashMap<>();
        public QuestStatus status() { return status; }
        public void status(QuestStatus status) { this.status = status; }
        public long acceptedAt() { return acceptedAt; } public void acceptedAt(long v) { acceptedAt = v; }
        public long completedAt() { return completedAt; } public void completedAt(long v) { completedAt = v; }
        public long claimedAt() { return claimedAt; } public void claimedAt(long v) { claimedAt = v; }
        public long cooldownUntil() { return cooldownUntil; } public void cooldownUntil(long v) { cooldownUntil = v; }
        public Map<String, Integer> progress() { return progress; }
    }
}
