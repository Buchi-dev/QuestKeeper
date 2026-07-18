package com.questkeeper.database;

import com.questkeeper.quest.model.PlayerQuestData;
import com.questkeeper.quest.model.QuestStatus;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "QuestKeeper-SQLite");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create database directory " + parent);
        }
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            initialize();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to open SQLite database", exception);
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate("PRAGMA busy_timeout=5000");
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
            loadQuestRecords(uuid, data);
            loadProgress(uuid, data);
            return data;
        }, executor);
    }

    private void loadQuestRecords(UUID uuid, PlayerQuestData data) {
        String sql = "SELECT quest_id,status,accepted_at,completed_at,claimed_at,cooldown_until "
                + "FROM player_quests WHERE player_uuid=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        var record = data.record(result.getString(1));
                        record.status(QuestStatus.valueOf(result.getString(2)));
                        record.acceptedAt(result.getLong(3));
                        record.completedAt(result.getLong(4));
                        record.claimedAt(result.getLong(5));
                        record.cooldownUntil(result.getLong(6));
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("Ignoring invalid quest status for " + uuid + ": " + exception.getMessage());
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not load quest data for " + uuid, exception);
        }
    }

    private void loadProgress(UUID uuid, PlayerQuestData data) {
        String sql = "SELECT quest_id,objective_id,progress FROM objective_progress WHERE player_uuid=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    data.record(result.getString(1)).progress().put(result.getString(2), result.getInt(3));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not load objective progress for " + uuid, exception);
        }
    }

    public CompletableFuture<Void> save(PlayerQuestData.Snapshot data) {
        return CompletableFuture.runAsync(() -> saveSnapshot(data), executor);
    }

    private void saveSnapshot(PlayerQuestData.Snapshot data) {
        String deleteQuests = "DELETE FROM player_quests WHERE player_uuid=?";
        String deleteProgress = "DELETE FROM objective_progress WHERE player_uuid=?";
        String upsertPlayer = "INSERT INTO players(player_uuid,last_seen) VALUES(?,?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET last_seen=excluded.last_seen";
        String insertQuest = "INSERT INTO player_quests(player_uuid,quest_id,status,accepted_at,completed_at,claimed_at,cooldown_until) "
                + "VALUES(?,?,?,?,?,?,?)";
        String insertProgress = "INSERT INTO objective_progress(player_uuid,quest_id,objective_id,progress) VALUES(?,?,?,?)";

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement player = connection.prepareStatement(upsertPlayer);
                 PreparedStatement questDelete = connection.prepareStatement(deleteQuests);
                 PreparedStatement progressDelete = connection.prepareStatement(deleteProgress);
                 PreparedStatement questInsert = connection.prepareStatement(insertQuest);
                 PreparedStatement progressInsert = connection.prepareStatement(insertProgress)) {
                String playerId = data.playerId().toString();
                player.setString(1, playerId);
                player.setLong(2, System.currentTimeMillis());
                player.executeUpdate();

                questDelete.setString(1, playerId);
                questDelete.executeUpdate();
                progressDelete.setString(1, playerId);
                progressDelete.executeUpdate();

                for (var entry : data.quests().entrySet()) {
                    var record = entry.getValue();
                    questInsert.setString(1, playerId);
                    questInsert.setString(2, entry.getKey());
                    questInsert.setString(3, record.status().name());
                    questInsert.setLong(4, record.acceptedAt());
                    questInsert.setLong(5, record.completedAt());
                    questInsert.setLong(6, record.claimedAt());
                    questInsert.setLong(7, record.cooldownUntil());
                    questInsert.addBatch();

                    for (var progress : record.progress().entrySet()) {
                        progressInsert.setString(1, playerId);
                        progressInsert.setString(2, entry.getKey());
                        progressInsert.setString(3, progress.getKey());
                        progressInsert.setInt(4, progress.getValue());
                        progressInsert.addBatch();
                    }
                }
                questInsert.executeBatch();
                progressInsert.executeBatch();
                connection.commit();
            }
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not save quest data for " + data.playerId(), exception);
            throw new CompletionException(exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not reset SQLite transaction state", exception);
            }
        }
    }

    public CompletableFuture<Void> saveAll(List<PlayerQuestData.Snapshot> values) {
        List<CompletableFuture<Void>> saves = new ArrayList<>(values.size());
        for (PlayerQuestData.Snapshot value : values) {
            saves.add(save(value));
        }
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Interrupted while closing SQLite executor", exception);
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not close SQLite connection", exception);
        }
    }
}
