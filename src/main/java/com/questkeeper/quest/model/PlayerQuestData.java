package com.questkeeper.quest.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable player-owned state. Bukkit mutations happen on the server thread; snapshots are safe for async persistence. */
public final class PlayerQuestData {
    private final UUID playerId;
    private final Map<String, QuestRecord> quests = new LinkedHashMap<>();
    private long revision;
    private long savedRevision;

    public PlayerQuestData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public Map<String, QuestRecord> quests() {
        return quests;
    }

    public synchronized boolean isDirty() {
        return revision != savedRevision;
    }

    public synchronized void dirty() {
        revision++;
    }

    public synchronized Snapshot snapshot() {
        Map<String, QuestRecordSnapshot> records = new LinkedHashMap<>();
        for (Map.Entry<String, QuestRecord> entry : quests.entrySet()) {
            records.put(entry.getKey(), entry.getValue().snapshot());
        }
        return new Snapshot(playerId, records, revision);
    }

    public synchronized void markSaved(long savedRevision) {
        this.savedRevision = Math.max(this.savedRevision, savedRevision);
    }

    public QuestRecord record(String id) {
        return quests.computeIfAbsent(id, ignored -> new QuestRecord());
    }

    public static final class QuestRecord {
        private QuestStatus status = QuestStatus.AVAILABLE;
        private long acceptedAt;
        private long completedAt;
        private long claimedAt;
        private long cooldownUntil;
        private final Map<String, Integer> progress = new LinkedHashMap<>();

        public QuestStatus status() {
            return status;
        }

        public void status(QuestStatus status) {
            this.status = status;
        }

        public long acceptedAt() {
            return acceptedAt;
        }

        public void acceptedAt(long value) {
            acceptedAt = value;
        }

        public long completedAt() {
            return completedAt;
        }

        public void completedAt(long value) {
            completedAt = value;
        }

        public long claimedAt() {
            return claimedAt;
        }

        public void claimedAt(long value) {
            claimedAt = value;
        }

        public long cooldownUntil() {
            return cooldownUntil;
        }

        public void cooldownUntil(long value) {
            cooldownUntil = value;
        }

        public Map<String, Integer> progress() {
            return progress;
        }

        private QuestRecordSnapshot snapshot() {
            return new QuestRecordSnapshot(status, acceptedAt, completedAt, claimedAt, cooldownUntil,
                    Map.copyOf(progress));
        }
    }

    public record Snapshot(UUID playerId, Map<String, QuestRecordSnapshot> quests, long revision) {
        public Snapshot {
            quests = Map.copyOf(quests);
        }
    }

    public record QuestRecordSnapshot(QuestStatus status, long acceptedAt, long completedAt,
                                     long claimedAt, long cooldownUntil, Map<String, Integer> progress) {
        public QuestRecordSnapshot {
            progress = Map.copyOf(progress);
        }
    }
}
