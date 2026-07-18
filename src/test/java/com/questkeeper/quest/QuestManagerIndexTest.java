package com.questkeeper.quest;

import com.questkeeper.quest.model.ObjectiveDefinition;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.RepeatType;
import com.questkeeper.quest.model.RequirementDefinition;
import com.questkeeper.quest.model.RewardDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestManagerIndexTest {
    @Test
    void indexesOnlyObjectivesMatchingTheEventKey() {
        Quest zombieQuest = quest("zombie_hunt", ObjectiveType.KILL_ENTITY, Map.of("entity", "ZOMBIE"));
        Quest skeletonQuest = quest("skeleton_hunt", ObjectiveType.KILL_ENTITY, Map.of("entity", "SKELETON"));
        QuestManager manager = new QuestManager();
        manager.replace(Map.of(zombieQuest.id(), zombieQuest, skeletonQuest.id(), skeletonQuest));

        assertEquals(List.of("zombie_hunt"), manager.objectives(ObjectiveType.KILL_ENTITY, "zombie")
                .stream().map(target -> target.quest().id()).toList());
        assertTrue(manager.objectives(ObjectiveType.KILL_ENTITY, "CREEPER").isEmpty());
    }

    private Quest quest(String id, ObjectiveType type, Map<String, Object> settings) {
        ObjectiveDefinition objective = new ObjectiveDefinition("objective", type, 1, "objective", settings);
        return new Quest(id, id, List.of(), "BOOK", "", "", Map.of(objective.id(), objective),
                new RequirementDefinition("", 0, List.of(), ""), List.<RewardDefinition>of(),
                RepeatType.ONE_TIME, 0, false, true, false);
    }
}
