package com.questkeeper.quest.service;

import com.questkeeper.quest.model.PlayerQuestData;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.QuestStatus;
import com.questkeeper.quest.model.RepeatType;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

public final class QuestStateService {
    private final PlayerQuestDataService data;
    private final RequirementService requirements;
    private final ZoneId zone;

    public QuestStateService(PlayerQuestDataService data, RequirementService requirements, ZoneId zone) {
        this.data = data;
        this.requirements = requirements;
        this.zone = zone;
    }

    public QuestStatus state(Player player, Quest quest) {
        PlayerQuestData playerData = data.get(player.getUniqueId());
        if (playerData == null) {
            return QuestStatus.LOCKED;
        }

        PlayerQuestData.QuestRecord record = playerData.quests().get(quest.id());
        if (record == null) {
            return requirements.meets(player, quest) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
        }

        QuestStatus status = record.status();
        if (status == QuestStatus.ACTIVE && complete(quest, record)) {
            record.status(QuestStatus.READY_TO_CLAIM);
            playerData.dirty();
            status = QuestStatus.READY_TO_CLAIM;
        }
        if (status == QuestStatus.COOLDOWN && System.currentTimeMillis() >= record.cooldownUntil()) {
            status = requirements.meets(player, quest) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
            if (status != record.status()) {
                record.status(status);
                playerData.dirty();
            }
        }
        if (status == QuestStatus.COMPLETED && quest.repeatType() != RepeatType.ONE_TIME && resetAvailable(quest, record)) {
            record.status(QuestStatus.AVAILABLE);
            playerData.dirty();
            status = QuestStatus.AVAILABLE;
        }
        if (status == QuestStatus.AVAILABLE && !requirements.meets(player, quest)) {
            record.status(QuestStatus.LOCKED);
            playerData.dirty();
            return QuestStatus.LOCKED;
        }
        if (status == QuestStatus.LOCKED && requirements.meets(player, quest)) {
            record.status(QuestStatus.AVAILABLE);
            playerData.dirty();
            return QuestStatus.AVAILABLE;
        }
        return status;
    }

    public boolean complete(Quest quest, PlayerQuestData.QuestRecord record) {
        return quest.objectives().values().stream()
                .allMatch(objective -> record.progress().getOrDefault(objective.id(), 0) >= objective.amount());
    }

    public boolean resetAvailable(Quest quest, PlayerQuestData.QuestRecord record) {
        Instant completed = Instant.ofEpochMilli(record.completedAt());
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime then = completed.atZone(zone);
        return switch (quest.repeatType()) {
            case REPEATABLE -> System.currentTimeMillis() >= record.cooldownUntil();
            case DAILY -> !then.toLocalDate().equals(now.toLocalDate());
            case WEEKLY -> then.get(WeekFields.ISO.weekOfWeekBasedYear())
                    != now.get(WeekFields.ISO.weekOfWeekBasedYear()) || then.getYear() != now.getYear();
            default -> false;
        };
    }
}
