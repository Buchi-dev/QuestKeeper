package com.questkeeper.quest;

import com.questkeeper.quest.model.ObjectiveDefinition;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.service.QuestProgressService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuestManager implements QuestProgressService.QuestManagerView {
    private Map<String, Quest> quests = Map.of();
    private Map<ObjectiveType, Map<String, List<ObjectiveTarget>>> objectiveIndex = Map.of();

    public void replace(Map<String, Quest> values) {
        Map<String, Quest> orderedQuests = new LinkedHashMap<>(values);
        quests = Map.copyOf(orderedQuests);

        Map<ObjectiveType, Map<String, List<ObjectiveTarget>>> mutableIndex = new EnumMap<>(ObjectiveType.class);
        for (Quest quest : orderedQuests.values()) {
            for (ObjectiveDefinition objective : quest.objectives().values()) {
                String key = objectiveKey(objective);
                if (key == null) continue;
                mutableIndex.computeIfAbsent(objective.type(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new ObjectiveTarget(quest, objective));
            }
        }

        Map<ObjectiveType, Map<String, List<ObjectiveTarget>>> immutableIndex = new EnumMap<>(ObjectiveType.class);
        for (var typeEntry : mutableIndex.entrySet()) {
            Map<String, List<ObjectiveTarget>> entries = new LinkedHashMap<>();
            for (var keyEntry : typeEntry.getValue().entrySet()) {
                entries.put(keyEntry.getKey(), List.copyOf(keyEntry.getValue()));
            }
            immutableIndex.put(typeEntry.getKey(), Map.copyOf(entries));
        }
        objectiveIndex = Map.copyOf(immutableIndex);
    }

    public Quest get(String id) {
        return quests.get(id);
    }

    public Collection<Quest> all() {
        return quests.values();
    }

    public boolean contains(String id) {
        return quests.containsKey(id);
    }

    public Collection<ObjectiveTarget> objectives(ObjectiveType type, String key) {
        Map<String, List<ObjectiveTarget>> byKey = objectiveIndex.get(type);
        if (byKey == null) return List.of();
        String normalized = key == null || key.isBlank() ? "" : normalize(key);
        return normalized == null ? List.of() : byKey.getOrDefault(normalized, List.of());
    }

    public Collection<ObjectiveTarget> objectives(ObjectiveType type) {
        return objectives(type, "");
    }

    private String objectiveKey(ObjectiveDefinition objective) {
        return switch (objective.type()) {
            case REACH_LOCATION -> "";
            case KILL_ENTITY -> normalize(objective.settings().getOrDefault("entity", "").toString());
            case KILL_MYTHIC_MOB -> normalize(objective.settings()
                    .getOrDefault("mob-id", objective.settings().getOrDefault("mob", "")).toString());
            case COLLECT_ITEM, BREAK_BLOCK, PLACE_BLOCK, CRAFT_ITEM -> normalize(objective.settings()
                    .getOrDefault("material", "").toString());
            case INTERACT_NPC -> normalize(objective.settings()
                    .getOrDefault("npc-id", objective.settings().getOrDefault("npc", "")).toString());
            case EXECUTE_COMMAND -> normalize(objective.settings().getOrDefault("command", "")
                    .toString().replaceFirst("^/", ""));
        };
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public record ObjectiveTarget(Quest quest, ObjectiveDefinition objective) {
    }
}
