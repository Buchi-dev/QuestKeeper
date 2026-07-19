package com.questkeeper;

import com.questkeeper.api.QuestKeeperAPI;
import com.questkeeper.database.DatabaseManager;
import com.questkeeper.gui.GuiManager;
import com.questkeeper.hook.IntegrationManager;
import com.questkeeper.listener.QuestListener;
import com.questkeeper.message.MessageManager;
import com.questkeeper.npc.NpcManager;
import com.questkeeper.quest.QuestLoader;
import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.service.QuestClaimService;
import com.questkeeper.quest.service.QuestProgressService;
import com.questkeeper.quest.service.QuestStateService;
import com.questkeeper.quest.service.PlayerQuestDataService;
import com.questkeeper.quest.service.RequirementService;
import com.questkeeper.quest.service.QuestNotificationService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;
import java.util.Objects;

public final class QuestKeeperPlugin extends JavaPlugin {
    private DatabaseManager database;
    private PlayerQuestDataService data;
    private QuestManager quests;
    private RequirementService requirements;
    private QuestStateService states;
    private QuestProgressService progress;
    private QuestClaimService claims;
    private NpcManager npcs;
    private GuiManager guis;
    private MessageManager messages;
    private QuestNotificationService notifications;
    private QuestKeeperAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("guis.yml");
        saveResourceIfMissing("npcs.yml");
        new File(getDataFolder(), "quests").mkdirs();
        saveResourceIfMissing("quests/example_quest.yml");

        messages = new MessageManager(getDataFolder());
        messages.load();
        notifications = new QuestNotificationService(this, messages);
        database = new DatabaseManager(this, new File(getDataFolder(), getConfig().getString("database.file", "quests.db")));
        data = new PlayerQuestDataService(database);
        quests = new QuestManager();
        requirements = new RequirementService(data);
        ZoneId zone;
        try {
            zone = ZoneId.of(getConfig().getString("quest-resets.timezone", "UTC"));
        } catch (Exception exception) {
            zone = ZoneId.of("UTC");
        }
        states = new QuestStateService(data, requirements, zone);
        progress = new QuestProgressService(data, quests, states);
        claims = new QuestClaimService(this, quests, data, states, requirements, messages);
        npcs = new NpcManager(this);
        guis = new GuiManager(this, quests, npcs, states, progress, requirements, messages);
        api = new QuestKeeperAPI(quests, data, states, progress, claims);
        reloadQuestKeeper();

        Bukkit.getPluginManager().registerEvents(new QuestListener(this, npcs, quests, guis, progress, claims, data, states, notifications), this);
        var playerCommand = new com.questkeeper.command.QuestCommand(guis, claims, quests, messages);
        Objects.requireNonNull(getCommand("quests"), "quests command is missing from plugin.yml").setExecutor(playerCommand);
        Objects.requireNonNull(getCommand("quests"), "quests command is missing from plugin.yml").setTabCompleter(playerCommand);
        var adminCommand = new com.questkeeper.command.QuestAdminCommand(this);
        Objects.requireNonNull(getCommand("questadmin"), "questadmin command is missing from plugin.yml").setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("questadmin"), "questadmin command is missing from plugin.yml").setTabCompleter(adminCommand);
        Objects.requireNonNull(getCommand("questkeeper"), "questkeeper command is missing from plugin.yml").setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("questkeeper"), "questkeeper command is missing from plugin.yml").setTabCompleter(adminCommand);
        Bukkit.getServicesManager().register(QuestKeeperAPI.class, api, this, ServicePriority.Normal);

        long autosave = Math.max(1L, getConfig().getLong("autosave-interval-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, data::saveAll, autosave, autosave);
        long npcCheck = Math.max(1L, getConfig().getLong("npc-validation-interval-seconds", 30)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, npcs::respawnMissing, npcCheck, npcCheck);
        new IntegrationManager(this).load(quests, progress);
        getLogger().info("QuestKeeper enabled.");
    }

    private void saveResourceIfMissing(String path) {
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    public boolean reloadQuestKeeper() {
        savePending();
        reloadConfig();
        QuestLoader.LoadResult loaded = new QuestLoader(this)
                .loadResult(new File(getDataFolder(), "quests"));
        if (!loaded.success()) {
            getLogger().severe("QuestKeeper reload aborted: " + loaded.failedFiles()
                    + " of " + loaded.totalFiles() + " quest files failed validation.");
            return false;
        }
        messages.load();
        guis.reload();
        notifications.reload();
        npcs.removeAll();
        quests.replace(loaded.quests());
        npcs.load();
        npcs.spawnAll();
        getLogger().info("Loaded " + quests.all().size() + " quests.");
        getLogger().info("Loaded " + npcs.all().size() + " QuestKeeper NPCs.");
        return true;
    }

    private void savePending() {
        if (data != null) data.saveAll();
    }

    public QuestManager quests() { return quests; }
    public NpcManager npcs() { return npcs; }
    public PlayerQuestDataService data() { return data; }
    public QuestClaimService claims() { return claims; }
    public MessageManager messages() { return messages; }
    public QuestKeeperAPI api() { return api; }

    @Override
    public void onDisable() {
        if (data != null) data.saveAll();
        if (npcs != null) npcs.removeAll();
        if (database != null) database.close();
    }
}
