package com.questkeeper.quest.service;

import com.questkeeper.quest.model.*;
import java.time.*;
import java.time.temporal.WeekFields;

/** Pure rules used by the state service and easy to test without a server. */
public final class QuestStateRules {
    private QuestStateRules() { }
    public static QuestStatus availability(boolean requirementsMet, QuestStatus current, boolean objectivesComplete) {
        if (current == QuestStatus.ACTIVE && objectivesComplete) return QuestStatus.READY_TO_CLAIM;
        if (current == QuestStatus.LOCKED || current == QuestStatus.AVAILABLE) return requirementsMet ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
        return current;
    }
    public static boolean repeatAvailable(RepeatType type, long completedAt, long cooldownUntil, Instant now, ZoneId zone) {
        if (type == RepeatType.REPEATABLE) return now.toEpochMilli() >= cooldownUntil;
        ZonedDateTime completed = Instant.ofEpochMilli(completedAt).atZone(zone), current = now.atZone(zone);
        return switch (type) {
            case DAILY -> !completed.toLocalDate().equals(current.toLocalDate());
            case WEEKLY -> completed.get(WeekFields.ISO.weekOfWeekBasedYear()) != current.get(WeekFields.ISO.weekOfWeekBasedYear()) || completed.getYear() != current.getYear();
            default -> false;
        };
    }
    public static int cappedProgress(int current, int amount, int required) { return Math.min(required, Math.max(0, current) + Math.max(0, amount)); }
}
