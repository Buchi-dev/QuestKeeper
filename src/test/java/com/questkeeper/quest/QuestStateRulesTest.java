package com.questkeeper.quest;

import com.questkeeper.quest.model.*;
import com.questkeeper.quest.service.QuestStateRules;
import com.questkeeper.utility.IdValidator;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestStateRulesTest {
    @Test void activeQuestBecomesReadyOnlyWhenObjectivesAreComplete() {
        assertEquals(QuestStatus.READY_TO_CLAIM, QuestStateRules.availability(true, QuestStatus.ACTIVE, true));
        assertEquals(QuestStatus.ACTIVE, QuestStateRules.availability(true, QuestStatus.ACTIVE, false));
    }
    @Test void lockedAndAvailableDependOnRequirements() {
        assertEquals(QuestStatus.LOCKED, QuestStateRules.availability(false, QuestStatus.AVAILABLE, false));
        assertEquals(QuestStatus.AVAILABLE, QuestStateRules.availability(true, QuestStatus.LOCKED, false));
    }
    @Test void cooldownAndCalendarResetsAreCalculatedCorrectly() {
        Instant completed = Instant.parse("2026-07-17T23:00:00Z");
        assertFalse(QuestStateRules.repeatAvailable(RepeatType.REPEATABLE, 0, completed.plusSeconds(60).toEpochMilli(), completed, ZoneId.of("UTC")));
        assertTrue(QuestStateRules.repeatAvailable(RepeatType.DAILY, completed.toEpochMilli(), 0, Instant.parse("2026-07-18T00:00:01Z"), ZoneId.of("UTC")));
        assertTrue(QuestStateRules.repeatAvailable(RepeatType.WEEKLY, completed.toEpochMilli(), 0, Instant.parse("2026-07-27T00:00:01Z"), ZoneId.of("UTC")));
    }
    @Test void progressNeverExceedsRequiredAmount() { assertEquals(10, QuestStateRules.cappedProgress(9, 99, 10)); assertEquals(0, QuestStateRules.cappedProgress(0, -4, 10)); }
    @Test void idsRejectPathsSpacesAndInvalidCharacters() { assertTrue(IdValidator.isValid("savanna_hunter_1")); assertFalse(IdValidator.isValid("../escape")); assertFalse(IdValidator.isValid("has space")); assertFalse(IdValidator.isValid("ab")); }
}
