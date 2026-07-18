package com.questkeeper.quest.service;

import com.questkeeper.database.DatabaseManager;
import com.questkeeper.quest.model.PlayerQuestData;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerQuestDataService {
    private final DatabaseManager database; private final Map<UUID, PlayerQuestData> cache = new ConcurrentHashMap<>();
    public PlayerQuestDataService(DatabaseManager database) { this.database = database; }
    public void load(Player player) { database.load(player.getUniqueId()).thenAccept(data -> cache.put(data.playerId(), data)); }
    public PlayerQuestData get(UUID id) { return cache.get(id); }
    public PlayerQuestData getOrCreate(UUID id) { return cache.computeIfAbsent(id, PlayerQuestData::new); }
    public void save(PlayerQuestData data) { data.clean(); database.save(data); }
    public void saveAndRemove(UUID id) { PlayerQuestData data = cache.remove(id); if (data != null) database.save(data); }
    public void saveAll() { database.saveAll(cache.values()); }
    public Collection<PlayerQuestData> cached() { return cache.values(); }
}
