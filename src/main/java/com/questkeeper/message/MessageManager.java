package com.questkeeper.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.Map;

public final class MessageManager {
    private final File file; private final MiniMessage mini = MiniMessage.miniMessage(); private FileConfiguration config;
    public MessageManager(File dataFolder) { file = new File(dataFolder, "messages.yml"); }
    public void load() { config = YamlConfiguration.loadConfiguration(file); }
    public void send(Player player, String key, Map<String, String> replacements) {
        String raw = config.getString(key, "<red>Missing message: " + key + "</red>");
        raw = raw.replace("%prefix%", config.getString("prefix", ""));
        for (var entry : replacements.entrySet()) raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        player.sendMessage(mini.deserialize(raw));
    }
    public Component component(String raw, Map<String, String> replacements) {
        for (var entry : replacements.entrySet()) raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        return mini.deserialize(raw);
    }
}
