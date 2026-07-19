package com.questkeeper.quest.service;

import com.questkeeper.message.MessageManager;
import com.questkeeper.quest.model.Quest;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Coordinates configurable toast, chat, and sound notifications for quest events. */
public final class QuestNotificationService {
    private static final Sound DEFAULT_OBJECTIVES_SOUND = Sound.ENTITY_PLAYER_LEVELUP;
    private static final Sound DEFAULT_COMPLETION_SOUND = Sound.UI_TOAST_CHALLENGE_COMPLETE;

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final AdvancementToastService toasts;
    private final EnumMap<Event, Notification> notifications = new EnumMap<>(Event.class);
    private FileConfiguration config;

    public QuestNotificationService(JavaPlugin plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.toasts = new AdvancementToastService(plugin);
        reload();
    }

    public void reload() {
        config = plugin.getConfig();
        notifications.clear();
        notifications.put(Event.ACCEPTED, read(Event.ACCEPTED, "notifications.events.quest-accepted",
                "notifications.quest-accepted", "<green>Quest Accepted", "<yellow>%quest%", "TASK", true));
        notifications.put(Event.OBJECTIVES_COMPLETED, read(Event.OBJECTIVES_COMPLETED,
                "notifications.events.objectives-completed", "notifications.objectives-complete",
                "<gold>Objectives Complete", "<yellow>Return to claim: %quest%", "GOAL", true));
        notifications.put(Event.CLAIMED, read(Event.CLAIMED, "notifications.events.quest-claimed",
                "notifications.quest-complete", "<gold>Quest Completed", "<yellow>%quest%", "CHALLENGE", true));
        notifications.put(Event.CANCELLED, read(Event.CANCELLED, "notifications.events.quest-cancelled",
                "notifications.quest-cancelled", "<yellow>Quest Cancelled", "<yellow>%quest%", "TASK", false));
        notifications.put(Event.LOCKED, read(Event.LOCKED, "notifications.events.quest-locked",
                "notifications.quest-locked", "<red>Quest Locked", "<yellow>%quest%", "TASK", false));
    }

    public void questAccepted(Player player, Quest quest) {
        notify(Event.ACCEPTED, player, quest, "quest-accepted",
                "%prefix%<green>You accepted <yellow>%quest%</yellow>.");
    }

    public void objectivesComplete(Player player, Quest quest) {
        notify(Event.OBJECTIVES_COMPLETED, player, quest, "quest-objectives-complete",
                "%prefix%<green>Objectives complete! Return to the Quest Keeper to claim your reward for <yellow>%quest%</yellow>.");
    }

    public void questClaimed(Player player, Quest quest) {
        notify(Event.CLAIMED, player, quest, "quest-claimed",
                "%prefix%<green>You claimed the rewards for <yellow>%quest%</yellow>.");
    }

    /** Compatibility alias for integrations using the previous method name. */
    public void questComplete(Player player, Quest quest) {
        questClaimed(player, quest);
    }

    public void questCancelled(Player player, Quest quest) {
        notify(Event.CANCELLED, player, quest, "quest-cancelled",
                "%prefix%<yellow>You cancelled <gold>%quest%</gold>.");
    }

    public void questLocked(Player player, Quest quest) {
        notify(Event.LOCKED, player, quest, "quest-locked",
                "%prefix%<red>You have not met this quest's requirements.");
    }

    private void notify(Event event, Player player, Quest quest, String messageKey, String fallback) {
        Notification notification = notifications.get(event);
        if (notification == null || !notification.enabled()) return;
        if (notification.chat()) messages.send(player, messageKey, Map.of("quest", quest.displayName()), fallback);
        if (notification.toast()) {
            toasts.show(player, quest, notification.title(), notification.description(),
                    notification.frame(), notification.icon(), notification.customModelData(), notification.itemModel());
        }
        if (notification.sound().enabled()) {
            SoundSettings sound = notification.sound();
            player.playSound(player.getLocation(), sound.soundKey(), sound.category(), sound.volume(), sound.pitch());
        }
    }

