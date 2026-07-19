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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    public void show(Player player, Quest quest, String title, String description, String frame, String icon,
                     int customModelData, String itemModel) {
        ItemStack iconStack = resolveIcon(quest, icon, customModelData, itemModel);
        String advancementFrame = normalizeFrame(frame);
        Component titleComponent = mini.deserialize(resolveText(title, quest));
        Component descriptionComponent = mini.deserialize(resolveText(description, quest));
        NamespacedKey key = new NamespacedKey(plugin,
                "toast/" + player.getUniqueId().toString().replace("-", "") + "/" + UUID.randomUUID());
        String json = advancementJson(iconStack, titleComponent, descriptionComponent, advancementFrame);

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

    private ItemStack resolveIcon(Quest quest, String configured, int customModelData, String itemModel) {
        String value = configured == null ? "quest" : configured.trim();
        Material material = value.equalsIgnoreCase("quest")
                ? Material.matchMaterial(quest.iconMaterial())
                : Material.matchMaterial(value);
        if (material == null) {
            plugin.getLogger().warning("Invalid quest toast icon '" + value + "'; using BOOK.");
            material = Material.BOOK;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (customModelData > 0) meta.setCustomModelData(customModelData);
        if (itemModel != null && !itemModel.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.trim().toLowerCase(Locale.ROOT));
            if (key == null) {
                plugin.getLogger().warning("Invalid quest toast item model '" + itemModel + "'; ignoring it.");
            } else {
                meta.setItemModel(key);
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String resolveText(String template, Quest quest) {
        String level = questLevel(quest.id());
        String name = quest.displayName();
        if (!level.isBlank()) {
            name = name.replaceFirst("\\s+(?:I|II|III|IV|V|1|2|3|4|5)$", "");
        }
        return template
                .replace("%quest%", quest.displayName())
                .replace("%quest-name%", name)
                .replace("%level%", level);
    }

    private String questLevel(String questId) {
        if (questId == null || questId.isEmpty()) return "";
        char last = questId.charAt(questId.length() - 1);
        if (questId.length() < 2 || questId.charAt(questId.length() - 2) != '_'
                || last < '1' || last > '5') return "";
        return switch (last) {
            case '1' -> "I";
            case '2' -> "II";
            case '3' -> "III";
            case '4' -> "IV";
            case '5' -> "V";
            default -> "";
        };
    }

    private String normalizeFrame(String configured) {
        String frame = configured == null ? "TASK" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (frame) {
            case "task", "goal", "challenge" -> frame;
            default -> "task";
        };
    }

    private String advancementJson(ItemStack icon, Component title, Component description, String frame) {
        String iconJson = Bukkit.getUnsafe().serializeItemAsJson(icon).toString();
        return "{"
                + "\"criteria\":{\"" + CRITERION + "\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"display\":{"
                + "\"icon\":" + iconJson + ","
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
