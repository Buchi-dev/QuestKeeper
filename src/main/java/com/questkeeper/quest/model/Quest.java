package com.questkeeper.quest.model;

import java.util.List;
import java.util.Map;

public record Quest(String id, String displayName, List<String> description, String iconMaterial, String startNpc,
                   String claimNpc, Map<String, ObjectiveDefinition> objectives, RequirementDefinition requirements,
                   List<RewardDefinition> rewards, RepeatType repeatType, long cooldownSeconds, boolean autoClaim,
                   boolean allowCancel, boolean consumeCollectedItems) {
    public Quest {
        description = List.copyOf(description); objectives = Map.copyOf(objectives); rewards = List.copyOf(rewards);
    }
}
