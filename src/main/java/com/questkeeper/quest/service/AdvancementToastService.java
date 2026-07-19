package com.questkeeper.quest.service;

import com.questkeeper.quest.model.Quest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

/** Displays short-lived, hidden vanilla-style advancement toasts. */
public final class AdvancementToastService {
    private static final String CRITERION = "questkeeper_toast";
    private static final int CLEANUP_TICKS = 120;

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final GsonComponentSerializer gson = GsonComponentSerializer.gson();

    public AdvancementToastService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, Quest quest, String title, String description, String frame, String icon) {
        Material material = resolveIcon(quest, icon);
        String advancementFrame = normalizeFrame(frame);
        Component titleComponent = mini.deserialize(title.replace("%quest%", quest.displayName()));
        Component descriptionComponent = mini.deserialize(description.replace("%quest%", quest.displayName()));
        NamespacedKey key = new NamespacedKey(plugin,
                "toast/" + player.getUniqueId().toString().replace("-", "") + "/" + UUID.randomUUID());
        String json = advancementJson(material, titleComponent, descriptionComponent, advancementFrame);

        Advancement advancement;
        try {
            advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to create quest toast: " + exception.getMessage());
            return;
        }
        if (advancement == null) return;

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        progress.awardCriteria(CRITERION);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                player.getAdvancementProgress(advancement).revokeCriteria(CRITERION);
                Bukkit.getUnsafe().removeAdvancement(key);
            } catch (RuntimeException exception) {
                plugin.getLogger().fine("Unable to clean up quest toast " + key + ": " + exception.getMessage());
            }
        }, CLEANUP_TICKS);
    }

    private Material resolveIcon(Quest quest, String configured) {
        String value = configured == null ? "quest" : configured.trim();
        Material material = value.equalsIgnoreCase("quest")
                ? Material.matchMaterial(quest.iconMaterial())
                : Material.matchMaterial(value);
        if (material == null) {
            plugin.getLogger().warning("Invalid quest toast icon '" + value + "'; using BOOK.");
            return Material.BOOK;
        }
        return material;
    }

    private String normalizeFrame(String configured) {
        String frame = configured == null ? "TASK" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (frame) {
            case "task", "goal", "challenge" -> frame;
            default -> "task";
        };
    }

    private String advancementJson(Material icon, Component title, Component description, String frame) {
        return "{"
                + "\"criteria\":{\"" + CRITERION + "\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"display\":{"
                + "\"icon\":{\"item\":\"minecraft:" + icon.name().toLowerCase(Locale.ROOT) + "\"},"
                + "\"title\":" + gson.serialize(title) + ","
                + "\"description\":" + gson.serialize(description) + ","
                + "\"frame\":\"" + frame + "\","
                + "\"show_toast\":true,"
                + "\"announce_to_chat\":false,"
                + "\"hidden\":true"
                + "}"
                + "}";
    }
}
