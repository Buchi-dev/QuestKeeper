package com.questkeeper.quest.service;

import com.questkeeper.quest.model.*;
import org.bukkit.entity.Player;
import java.time.*; import java.time.temporal.WeekFields;

public final class QuestStateService {
    private final PlayerQuestDataService data; private final RequirementService requirements; private final ZoneId zone;
    public QuestStateService(PlayerQuestDataService data, RequirementService requirements, ZoneId zone) { this.data = data; this.requirements = requirements; this.zone = zone; }
    public QuestStatus state(Player player, Quest quest) { var r = data.getOrCreate(player.getUniqueId()).record(quest.id()); if (r.status() == QuestStatus.ACTIVE && complete(quest, r)) r.status(QuestStatus.READY_TO_CLAIM); if (r.status() == QuestStatus.COOLDOWN && System.currentTimeMillis() >= r.cooldownUntil()) r.status(requirements.meets(player, quest) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED); if (r.status() == QuestStatus.COMPLETED && quest.repeatType() != RepeatType.ONE_TIME && resetAvailable(quest, r)) r.status(QuestStatus.AVAILABLE); if (r.status() == QuestStatus.AVAILABLE && !requirements.meets(player, quest)) return QuestStatus.LOCKED; if (r.status() == QuestStatus.LOCKED && requirements.meets(player, quest)) return QuestStatus.AVAILABLE; return r.status(); }
    public boolean complete(Quest quest, PlayerQuestData.QuestRecord r) { return quest.objectives().values().stream().allMatch(o -> r.progress().getOrDefault(o.id(), 0) >= o.amount()); }
    public boolean resetAvailable(Quest quest, PlayerQuestData.QuestRecord r) { Instant completed = Instant.ofEpochMilli(r.completedAt()); ZonedDateTime now = ZonedDateTime.now(zone); ZonedDateTime then = completed.atZone(zone); return switch (quest.repeatType()) { case REPEATABLE -> System.currentTimeMillis() >= r.cooldownUntil(); case DAILY -> !then.toLocalDate().equals(now.toLocalDate()); case WEEKLY -> then.get(WeekFields.ISO.weekOfWeekBasedYear()) != now.get(WeekFields.ISO.weekOfWeekBasedYear()) || then.getYear() != now.getYear(); default -> false; }; }
}
