package com.questkeeper.npc;

import net.kyori.adventure.text.minimessage.MiniMessage; import org.bukkit.*; import org.bukkit.configuration.ConfigurationSection; import org.bukkit.configuration.file.YamlConfiguration; import org.bukkit.entity.*; import org.bukkit.persistence.PersistentDataType; import org.bukkit.plugin.java.JavaPlugin; import java.io.File; import java.util.*;

public final class NpcManager {
    private final JavaPlugin plugin; private final NamespacedKey marker,idKey; private final Map<String,NpcDefinition> definitions=new LinkedHashMap<>(); private final Map<String,UUID> entities=new HashMap<>();
    public NpcManager(JavaPlugin plugin){this.plugin=plugin;marker=new NamespacedKey(plugin,"questkeeper_npc");idKey=new NamespacedKey(plugin,"npc_id");}
    public void load(){definitions.clear();File file=new File(plugin.getDataFolder(),"npcs.yml");var c=YamlConfiguration.loadConfiguration(file);ConfigurationSection section=c.getConfigurationSection("npcs");if(section==null)return;for(String id:section.getKeys(false)){try{String path="npcs."+id;String world=c.getString(path+".location.world");World w=Bukkit.getWorld(world);if(w==null)throw new IllegalArgumentException("world not found: "+world);Location l=new Location(w,c.getDouble(path+".location.x"),c.getDouble(path+".location.y"),c.getDouble(path+".location.z"),(float)c.getDouble(path+".location.yaw"),(float)c.getDouble(path+".location.pitch"));definitions.put(id,new NpcDefinition(id,c.getString(path+".display-name",id),c.getString(path+".profession","NONE"),c.getString(path+".villager-type","PLAINS"),l,c.getStringList(path+".greeting"),c.getStringList(path+".quests"),c.getBoolean(path+".silent",true)));}catch(Exception e){plugin.getLogger().warning("Failed to load NPC "+id+": "+e.getMessage());}}}
    public void spawnAll(){for(NpcDefinition definition:definitions.values())spawn(definition);}
    public void spawn(NpcDefinition definition){remove(definition.id());Villager v=(Villager)definition.location().getWorld().spawnEntity(definition.location(),EntityType.VILLAGER);v.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);v.getPersistentDataContainer().set(idKey,PersistentDataType.STRING,definition.id());v.customName(MiniMessage.miniMessage().deserialize(definition.displayName()));v.setCustomNameVisible(true);try{v.setProfession(Villager.Profession.valueOf(definition.profession().toUpperCase(Locale.ROOT)));}catch(Exception ignored){}try{v.setVillagerType(Villager.Type.valueOf(definition.villagerType().toUpperCase(Locale.ROOT)));}catch(Exception ignored){}v.setAI(false);v.setInvulnerable(true);v.setSilent(definition.silent());v.setCollidable(false);v.setCanPickupItems(false);v.setPersistent(true);v.setRemoveWhenFarAway(false);entities.put(definition.id(),v.getUniqueId());}
    public void remove(String id){UUID uuid=entities.remove(id);if(uuid==null)return;Entity e=Bukkit.getEntity(uuid);if(e!=null)e.remove();}
    public void removeAll(){new ArrayList<>(definitions.keySet()).forEach(this::remove);}
    public NpcDefinition get(String id){return definitions.get(id);}
    public Collection<NpcDefinition> all(){return definitions.values();}
    public String id(Entity entity){return entity.getPersistentDataContainer().get(idKey,PersistentDataType.STRING);}
    public NpcDefinition from(Entity entity){String id=id(entity);return id==null?null:definitions.get(id);}
    public boolean isNpc(Entity entity){return entity.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)&&id(entity)!=null;}
    public void respawnMissing(){for(NpcDefinition d:definitions.values()){Entity e=entities.containsKey(d.id())?Bukkit.getEntity(entities.get(d.id())):null;if(e==null||!e.isValid())spawn(d);}}
    public NamespacedKey markerKey(){return marker;}
}
