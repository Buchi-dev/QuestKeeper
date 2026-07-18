package com.questkeeper.database;

import com.questkeeper.quest.model.PlayerQuestData;
import com.questkeeper.quest.model.QuestStatus;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "QuestKeeper-SQLite");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            initialize();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open SQLite database", e);
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (player_uuid TEXT PRIMARY KEY, last_seen INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_quests (player_uuid TEXT NOT NULL, quest_id TEXT NOT NULL, status TEXT NOT NULL, accepted_at INTEGER, completed_at INTEGER, claimed_at INTEGER, cooldown_until INTEGER, PRIMARY KEY(player_uuid, quest_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS objective_progress (player_uuid TEXT NOT NULL, quest_id TEXT NOT NULL, objective_id TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(player_uuid, quest_id, objective_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS placed_blocks (world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, player_uuid TEXT NOT NULL, PRIMARY KEY(world,x,y,z))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_player ON player_quests(player_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_progress_player ON objective_progress(player_uuid)");
        }
    }

    public CompletableFuture<PlayerQuestData> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerQuestData data = new PlayerQuestData(uuid);
            try (PreparedStatement statement = connection.prepareStatement("SELECT quest_id,status,accepted_at,completed_at,claimed_at,cooldown_until FROM player_quests WHERE player_uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        var record = data.record(result.getString(1));
                        record.status(QuestStatus.valueOf(result.getString(2)));
                        record.acceptedAt(result.getLong(3)); record.completedAt(result.getLong(4));
                        record.claimedAt(result.getLong(5)); record.cooldownUntil(result.getLong(6));
                    }
                }
            } catch (SQLException e) { plugin.getLogger().warning("Could not load quest data for " + uuid + ": " + e.getMessage()); }
            try (PreparedStatement statement = connection.prepareStatement("SELECT quest_id,objective_id,progress FROM objective_progress WHERE player_uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) data.record(result.getString(1)).progress().put(result.getString(2), result.getInt(3));
                }
            } catch (SQLException e) { plugin.getLogger().warning("Could not load objective progress for " + uuid + ": " + e.getMessage()); }
            return data;
        }, executor);
    }

    public CompletableFuture<Void> save(PlayerQuestData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement player = connection.prepareStatement("INSERT INTO players(player_uuid,last_seen) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET last_seen=excluded.last_seen");
                     PreparedStatement quest = connection.prepareStatement("INSERT INTO player_quests(player_uuid,quest_id,status,accepted_at,completed_at,claimed_at,cooldown_until) VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid,quest_id) DO UPDATE SET status=excluded.status,accepted_at=excluded.accepted_at,completed_at=excluded.completed_at,claimed_at=excluded.claimed_at,cooldown_until=excluded.cooldown_until");
                     PreparedStatement objective = connection.prepareStatement("INSERT INTO objective_progress(player_uuid,quest_id,objective_id,progress) VALUES(?,?,?,?) ON CONFLICT(player_uuid,quest_id,objective_id) DO UPDATE SET progress=excluded.progress")) {
                    player.setString(1, data.playerId().toString()); player.setLong(2, System.currentTimeMillis()); player.executeUpdate();
                    for (Map.Entry<String, PlayerQuestData.QuestRecord> entry : data.quests().entrySet()) {
                        var record = entry.getValue(); quest.setString(1, data.playerId().toString()); quest.setString(2, entry.getKey()); quest.setString(3, record.status().name());
                        quest.setLong(4, record.acceptedAt()); quest.setLong(5, record.completedAt()); quest.setLong(6, record.claimedAt()); quest.setLong(7, record.cooldownUntil()); quest.addBatch();
                        for (var progress : record.progress().entrySet()) { objective.setString(1, data.playerId().toString()); objective.setString(2, entry.getKey()); objective.setString(3, progress.getKey()); objective.setInt(4, progress.getValue()); objective.addBatch(); }
                    }
                    quest.executeBatch(); objective.executeBatch(); connection.commit();
                }
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ignored) { }
                plugin.getLogger().warning("Could not save quest data for " + data.playerId() + ": " + e.getMessage());
            } finally { try { connection.setAutoCommit(true); } catch (SQLException ignored) { } }
        }, executor);
    }

    public CompletableFuture<Void> saveAll(Iterable<PlayerQuestData> values) { return CompletableFuture.allOf(java.util.stream.StreamSupport.stream(values.spliterator(), false).map(this::save).toArray(CompletableFuture[]::new)); }
    public void close() { executor.shutdown(); try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } try { if (connection != null) connection.close(); } catch (SQLException ignored) { } }
}
