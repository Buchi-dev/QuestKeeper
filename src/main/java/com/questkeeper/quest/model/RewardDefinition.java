package com.questkeeper.quest.model;

import java.util.Map;

public record RewardDefinition(RewardType type, int amount, String value, Map<String, Object> settings) {
    public RewardDefinition { settings = Map.copyOf(settings); }
}
