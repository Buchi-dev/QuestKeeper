package com.questkeeper.quest.service;

import com.questkeeper.quest.model.*;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public final class RequirementService {
    private final PlayerQuestDataService data;
    public RequirementService(PlayerQuestDataService data) { this.data = data; }
    public List<String> missing(Player player, Quest quest) { List<String> missing = new ArrayList<>(); RequirementDefinition r = quest.requirements(); if (!r.permission().isBlank() && !player.hasPermission(r.permission())) missing.add("Permission: " + r.permission()); if (player.getLevel() < r.minimumLevel()) missing.add("Level: " + r.minimumLevel()); if (!r.world().isBlank() && !player.getWorld().getName().equalsIgnoreCase(r.world())) missing.add("World: " + r.world()); for (String completed : r.completedQuests()) { var record = data.getOrCreate(player.getUniqueId()).record(completed); if (record.status() != QuestStatus.COMPLETED) missing.add("Complete: " + completed); } return missing; }
    public boolean meets(Player player, Quest quest) { return missing(player, quest).isEmpty(); }
}
