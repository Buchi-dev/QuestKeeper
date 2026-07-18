package com.questkeeper.quest;

import com.questkeeper.quest.model.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public final class QuestLoader {
    private final JavaPlugin plugin; public QuestLoader(JavaPlugin plugin) { this.plugin = plugin; }
    public Map<String, Quest> load(File directory) { Map<String, Quest> quests = new LinkedHashMap<>(); File[] files = directory.listFiles((d, n) -> n.endsWith(".yml")); if (files == null) return quests; for (File file : files) { try { Quest quest = parse(file); quests.put(quest.id(), quest); } catch (Exception e) { plugin.getLogger().warning("Failed to load " + file.getName() + ": " + e.getMessage()); } } return quests; }
    private Quest parse(File file) {
        var c = YamlConfiguration.loadConfiguration(file); String id = requiredId(c.getString("id"), file, "id");
        String display = Objects.requireNonNullElse(c.getString("display-name"), id); String icon = material(c.getString("icon.material", "BOOK"), file, "icon.material");
        String start = c.getString("npc.start", ""); String claim = c.getString("npc.claim", start);
        Map<String, ObjectiveDefinition> objectives = new LinkedHashMap<>(); ConfigurationSection os = c.getConfigurationSection("objectives"); if (os == null || os.getKeys(false).isEmpty()) throw new IllegalArgumentException("objectives must contain at least one objective");
        for (String key : os.getKeys(false)) { String base = "objectives." + key; ObjectiveType type = enumValue(ObjectiveType.class, c.getString(base + ".type"), file, base + ".type"); int amount = c.getInt(base + ".amount", 0); String text = c.getString(base + ".display", "<white>" + key + ": %progress%/%required%"); Map<String, Object> settings = new HashMap<>(c.getConfigurationSection(base) == null ? Map.of() : c.getConfigurationSection(base).getValues(true)); objectives.put(key, new ObjectiveDefinition(key, type, amount, text, settings)); }
        List<String> completed = c.getStringList("requirements.completed-quests"); RequirementDefinition requirements = new RequirementDefinition(c.getString("requirements.permission", ""), c.getInt("requirements.minimum-level", 0), completed, c.getString("requirements.world", ""));
        List<RewardDefinition> rewards = new ArrayList<>(); int experience = c.getInt("rewards.experience", 0); if (experience > 0) rewards.add(new RewardDefinition(RewardType.EXPERIENCE, experience, "", Map.of())); int money = c.getInt("rewards.money", 0); if (money > 0) rewards.add(new RewardDefinition(RewardType.MONEY, money, "", Map.of())); for (String command : c.getStringList("rewards.commands")) rewards.add(new RewardDefinition(RewardType.COMMAND, 0, command, Map.of())); ConfigurationSection items = c.getConfigurationSection("rewards.items"); if (items != null) for (String key : items.getKeys(false)) { String base = "rewards.items." + key; Map<String, Object> settings = new HashMap<>(items.getConfigurationSection(key).getValues(true)); rewards.add(new RewardDefinition(RewardType.ITEM, c.getInt(base + ".amount", 1), material(c.getString(base + ".material", "STONE"), file, base + ".material"), settings)); }
        RepeatType repeat = enumValue(RepeatType.class, c.getString("settings.repeat-type", "ONE_TIME"), file, "settings.repeat-type"); return new Quest(id, display, c.getStringList("description"), icon, start, claim, objectives, requirements, rewards, repeat, c.getLong("settings.cooldown-seconds", 0), c.getBoolean("settings.auto-claim", false), c.getBoolean("settings.allow-cancel", true), c.getBoolean("settings.consume-collected-items", false));
    }
    private String requiredId(String value, File file, String path) { if (!com.questkeeper.utility.IdValidator.isValid(value)) throw new IllegalArgumentException(path + " must match ^[a-z0-9_]{3,50}$ in " + file.getName()); return value; }
    private String material(String value, File file, String path) { try { if (Material.matchMaterial(value) == null) throw new IllegalArgumentException("unknown material " + value); return Material.matchMaterial(value).name(); } catch (Exception e) { throw new IllegalArgumentException(path + ": " + e.getMessage()); } }
    private <T extends Enum<T>> T enumValue(Class<T> type, String value, File file, String path) { try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); } catch (Exception e) { throw new IllegalArgumentException(path + " has invalid value " + value + " in " + file.getName()); } }
}
