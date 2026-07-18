package com.questkeeper.quest.service;

import com.questkeeper.database.DatabaseManager;
import com.questkeeper.quest.model.PlayerQuestData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerQuestDataService {
    private final DatabaseManager database;
    private final Map<UUID, PlayerQuestData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerQuestData>> loading = new ConcurrentHashMap<>();

    public PlayerQuestDataService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<PlayerQuestData> load(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerQuestData cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<PlayerQuestData> existing = loading.get(playerId);
        if (existing != null) return existing;

        CompletableFuture<PlayerQuestData> future = database.load(playerId);
        existing = loading.putIfAbsent(playerId, future);
        if (existing != null) return existing;
        future.whenComplete((data, error) -> {
            if (loading.remove(playerId, future) && error == null && data != null) {
                cache.put(playerId, data);
            }
        });
        return future;
    }

    public boolean isLoaded(UUID id) {
        return cache.containsKey(id);
    }

    public PlayerQuestData get(UUID id) {
        return cache.get(id);
    }

    /** Used only by write paths such as accepting, progressing, or resetting a quest. */
    public PlayerQuestData getOrCreate(UUID id) {
        return cache.computeIfAbsent(id, PlayerQuestData::new);
    }

    public CompletableFuture<Void> save(PlayerQuestData data) {
        if (!data.isDirty()) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerQuestData.Snapshot snapshot = data.snapshot();
        return database.save(snapshot).thenRun(() -> data.markSaved(snapshot.revision()));
    }

    public void saveAndRemove(UUID id) {
        PlayerQuestData data = cache.remove(id);
        loading.remove(id);
        if (data == null || !data.isDirty()) {
            return;
        }
        database.save(data.snapshot());
    }

    /** Called on the server thread so snapshots are captured before async SQL work starts. */
    public void saveAll() {
        List<PendingSave> pending = new ArrayList<>();
        for (PlayerQuestData data : cache.values()) {
            if (data.isDirty()) {
                pending.add(new PendingSave(data, data.snapshot()));
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        database.saveAll(pending.stream().map(PendingSave::snapshot).toList())
                .thenRun(() -> pending.forEach(value -> value.data().markSaved(value.snapshot().revision())));
    }

    public Collection<PlayerQuestData> cached() {
        return cache.values();
    }

    private record PendingSave(PlayerQuestData data, PlayerQuestData.Snapshot snapshot) {
    }
}
