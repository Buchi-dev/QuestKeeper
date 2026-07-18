package com.questkeeper.quest;

import com.questkeeper.quest.model.ObjectiveDefinition;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.RepeatType;
import com.questkeeper.quest.model.RequirementDefinition;
import com.questkeeper.quest.model.RewardDefinition;
import com.questkeeper.quest.model.RewardType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class QuestLoader {
    private final JavaPlugin plugin;

    public QuestLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public LoadResult loadResult(File directory) {
        Map<String, Quest> quests = new LinkedHashMap<>();
        File[] files = directory.listFiles((ignored, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return new LoadResult(quests, 0, 0);
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));

        int failed = 0;
        for (File file : files) {
            try {
                Quest quest = parse(file);
                if (quests.containsKey(quest.id())) {
                    throw new IllegalArgumentException("duplicate quest id " + quest.id());
                }
                quests.put(quest.id(), quest);
            } catch (Exception exception) {
                failed++;
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Failed to load quest " + file.getName(), exception);
            }
        }
        return new LoadResult(quests, files.length, failed);
    }

    public Map<String, Quest> load(File directory) {
        return loadResult(directory).quests();
    }

    private Quest parse(File file) {
        var configuration = YamlConfiguration.loadConfiguration(file);
        String id = requiredId(configuration.getString("id"), file, "id");
        String display = Objects.requireNonNullElse(configuration.getString("display-name"), id);
        String icon = material(configuration.getString("icon.material", "BOOK"), file, "icon.material");
        String startNpc = configuration.getString("npc.start", "");
        String claimNpc = configuration.getString("npc.claim", startNpc);

        Map<String, ObjectiveDefinition> objectives = parseObjectives(configuration, file);
        RequirementDefinition requirements = new RequirementDefinition(
                configuration.getString("requirements.permission", ""),
                positiveOrZero(configuration.getInt("requirements.minimum-level", 0), file, "requirements.minimum-level"),
                configuration.getStringList("requirements.completed-quests"),
                configuration.getString("requirements.world", ""));
        List<RewardDefinition> rewards = parseRewards(configuration, file);
        RepeatType repeatType = enumValue(RepeatType.class,
                configuration.getString("settings.repeat-type", "ONE_TIME"), file, "settings.repeat-type");
        long cooldownSeconds = configuration.getLong("settings.cooldown-seconds", 0);
        if (cooldownSeconds < 0) throw new IllegalArgumentException("settings.cooldown-seconds cannot be negative");

        return new Quest(id, display, configuration.getStringList("description"), icon, startNpc, claimNpc,
                objectives, requirements, rewards, repeatType, cooldownSeconds,
                configuration.getBoolean("settings.auto-claim", false),
                configuration.getBoolean("settings.allow-cancel", true),
                configuration.getBoolean("settings.consume-collected-items", false));
    }

    private Map<String, ObjectiveDefinition> parseObjectives(org.bukkit.configuration.file.FileConfiguration configuration,
                                                               File file) {
        ConfigurationSection section = configuration.getConfigurationSection("objectives");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("objectives must contain at least one objective");
        }

        Map<String, ObjectiveDefinition> objectives = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String base = "objectives." + key;
            ObjectiveType type = enumValue(ObjectiveType.class, configuration.getString(base + ".type"), file, base + ".type");
            int amount = positive(configuration.getInt(base + ".amount", 0), file, base + ".amount");
            String text = configuration.getString(base + ".display", "<white>" + key + ": %progress%/%required%");
            ConfigurationSection objectiveSection = configuration.getConfigurationSection(base);
            Map<String, Object> settings = objectiveSection == null
                    ? Map.of() : new HashMap<>(objectiveSection.getValues(true));
            validateObjective(type, settings, file, base);
            objectives.put(key, new ObjectiveDefinition(key, type, amount, text, settings));
        }
        return objectives;
    }

    private void validateObjective(ObjectiveType type, Map<String, Object> settings, File file, String path) {
        switch (type) {
            case KILL_ENTITY -> {
                String entity = setting(settings, "entity", path);
                try {
                    EntityType.valueOf(entity.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(path + ".entity has invalid entity type " + entity, exception);
                }
            }
            case KILL_MYTHIC_MOB -> setting(settings,
                    settings.containsKey("mob-id") ? "mob-id" : "mob", path);
            case COLLECT_ITEM, BREAK_BLOCK, PLACE_BLOCK, CRAFT_ITEM ->
                    material(setting(settings, "material", path), file, path + ".material");
            case INTERACT_NPC -> setting(settings,
                    settings.containsKey("npc-id") ? "npc-id" : "npc", path);
            case EXECUTE_COMMAND -> setting(settings, "command", path);
            case REACH_LOCATION -> {
                number(settings.get("x"), path + ".x");
                number(settings.get("y"), path + ".y");
                number(settings.get("z"), path + ".z");
                double radius = number(settings.getOrDefault("radius", 2), path + ".radius");
                if (radius <= 0) throw new IllegalArgumentException(path + ".radius must be greater than zero");
            }
        }
    }

    private String setting(Map<String, Object> settings, String key, String path) {
        Object value = settings.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(path + "." + key + " is required");
        }
        return value.toString();
    }

    private double number(Object value, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        return number.doubleValue();
    }

    private List<RewardDefinition> parseRewards(org.bukkit.configuration.file.FileConfiguration configuration, File file) {
        List<RewardDefinition> rewards = new ArrayList<>();
        int experience = positiveOrZero(configuration.getInt("rewards.experience", 0), file, "rewards.experience");
        if (experience > 0) rewards.add(new RewardDefinition(RewardType.EXPERIENCE, experience, "", Map.of()));
        int money = positiveOrZero(configuration.getInt("rewards.money", 0), file, "rewards.money");
        if (money > 0) rewards.add(new RewardDefinition(RewardType.MONEY, money, "", Map.of()));
        for (String command : configuration.getStringList("rewards.commands")) {
            if (command.isBlank()) throw new IllegalArgumentException("rewards.commands cannot contain blank commands");
            rewards.add(new RewardDefinition(RewardType.COMMAND, 0, command, Map.of()));
        }

        ConfigurationSection items = configuration.getConfigurationSection("rewards.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                String base = "rewards.items." + key;
                ConfigurationSection itemSection = items.getConfigurationSection(key);
                if (itemSection == null) throw new IllegalArgumentException(base + " must be a section");
                Map<String, Object> settings = new HashMap<>(itemSection.getValues(true));
                rewards.add(new RewardDefinition(RewardType.ITEM,
                        positive(itemSection.getInt("amount", 1), file, base + ".amount"),
                        material(itemSection.getString("material", "STONE"), file, base + ".material"),
                        settings));
            }
        }
        return rewards;
    }

    private String requiredId(String value, File file, String path) {
        if (!com.questkeeper.utility.IdValidator.isValid(value)) {
            throw new IllegalArgumentException(path + " must match ^[a-z0-9_]{3,50}$ in " + file.getName());
        }
        return value;
    }

    private int positive(int value, File file, String path) {
        if (value <= 0) throw new IllegalArgumentException(path + " must be greater than zero in " + file.getName());
        return value;
    }

    private int positiveOrZero(int value, File file, String path) {
        if (value < 0) throw new IllegalArgumentException(path + " cannot be negative in " + file.getName());
        return value;
    }

    private String material(String value, File file, String path) {
        Material material = value == null ? null : Material.matchMaterial(value.trim());
        if (material == null) throw new IllegalArgumentException(path + " has unknown material " + value + " in " + file.getName());
        return material.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, File file, String path) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException(path + " has invalid value " + value + " in " + file.getName(), exception);
        }
    }

    public record LoadResult(Map<String, Quest> quests, int totalFiles, int failedFiles) {
        public boolean success() {
            return failedFiles == 0;
        }
    }
}
