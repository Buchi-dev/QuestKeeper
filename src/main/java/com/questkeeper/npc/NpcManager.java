package com.questkeeper.npc;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NpcManager {
    private final JavaPlugin plugin;
    private final NamespacedKey marker;
    private final NamespacedKey idKey;
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, UUID> entities = new HashMap<>();

    public NpcManager(JavaPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "questkeeper_npc");
        idKey = new NamespacedKey(plugin, "npc_id");
    }

    public void load() {
        definitions.clear();
        File file = new File(plugin.getDataFolder(), "npcs.yml");
        var configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("npcs");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            try {
                String path = "npcs." + id;
                String worldName = configuration.getString(path + ".location.world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) throw new IllegalArgumentException("world not found: " + worldName);
                Location location = new Location(
                        world,
                        configuration.getDouble(path + ".location.x"),
                        configuration.getDouble(path + ".location.y"),
                        configuration.getDouble(path + ".location.z"),
                        (float) configuration.getDouble(path + ".location.yaw"),
                        (float) configuration.getDouble(path + ".location.pitch")
                );
                definitions.put(id, new NpcDefinition(
                        id,
                        configuration.getString(path + ".display-name", id),
                        configuration.getString(path + ".profession", "NONE"),
                        configuration.getString(path + ".villager-type", "PLAINS"),
                        location,
                        configuration.getStringList(path + ".greeting"),
                        configuration.getStringList(path + ".quests"),
                        configuration.getBoolean(path + ".silent", true)
                ));
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to load NPC " + id + ": " + exception.getMessage());
            }
        }
    }

    public void spawnAll() {
        for (NpcDefinition definition : definitions.values()) spawn(definition);
    }

    public void spawn(NpcDefinition definition) {
        remove(definition.id());
        Villager villager = (Villager) definition.location().getWorld().spawnEntity(definition.location(), EntityType.VILLAGER);
        villager.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        villager.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, definition.id());
        villager.customName(MiniMessage.miniMessage().deserialize(definition.displayName()));
        villager.setCustomNameVisible(true);
        try {
            Villager.Profession profession = professionValue(definition.profession());
            if (profession != null) villager.setProfession(profession);
        } catch (Exception ignored) {
        }
        try {
            Villager.Type type = villagerTypeValue(definition.villagerType());
            if (type != null) villager.setVillagerType(type);
        } catch (Exception ignored) {
        }
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(definition.silent());
        villager.setCollidable(false);
        villager.setCanPickupItems(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        entities.put(definition.id(), villager.getUniqueId());
    }

    public void remove(String id) {
        UUID uuid = entities.remove(id);
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isNpc(entity)) continue;
                String storedId = id(entity);
                if (storedId != null && storedId.equalsIgnoreCase(id)) entity.remove();
            }
        }
    }

    /** Removes all current and stale QuestKeeper NPC entities, independent of the current config. */
    public void removeAll() {
        for (UUID uuid : new ArrayList<>(entities.values())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        entities.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isNpc(entity)) entity.remove();
            }
        }
    }

    /** Removes marked NPCs whose IDs are no longer present in the loaded configuration. */
    public void removeStale(World world) {
        for (Entity entity : world.getEntities()) {
            if (!isNpc(entity)) continue;
            String storedId = id(entity);
            if (storedId == null || !definitions.containsKey(storedId)) entity.remove();
        }
    }

    public NpcDefinition get(String id) {
        return definitions.get(id);
    }

    public Collection<NpcDefinition> all() {
        return definitions.values();
    }

    public String id(Entity entity) {
        return entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public NpcDefinition from(Entity entity) {
        String id = id(entity);
        return id == null ? null : definitions.get(id);
    }

    public boolean isNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(marker, PersistentDataType.BYTE) && id(entity) != null;
    }

    public void respawnMissing() {
        for (NpcDefinition definition : definitions.values()) {
            Entity entity = entities.containsKey(definition.id()) ? Bukkit.getEntity(entities.get(definition.id())) : null;
            if (entity == null || !entity.isValid()) spawn(definition);
        }
    }

    public NamespacedKey markerKey() {
        return marker;
    }

    private Villager.Profession professionValue(String value) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT)));
    }

    private Villager.Type villagerTypeValue(String value) {
        return Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT)));
    }
}
