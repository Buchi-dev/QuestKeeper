package com.questkeeper.quest.service;

import com.questkeeper.message.MessageManager;
import com.questkeeper.quest.model.Quest;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class QuestNotificationService {
    private static final Sound DEFAULT_OBJECTIVES_SOUND = Sound.ENTITY_PLAYER_LEVELUP;
    private static final Sound DEFAULT_COMPLETION_SOUND = Sound.UI_TOAST_CHALLENGE_COMPLETE;

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private Notification objectivesComplete;
    private Notification questComplete;

    public QuestNotificationService(JavaPlugin plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
        reload();
    }

    public void reload() {
        objectivesComplete = read("notifications.objectives-complete", DEFAULT_OBJECTIVES_SOUND, 1.0f, 1.2f);
        questComplete = read("notifications.quest-complete", DEFAULT_COMPLETION_SOUND, 1.0f, 1.0f);
    }

    public void objectivesComplete(Player player, Quest quest) {
        notify(player, quest, objectivesComplete, "quest-objectives-complete",
                "%prefix%<green>Objectives complete! Return to the Quest Keeper to claim your reward for <yellow>%quest%</yellow>.");
    }

    public void questComplete(Player player, Quest quest) {
        notify(player, quest, questComplete, "quest-complete-announcer",
                "%prefix%<gold>Quest completed: <yellow>%quest%</yellow>!");
    }

    private void notify(Player player, Quest quest, Notification notification, String messageKey, String fallback) {
        if (!notification.enabled()) return;
        messages.send(player, messageKey, Map.of("quest", quest.displayName()), fallback);
        player.playSound(player.getLocation(), notification.sound(), notification.volume(), notification.pitch());
    }

    private Notification read(String path, Sound fallbackSound, float fallbackVolume, float fallbackPitch) {
        boolean enabled = plugin.getConfig().getBoolean(path + ".enabled", true);
        Sound sound = sound(plugin.getConfig().getString(path + ".sound"), fallbackSound, path + ".sound");
        float volume = clamp(plugin.getConfig().getDouble(path + ".volume", fallbackVolume), 0.0f, 10.0f);
        float pitch = clamp(plugin.getConfig().getDouble(path + ".pitch", fallbackPitch), 0.0f, 2.0f);
        return new Notification(enabled, sound, volume, pitch);
    }

    private Sound sound(String configured, Sound fallback, String path) {
        if (configured == null || configured.isBlank()) return fallback;
        String soundKey = configured.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '.');
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(soundKey));
        if (sound == null) {
            plugin.getLogger().warning("Invalid sound '" + configured + "' at " + path
                    + "; using " + fallback + ".");
        }
        return sound == null ? fallback : sound;
    }

    private float clamp(double value, float minimum, float maximum) {
        return (float) Math.max(minimum, Math.min(maximum, value));
    }

    private record Notification(boolean enabled, Sound sound, float volume, float pitch) {
    }
}
