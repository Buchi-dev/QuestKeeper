package com.questkeeper.api.event;
import com.questkeeper.quest.model.Quest; import org.bukkit.entity.Player; import org.bukkit.event.HandlerList; import org.bukkit.event.player.PlayerEvent;
public final class PlayerQuestObjectivesCompleteEvent extends PlayerEvent { private static final HandlerList HANDLERS=new HandlerList(); private final Quest quest; public PlayerQuestObjectivesCompleteEvent(Player p,Quest q){super(p);quest=q;} public Quest getQuest(){return quest;} public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;} }
