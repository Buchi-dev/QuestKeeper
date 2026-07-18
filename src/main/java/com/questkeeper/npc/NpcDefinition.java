package com.questkeeper.npc;
import java.util.List; import org.bukkit.Location;
public record NpcDefinition(String id,String displayName,String profession,String villagerType,Location location,List<String> greeting,List<String> quests,boolean silent){public NpcDefinition{greeting=List.copyOf(greeting);quests=List.copyOf(quests);}}
