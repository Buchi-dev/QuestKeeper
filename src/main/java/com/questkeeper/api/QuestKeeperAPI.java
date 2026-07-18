package com.questkeeper.api;

import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.QuestStatus;
import com.questkeeper.quest.service.PlayerQuestDataService;
import com.questkeeper.quest.service.QuestClaimService;
import com.questkeeper.quest.service.QuestProgressService;
import com.questkeeper.quest.service.QuestStateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public final class QuestKeeperAPI {
    private final QuestManager quests;
    private final PlayerQuestDataService data;
    private final QuestStateService states;
    private final QuestProgressService progress;
    private final QuestClaimService claims;

    public QuestKeeperAPI(QuestManager quests, PlayerQuestDataService data, QuestStateService states,
                          QuestProgressService progress, QuestClaimService claims) {
        this.quests = quests;
        this.data = data;
        this.states = states;
        this.progress = progress;
        this.claims = claims;
    }

    public Quest getQuest(String id) {
        return quests.get(id);
    }

    public Collection<Quest> getQuests() {
        return quests.all();
    }

    public int getProgress(Player player, String questId, String objectiveId) {
        Quest quest = quests.get(questId);
        return quest == null ? 0 : progress.progress(player, quest, objectiveId);
    }

    public QuestStatus getPlayerQuestState(UUID playerId, String questId) {
        Quest quest = quests.get(questId);
        Player player = Bukkit.getPlayer(playerId);
        return quest == null || player == null ? QuestStatus.LOCKED : states.state(player, quest);
    }

    public boolean acceptQuest(Player player, String questId) {
        return claims.accept(player, questId);
    }

    public void addProgress(Player player, String questId, String objectiveId, int amount) {
        progress.add(player, questId, objectiveId, amount);
    }

    public boolean completeQuest(Player player, String questId) {
        Quest quest = quests.get(questId);
        var playerData = data.get(player.getUniqueId());
        if (quest == null || playerData == null) return false;

        var record = playerData.record(questId);
        record.progress().putAll(quest.objectives().keySet().stream()
                .collect(Collectors.toMap(id -> id, id -> quest.objectives().get(id).amount())));
        playerData.dirty();
        data.save(playerData);
        return states.state(player, quest) == QuestStatus.READY_TO_CLAIM;
    }

    public void resetQuest(UUID playerId, String questId) {
        var playerData = data.get(playerId);
        if (playerData != null && playerData.quests().remove(questId) != null) {
            playerData.dirty();
            data.save(playerData);
        }
    }
}
