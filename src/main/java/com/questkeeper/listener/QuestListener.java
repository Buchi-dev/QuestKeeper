package com.questkeeper.listener;

import com.questkeeper.gui.DetailQuestHolder;
import com.questkeeper.gui.JournalHolder;
import com.questkeeper.gui.MainQuestHolder;
import com.questkeeper.gui.QuestCatalogHolder;
import com.questkeeper.gui.QuestChainHolder;
import com.questkeeper.gui.QuestCatalogFilter;
import com.questkeeper.gui.GuiManager;
import com.questkeeper.message.MessageManager;
import com.questkeeper.npc.NpcManager;
import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.QuestStatus;
import com.questkeeper.quest.service.PlayerQuestDataService;
import com.questkeeper.quest.service.QuestClaimService;
import com.questkeeper.quest.service.QuestProgressService;
import com.questkeeper.quest.service.QuestStateService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestListener implements Listener {
    private final JavaPlugin plugin;
    private final NpcManager npcs;
    private final QuestManager quests;
    private final GuiManager guis;
    private final QuestProgressService progress;
    private final QuestClaimService claims;
    private final PlayerQuestDataService data;
    private final QuestStateService states;
    private final MessageManager messages;
    private final Set<String> placedBlocks = Collections.synchronizedSet(new HashSet<>());

    public QuestListener(JavaPlugin plugin, NpcManager npcs, QuestManager quests, GuiManager guis,
                         QuestProgressService progress, QuestClaimService claims, PlayerQuestDataService data,
                         QuestStateService states, MessageManager messages) {
        this.plugin = plugin;
        this.npcs = npcs;
        this.quests = quests;
        this.guis = guis;
        this.progress = progress;
        this.claims = claims;
        this.data = data;
        this.states = states;
        this.messages = messages;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        data.load(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        data.saveAndRemove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void worldLoad(WorldLoadEvent event) {
        npcs.removeStale(event.getWorld());
    }

    @EventHandler
    public void npcInteract(PlayerInteractEntityEvent event) {
        if (!npcs.isNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        var npc = npcs.from(event.getRightClicked());
        if (npc == null) {
            event.getRightClicked().remove();
            return;
        }
        for (String line : npc.greeting()) player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.INTERACT_NPC
                    && objective.settings().getOrDefault("npc-id", "").toString().equalsIgnoreCase(npc.id())) {
                progress.add(player, quest.id(), objective.id(), 1);
            }
        }
        guis.openNpc(player, npc.id());
    }

    @EventHandler
    public void protect(EntityDamageEvent event) {
        if (npcs.isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void protectTarget(EntityTargetEvent event) {
        if (npcs.isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof QuestCatalogHolder)
                && !(holder instanceof QuestChainHolder)
                && !(holder instanceof DetailQuestHolder)
                && !(holder instanceof MainQuestHolder)
                && !(holder instanceof JournalHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0) return;

        if (holder instanceof QuestCatalogHolder catalog) {
            int slot = event.getRawSlot();
            if (slot == 48) {
                player.closeInventory();
            } else if (slot == 45 && catalog.page > 0) {
                guis.openCatalog(player, catalog.npcId, catalog.page - 1, catalog.filter);
            } else if (slot == 53) {
                guis.openCatalog(player, catalog.npcId, catalog.page + 1, catalog.filter);
            } else if (slot == 46 || slot == 47 || slot == 50 || slot == 51 || slot == 49) {
                QuestCatalogFilter filter = slot == 46 ? QuestCatalogFilter.ALL
                        : slot == 47 ? QuestCatalogFilter.ACTIVE
                        : slot == 50 ? QuestCatalogFilter.AVAILABLE
                        : slot == 51 ? QuestCatalogFilter.COMPLETED
                        : catalog.filter;
                guis.openCatalog(player, catalog.npcId, 0, filter);
            } else {
                String chainId = catalog.chains.get(slot);
                if (chainId != null) guis.openChain(player, catalog.npcId, chainId);
            }
        } else if (holder instanceof QuestChainHolder chain) {
            int slot = event.getRawSlot();
            if (slot == 26) {
                player.closeInventory();
            } else if (slot == 18) {
                guis.openCatalog(player, chain.npcId, 0, QuestCatalogFilter.ALL);
            } else if (slot == 22) {
                guis.openChain(player, chain.npcId, chain.chainId);
            } else {
                String questId = chain.quests.get(slot);
                if (questId != null) guis.openDetails(player, questId, chain.npcId, chain.chainId);
            }
        } else if (holder instanceof DetailQuestHolder detail) {
            Quest quest = quests.get(detail.questId);
            if (quest == null) return;
            if (event.getRawSlot() == 18) {
                if (detail.chainId == null || detail.chainId.isBlank()) guis.openCatalog(player, detail.npcId, 0, QuestCatalogFilter.ALL);
                else guis.openChain(player, detail.npcId, detail.chainId);
                return;
            }
            QuestStatus status = states.state(player, quest);
            if (event.getRawSlot() == 22) {
                if (status == QuestStatus.AVAILABLE) claims.accept(player, quest.id());
                else if (status == QuestStatus.READY_TO_CLAIM) claims.claim(player, quest.id(), detail.npcId.isBlank() ? quest.claimNpc() : detail.npcId);
                else if (status == QuestStatus.ACTIVE) claims.cancel(player, quest.id());
                guis.openDetails(player, quest.id(), detail.npcId, detail.chainId);
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof QuestCatalogHolder || holder instanceof QuestChainHolder
                || holder instanceof DetailQuestHolder || holder instanceof MainQuestHolder || holder instanceof JournalHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void kill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.KILL_ENTITY
                    && objective.settings().getOrDefault("entity", "").toString().equalsIgnoreCase(event.getEntityType().name())) {
                progress.add(player, quest.id(), objective.id(), 1);
            }
        }
    }

    @EventHandler
    public void blockPlace(BlockPlaceEvent event) {
        placedBlocks.add(key(event.getBlock().getLocation()));
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.PLACE_BLOCK
                    && objective.settings().getOrDefault("material", "").toString().equalsIgnoreCase(event.getBlock().getType().name())) {
                progress.add(event.getPlayer(), quest.id(), objective.id(), 1);
            }
        }
    }

    @EventHandler
    public void blockBreak(BlockBreakEvent event) {
        String locationKey = key(event.getBlock().getLocation());
        boolean placed = placedBlocks.remove(locationKey);
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.BREAK_BLOCK
                    && objective.settings().getOrDefault("material", "").toString().equalsIgnoreCase(event.getBlock().getType().name())
                    && (!placed || Boolean.TRUE.equals(objective.settings().get("count-player-placed-blocks")))) {
                progress.add(event.getPlayer(), quest.id(), objective.id(), 1);
            }
        }
    }

    @EventHandler
    public void craft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int amount = event.isShiftClick() ? event.getRecipe().getResult().getMaxStackSize() : event.getCurrentItem().getAmount();
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.CRAFT_ITEM
                    && objective.settings().getOrDefault("material", "").toString().equalsIgnoreCase(event.getRecipe().getResult().getType().name())) {
                progress.add(player, quest.id(), objective.id(), amount);
            }
        }
    }

    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.COLLECT_ITEM
                    && objective.settings().getOrDefault("material", "").toString().equalsIgnoreCase(stack.getType().name())) {
                progress.add(player, quest.id(), objective.id(), stack.getAmount());
            }
        }
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() == ObjectiveType.EXECUTE_COMMAND
                    && objective.settings().getOrDefault("command", "").toString().replace("/", "").equalsIgnoreCase(command)) {
                progress.add(event.getPlayer(), quest.id(), objective.id(), 1);
            }
        }
    }

    @EventHandler
    public void reach(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) < 1.0) return;
        Player player = event.getPlayer();
        for (Quest quest : quests.all()) for (var objective : quest.objectives().values()) {
            if (objective.type() != ObjectiveType.REACH_LOCATION) continue;
            String world = objective.settings().getOrDefault("world", "").toString();
            if (!world.isBlank() && !player.getWorld().getName().equalsIgnoreCase(world)) continue;
            double x = number(objective.settings().get("x"));
            double y = number(objective.settings().get("y"));
            double z = number(objective.settings().get("z"));
            double radius = number(objective.settings().getOrDefault("radius", 2));
            if (player.getLocation().distanceSquared(new Location(player.getWorld(), x, y, z)) <= radius * radius) {
                progress.add(player, quest.id(), objective.id(), objective.amount());
            }
        }
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
