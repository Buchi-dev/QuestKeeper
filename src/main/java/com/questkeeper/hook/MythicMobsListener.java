package com.questkeeper.hook;

import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.service.QuestProgressService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.LivingEntity;
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
        LivingEntity killer = event.getKiller();
        if (!(killer instanceof Player player)) {
            return;
        }

        String mobId = event.getMobType().getInternalName();
        for (var quest : quests.all()) {
            for (var objective : quest.objectives().values()) {
                if (objective.type() != ObjectiveType.KILL_MYTHIC_MOB) {
                    continue;
                }

                String configuredMobId = objective.settings().getOrDefault("mob-id", "").toString();
                if (configuredMobId.isBlank()) {
                    configuredMobId = objective.settings().getOrDefault("mob", "").toString();
                }

                if (configuredMobId.equalsIgnoreCase(mobId)) {
                    progress.add(player, quest.id(), objective.id(), 1);
                }
            }
        }
    }
}
