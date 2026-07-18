package com.questkeeper.quest.service;

import com.questkeeper.quest.model.*;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public final class RequirementService {
    private final PlayerQuestDataService data;
    public RequirementService(PlayerQuestDataService data) { this.data = data; }
    public List<String> missing(Player player, Quest quest) {
        List<String> missing = new ArrayList<>();
        if (!data.isLoaded(player.getUniqueId())) {
            missing.add("Quest data is still loading");
            return missing;
        }

        RequirementDefinition requirements = quest.requirements();
        if (!requirements.permission().isBlank() && !player.hasPermission(requirements.permission())) {
            missing.add("Permission: " + requirements.permission());
        }
        if (player.getLevel() < requirements.minimumLevel()) {
            missing.add("Level: " + requirements.minimumLevel());
        }
        if (!requirements.world().isBlank() && !player.getWorld().getName().equalsIgnoreCase(requirements.world())) {
            missing.add("World: " + requirements.world());
        }
        var playerData = data.get(player.getUniqueId());
        for (String completed : requirements.completedQuests()) {
            var record = playerData == null ? null : playerData.quests().get(completed);
            if (record == null || record.status() != QuestStatus.COMPLETED) {
                missing.add("Complete: " + completed);
            }
        }
        return missing;
    }
    public boolean meets(Player player, Quest quest) { return missing(player, quest).isEmpty(); }
}
