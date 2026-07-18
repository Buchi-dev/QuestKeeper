package com.questkeeper.hook;

import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.service.QuestProgressService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class MythicMobsListener implements Listener {
    private final QuestManager quests;
    private final QuestProgressService progress;

    public MythicMobsListener(QuestManager quests, QuestProgressService progress) {
        this.quests = quests;
        this.progress = progress;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!(event.getKiller() instanceof Player player)) {
            return;
        }

        String mobId = event.getMobType().getInternalName();
        for (var target : quests.objectives(ObjectiveType.KILL_MYTHIC_MOB, mobId)) {
            progress.add(player, target.quest().id(), target.objective().id(), 1);
        }
    }
}
