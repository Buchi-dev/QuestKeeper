package com.questkeeper.command;

import com.questkeeper.gui.GuiManager;
import com.questkeeper.gui.QuestCatalogFilter;
import com.questkeeper.message.MessageManager;
import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.service.QuestClaimService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuestCommand implements CommandExecutor, TabCompleter {
    private final GuiManager guis;
    private final QuestClaimService claims;
    private final QuestManager quests;
    private final MessageManager messages;

    public QuestCommand(GuiManager guis, QuestClaimService claims, QuestManager quests, MessageManager messages) {
        this.guis = guis;
        this.claims = claims;
        this.quests = quests;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("questkeeper.quests")) {
            messages.send(player, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            guis.openJournal(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "active" -> guis.openCatalog(player, "", 0, QuestCatalogFilter.ACTIVE);
            case "completed" -> guis.openCatalog(player, "", 0, QuestCatalogFilter.COMPLETED);
            case "tracked" -> guis.openJournal(player);
            case "help" -> sender.sendMessage("/quests [active|completed|tracked|cancel <quest>]");
            case "cancel" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /quests cancel <quest>");
                } else {
                    claims.cancel(player, args[1]);
                }
            }
            default -> guis.openJournal(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("active", "completed", "tracked", "help", "cancel");
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            return quests.all().stream().map(quest -> quest.id()).toList();
        }
        return List.of();
    }
}
