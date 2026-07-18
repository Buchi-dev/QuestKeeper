package com.questkeeper.hook;

import com.questkeeper.QuestKeeperPlugin;
import com.questkeeper.quest.model.QuestStatus;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class QuestPlaceholderExpansion extends PlaceholderExpansion {
    private final QuestKeeperPlugin plugin;
    public QuestPlaceholderExpansion(QuestKeeperPlugin plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "questkeeper"; }
    @Override public @NotNull String getAuthor() { return "QuestKeeper"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        if (params.equalsIgnoreCase("active_count") || params.equalsIgnoreCase("completed_count") || params.equalsIgnoreCase("ready_count")) {
            QuestStatus wanted = params.startsWith("active") ? QuestStatus.ACTIVE : params.startsWith("completed") ? QuestStatus.COMPLETED : QuestStatus.READY_TO_CLAIM;
            return String.valueOf(plugin.api().getQuests().stream().filter(q -> plugin.api().getPlayerQuestState(player.getUniqueId(), q.id()) == wanted).count());
        }
        if (params.startsWith("quest_") && params.endsWith("_status")) { String id = params.substring(6, params.length() - 7); return plugin.api().getQuest(id) == null ? "UNKNOWN" : plugin.api().getPlayerQuestState(player.getUniqueId(), id).name(); }
        if (params.startsWith("quest_") && params.endsWith("_progress")) { String id = params.substring(6, params.length() - 9); var q = plugin.api().getQuest(id); if (q == null) return "0"; return String.valueOf(q.objectives().values().stream().mapToInt(o -> plugin.api().getProgress(player, id, o.id())).sum()); }
        if (params.equalsIgnoreCase("tracked_quest")) return "";
        return null;
    }
}
