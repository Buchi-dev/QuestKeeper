package com.questkeeper.quest.model;

import java.util.List;

public record RequirementDefinition(String permission, int minimumLevel, List<String> completedQuests, String world) {
    public RequirementDefinition { completedQuests = List.copyOf(completedQuests); }
}