    private Notification read(Event event, String path, String legacyPath, String defaultTitle,
                              String defaultDescription, String defaultFrame, boolean defaultToast) {
        if (!config.contains(path) && config.contains(legacyPath)) {
            String soundKey = soundKey(config.getString(legacyPath + ".sound"), defaultSound(event), legacyPath + ".sound");
            float volume = clamp(config.getDouble(legacyPath + ".volume", 1.0), 0.0f, 10.0f);
            float pitch = clamp(config.getDouble(legacyPath + ".pitch", 1.0), 0.0f, 2.0f);
            return new Notification(config.getBoolean(legacyPath + ".enabled", true), false, true,
                    new SoundSettings(true, soundKey, SoundCategory.MASTER, volume, pitch), defaultTitle,
                    defaultDescription, defaultFrame, "quest", 0, "");
        }

        String defaults = "notifications.defaults";
        boolean enabled = config.getBoolean(path + ".enabled", true);
        boolean toast = channel(path + ".toast", config.getBoolean(defaults + ".toast", defaultToast));
        boolean chat = channel(path + ".chat", config.getBoolean(defaults + ".chat", false));
        boolean soundEnabled = channel(path + ".sound", config.getBoolean(defaults + ".sound", true));
        String soundKey = soundKey(config.getString(path + ".sound.name"), defaultSound(event), path + ".sound.name");
        SoundCategory category = category(config.getString(path + ".sound.category"), SoundCategory.MASTER,
                path + ".sound.category");
        float volume = clamp(config.getDouble(path + ".sound.volume", 1.0), 0.0f, 10.0f);
        float pitch = clamp(config.getDouble(path + ".sound.pitch", 1.0), 0.0f, 2.0f);
        String title = config.getString(path + ".toast.title", defaultTitle);
        String description = config.getString(path + ".toast.description", defaultDescription);
        String frame = config.getString(path + ".toast.frame", defaultFrame);
        String icon = config.getString(path + ".toast.icon", "quest");
        int customModelData = Math.max(0, config.getInt(path + ".toast.custom-model-data", 0));
        String itemModel = config.getString(path + ".toast.item-model", "");
        return new Notification(enabled, toast, chat, new SoundSettings(soundEnabled, soundKey, category, volume, pitch),
                title, description, frame, icon, customModelData, itemModel);
    }

    private boolean channel(String path, boolean fallback) {
        if (config.isBoolean(path)) return config.getBoolean(path);
        return config.getBoolean(path + ".enabled", fallback);
    }

    private Sound defaultSound(Event event) {
        return event == Event.OBJECTIVES_COMPLETED ? DEFAULT_OBJECTIVES_SOUND : DEFAULT_COMPLETION_SOUND;
    }

    private String soundKey(String configured, Sound fallback, String path) {
        if (configured == null || configured.isBlank()) return fallback.getKey().toString();
        String value = configured.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value.replace('_', '.');
        if (NamespacedKey.fromString(value) == null) {
            plugin.getLogger().warning("Invalid sound key '" + configured + "' at " + path
                    + "; using " + fallback.getKey() + ".");
            return fallback.getKey().toString();
        }
        if (value.startsWith("minecraft:")) {
            String vanillaKey = value.substring("minecraft:".length());
            if (Registry.SOUNDS.get(NamespacedKey.minecraft(vanillaKey)) == null) {
                plugin.getLogger().warning("Unknown vanilla sound '" + configured + "' at " + path
                        + "; using " + fallback.getKey() + ".");
                return fallback.getKey().toString();
            }
        }
        return value;
    }

    private SoundCategory category(String configured, SoundCategory fallback, String path) {
        if (configured == null || configured.isBlank()) return fallback;
        try {
            return SoundCategory.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid sound category '" + configured + "' at " + path
                    + "; using " + fallback.name() + ".");
            return fallback;
        }
    }

    private float clamp(double value, float minimum, float maximum) {
        return (float) Math.max(minimum, Math.min(maximum, value));
    }

    private enum Event {
        ACCEPTED, OBJECTIVES_COMPLETED, CLAIMED, CANCELLED, LOCKED
    }

    private record Notification(boolean enabled, boolean toast, boolean chat, SoundSettings sound,
                                String title, String description, String frame, String icon,
                                int customModelData, String itemModel) {
    }

    private record SoundSettings(boolean enabled, String soundKey, SoundCategory category,
                                 float volume, float pitch) {
    }
}
