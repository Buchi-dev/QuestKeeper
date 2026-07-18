package com.questkeeper.hook;

import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.service.QuestProgressService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class IntegrationManager {
    private final JavaPlugin plugin;

    public IntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(QuestManager quests, QuestProgressService progress) {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            plugin.getLogger().info("Vault detected; money rewards may be enabled by a provider.");
        } else {
            plugin.getLogger().info("Vault was not found. Money rewards are disabled.");
        }

        loadMythicMobs(quests, progress);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            plugin.getLogger().info("PlaceholderAPI detected.");
            if (plugin instanceof com.questkeeper.QuestKeeperPlugin questKeeper) {
                new QuestPlaceholderExpansion(questKeeper).register();
            }
        }
    }

    private void loadMythicMobs(QuestManager quests, QuestProgressService progress) {
        if (!plugin.getConfig().getBoolean("integrations.mythicmobs", true)) {
            plugin.getLogger().info("MythicMobs integration is disabled in config.");
            return;
        }

        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            plugin.getLogger().warning("MythicMobs was not found. MythicMob objectives are disabled.");
            return;
        }

        Bukkit.getPluginManager().registerEvents(new MythicMobsListener(quests, progress), plugin);
        plugin.getLogger().info("MythicMobs detected; MythicMob objectives enabled.");
    }
}
