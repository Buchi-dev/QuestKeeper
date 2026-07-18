package com.questkeeper.command;

import com.questkeeper.QuestKeeperPlugin;
import com.questkeeper.utility.IdValidator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestAdminCommand implements CommandExecutor, TabCompleter {
    private final QuestKeeperPlugin plugin;

    public QuestAdminCommand(QuestKeeperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("questkeeper.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/questadmin reload|quest|npc|progress|complete|reset|resetall|debug");
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> reload(sender);
                case "quest" -> quest(sender, args);
                case "npc" -> npc(sender, args);
                case "progress" -> progress(sender, args);
                case "complete" -> complete(sender, args);
                case "reset" -> reset(sender, args);
                case "resetall" -> resetAll(sender);
                case "debug" -> debug(sender, args);
                default -> sender.sendMessage("Unknown subcommand.");
            }
        } catch (IllegalArgumentException | IOException exception) {
            sender.sendMessage("Command failed: " + exception.getMessage());
            plugin.getLogger().warning("Admin command failed: " + exception.getMessage());
        }
        return true;
    }

    private void reload(CommandSender sender) {
        sender.sendMessage(plugin.reloadQuestKeeper()
                ? "QuestKeeper reloaded."
                : "QuestKeeper reload failed; the previous quest registry was preserved.");
    }

    private void quest(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) {
            sender.sendMessage("quest list|create|validate");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> plugin.quests().all()
                    .forEach(quest -> sender.sendMessage(quest.id() + " - " + quest.displayName()));
            case "create" -> createQuest(sender, args);
            case "validate" -> validateQuest(sender, args);
            default -> sender.sendMessage("Unsupported quest operation.");
        }
    }

    private void createQuest(CommandSender sender, String[] args) throws IOException {
        requireArgument(args, 2, "Usage: /questadmin quest create <id>");
        requireId(args[2]);
        File file = new File(plugin.getDataFolder(), "quests/" + args[2] + ".yml");
        if (file.exists()) {
            sender.sendMessage("Already exists.");
            return;
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("id", args[2]);
        configuration.set("display-name", "<gold>New Quest");
        configuration.set("description", List.of("<gray>Configure me."));
        configuration.set("objectives.kill.type", "KILL_ENTITY");
        configuration.set("objectives.kill.entity", "ZOMBIE");
        configuration.set("objectives.kill.amount", 1);
        configuration.set("objectives.kill.display", "<yellow>Kill zombies: <white>%progress%/%required%");
        configuration.set("requirements.minimum-level", 0);
        configuration.set("settings.repeat-type", "ONE_TIME");
        configuration.set("settings.allow-cancel", true);
        configuration.save(file);
        sender.sendMessage("Created " + args[2] + "; edit YAML then reload.");
    }

    private void validateQuest(CommandSender sender, String[] args) {
        requireArgument(args, 2, "Usage: /questadmin quest validate <id>");
        sender.sendMessage(plugin.quests().get(args[2]) != null
                ? "Quest is loaded and valid."
                : "Quest is missing or invalid.");
    }

    private void npc(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) {
            sender.sendMessage("npc list|create|remove|delete|assign|unassign");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> plugin.npcs().all()
                    .forEach(npc -> sender.sendMessage(npc.id() + " - " + npc.displayName()));
            case "create" -> createNpc(sender, args);
            case "remove", "delete" -> removeNpc(sender, args);
            case "assign", "unassign" -> updateNpcQuests(sender, args);
            default -> sender.sendMessage("Unsupported NPC operation.");
        }
    }

    private void createNpc(CommandSender sender, String[] args) throws IOException {
        requireArgument(args, 2, "Usage: /questadmin npc create <id>");
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        requireId(args[2]);
        File file = new File(plugin.getDataFolder(), "npcs.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String base = "npcs." + args[2];
        if (configuration.contains(base)) {
            sender.sendMessage("Already exists.");
            return;
        }
        configuration.set(base + ".display-name", "<gold>" + args[2]);
        configuration.set(base + ".profession", "NONE");
        configuration.set(base + ".villager-type", "PLAINS");
        configuration.set(base + ".silent", true);
        Location location = player.getLocation();
        configuration.set(base + ".location.world", location.getWorld().getName());
        configuration.set(base + ".location.x", location.getX());
        configuration.set(base + ".location.y", location.getY());
        configuration.set(base + ".location.z", location.getZ());
        configuration.set(base + ".location.yaw", location.getYaw());
        configuration.set(base + ".location.pitch", location.getPitch());
        configuration.set(base + ".greeting", List.of("<yellow>Select a quest if you are prepared."));
        configuration.set(base + ".quests", List.of());
        configuration.save(file);
        plugin.reloadQuestKeeper();
        sender.sendMessage("Created NPC " + args[2]);
    }

    private void removeNpc(CommandSender sender, String[] args) throws IOException {
        requireArgument(args, 2, "Usage: /questadmin npc remove <id>");
        requireId(args[2]);
        File file = new File(plugin.getDataFolder(), "npcs.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        configuration.set("npcs." + args[2], null);
        configuration.save(file);
        plugin.reloadQuestKeeper();
        sender.sendMessage("Removed NPC " + args[2]);
    }

    private void updateNpcQuests(CommandSender sender, String[] args) throws IOException {
        requireArgument(args, 3, "Usage: /questadmin npc assign|unassign <npc> <quest>");
        requireId(args[2]);
        File file = new File(plugin.getDataFolder(), "npcs.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String path = "npcs." + args[2] + ".quests";
        if (!configuration.contains("npcs." + args[2])) {
            throw new IllegalArgumentException("NPC does not exist: " + args[2]);
        }
        if (args[1].equalsIgnoreCase("assign") && !plugin.quests().contains(args[3])) {
            throw new IllegalArgumentException("Quest does not exist: " + args[3]);
        }
        List<String> quests = new ArrayList<>(configuration.getStringList(path));
        if (args[1].equalsIgnoreCase("assign") && !quests.contains(args[3])) quests.add(args[3]);
        if (args[1].equalsIgnoreCase("unassign")) quests.remove(args[3]);
        configuration.set(path, quests);
        configuration.save(file);
        plugin.reloadQuestKeeper();
        sender.sendMessage("Updated NPC quests.");
    }

    private void progress(CommandSender sender, String[] args) {
        requireArgument(args, 4, "/questadmin progress <player> <quest> <objective> <amount>");
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("Player must be online.");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[4]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("amount must be an integer");
        }
        if (amount <= 0) throw new IllegalArgumentException("amount must be greater than zero");
        plugin.api().addProgress(player, args[2], args[3], amount);
        sender.sendMessage("Progress added.");
    }

    private void complete(CommandSender sender, String[] args) {
        requireArgument(args, 2, "/questadmin complete <player> <quest>");
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("Player must be online.");
            return;
        }
        sender.sendMessage(plugin.api().completeQuest(player, args[2])
                ? "Quest objectives completed." : "Unable to complete quest.");
    }

    private void reset(CommandSender sender, String[] args) {
        requireArgument(args, 2, "/questadmin reset <player> <quest>");
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("Player must be online.");
            return;
        }
        plugin.api().resetQuest(player.getUniqueId(), args[2]);
        sender.sendMessage("Quest reset.");
    }

    private void resetAll(CommandSender sender) {
        sender.sendMessage("Use reset for each quest; bulk reset is intentionally conservative.");
    }

    private void debug(CommandSender sender, String[] args) {
        requireArgument(args, 1, "/questadmin debug <player>");
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage("Player must be online.");
            return;
        }
        plugin.quests().all().forEach(quest -> sender.sendMessage(quest.id() + ": "
                + plugin.api().getPlayerQuestState(player.getUniqueId(), quest.id())));
    }

    private void requireId(String id) {
        if (!IdValidator.isValid(id)) throw new IllegalArgumentException("invalid id");
    }

    private void requireArgument(String[] args, int index, String usage) {
        if (args.length <= index) throw new IllegalArgumentException(usage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "quest", "npc", "progress", "complete", "reset", "resetall", "debug");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quest")) {
            return List.of("list", "create", "validate");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return List.of("list", "create", "remove", "delete", "assign", "unassign");
        }
        return List.of();
    }
}
