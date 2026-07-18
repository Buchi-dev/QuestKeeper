package com.questkeeper.api;

import com.questkeeper.quest.model.*;
import org.bukkit.entity.Player;
import java.util.UUID;

public final class QuestKeeperAPI {
    private final com.questkeeper.quest.QuestManager quests; private final com.questkeeper.quest.service.PlayerQuestDataService data; private final com.questkeeper.quest.service.QuestStateService states; private final com.questkeeper.quest.service.QuestProgressService progress; private final com.questkeeper.quest.service.QuestClaimService claims;
    public QuestKeeperAPI(com.questkeeper.quest.QuestManager quests, com.questkeeper.quest.service.PlayerQuestDataService data, com.questkeeper.quest.service.QuestStateService states, com.questkeeper.quest.service.QuestProgressService progress, com.questkeeper.quest.service.QuestClaimService claims) { this.quests=quests; this.data=data; this.states=states; this.progress=progress; this.claims=claims; }
    public Quest getQuest(String id) { return quests.get(id); }
    public java.util.Collection<Quest> getQuests() { return quests.all(); }
    public int getProgress(Player player, String questId, String objectiveId) { Quest quest = quests.get(questId); return quest == null ? 0 : progress.progress(player, quest, objectiveId); }
    public QuestStatus getPlayerQuestState(UUID playerId, String questId) { Quest q=quests.get(questId); Player p=org.bukkit.Bukkit.getPlayer(playerId); return q == null || p == null ? QuestStatus.LOCKED : states.state(p,q); }
    public boolean acceptQuest(Player player, String questId) { return claims.accept(player, questId); }
    public void addProgress(Player player, String questId, String objectiveId, int amount) { progress.add(player,questId,objectiveId,amount); }
    public boolean completeQuest(Player player, String questId) { Quest q=quests.get(questId); if(q==null) return false; var r=data.getOrCreate(player.getUniqueId()).record(questId); r.progress().putAll(q.objectives().keySet().stream().collect(java.util.stream.Collectors.toMap(k->k,k->q.objectives().get(k).amount()))); data.getOrCreate(player.getUniqueId()).dirty(); return states.state(player,q)==QuestStatus.READY_TO_CLAIM; }
    public void resetQuest(UUID playerId, String questId) { var d=data.getOrCreate(playerId); d.quests().remove(questId); d.dirty(); }
}
