package com.questkeeper.quest.service;

import com.questkeeper.quest.model.*;
import com.questkeeper.api.event.PlayerQuestObjectivesCompleteEvent;
import com.questkeeper.api.event.PlayerQuestProgressEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

public final class QuestProgressService {
    private final PlayerQuestDataService data; private final QuestManagerView quests; private final QuestStateService states;
    public interface QuestManagerView { Quest get(String id); Collection<Quest> all(); }
    public QuestProgressService(PlayerQuestDataService data, QuestManagerView quests, QuestStateService states) { this.data = data; this.quests = quests; this.states = states; }
    public void add(Player player, String questId, String objectiveId, int amount) {
        if (amount <= 0) return;
        Quest quest = quests.get(questId);
        var playerData = data.get(player.getUniqueId());
        if (quest == null || playerData == null || states.state(player, quest) != QuestStatus.ACTIVE) return;
        var record = playerData.quests().get(questId);
        var objective = quest.objectives().get(objectiveId);
        if (record == null || objective == null) return;

        Bukkit.getPluginManager().callEvent(new PlayerQuestProgressEvent(player, quest, objectiveId, amount));
        int current = record.progress().getOrDefault(objectiveId, 0);
        record.progress().put(objectiveId, QuestStateRules.cappedProgress(current, amount, objective.amount()));
        playerData.dirty();
        if (states.complete(quest, record)) {
            record.status(QuestStatus.READY_TO_CLAIM);
            Bukkit.getPluginManager().callEvent(new PlayerQuestObjectivesCompleteEvent(player, quest));
        }
    }

    public int progress(Player player, Quest quest, String objectiveId) {
        var playerData = data.get(player.getUniqueId());
        if (playerData == null) return 0;
        var record = playerData.quests().get(quest.id());
        return record == null ? 0 : record.progress().getOrDefault(objectiveId, 0);
    }
}
