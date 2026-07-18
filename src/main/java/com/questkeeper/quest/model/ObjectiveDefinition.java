package com.questkeeper.quest.model;

import java.util.Map;

public record ObjectiveDefinition(String id, ObjectiveType type, int amount, String display, Map<String, Object> settings) {
    public ObjectiveDefinition {
        if (amount < 1) throw new IllegalArgumentException("amount must be greater than zero");
        settings = Map.copyOf(settings);
    }
}
