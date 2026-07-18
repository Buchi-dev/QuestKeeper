package com.questkeeper.listener;

import com.questkeeper.gui.DetailQuestHolder;
import com.questkeeper.gui.QuestCatalogHolder;
import com.questkeeper.gui.QuestChainHolder;
import com.questkeeper.gui.QuestCatalogFilter;
import com.questkeeper.gui.GuiManager;
import com.questkeeper.api.event.PlayerQuestObjectivesCompleteEvent;
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
import org.bukkit.Location;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class QuestListener implements Listener {
    private static final long PLACED_BLOCK_RETENTION_MILLIS = 60 * 60 * 1000L;
    private final NpcManager npcs;
    private final QuestManager quests;
    private final GuiManager guis;
    private final QuestProgressService progress;
    private final QuestClaimService claims;
    private final PlayerQuestDataService data;
    private final QuestStateService states;
    private final long reachCheckIntervalMillis;
    private final double reachMovementThreshold;
    private final MessageManager messages;
    private final Map<String, Long> placedBlocks = new HashMap<>();
    private final Map<UUID, Long> lastReachCheck = new HashMap<>();

    public QuestListener(JavaPlugin plugin, NpcManager npcs, QuestManager quests, GuiManager guis,
                         QuestProgressService progress, QuestClaimService claims, PlayerQuestDataService data,
                         QuestStateService states, MessageManager messages) {
        this.npcs = npcs;
        this.quests = quests;
        this.guis = guis;
        this.progress = progress;
        this.claims = claims;
        this.data = data;
        this.states = states;
        this.messages = messages;
        this.reachCheckIntervalMillis = Math.max(0L,
                plugin.getConfig().getLong("reach-check-interval-ticks", 20L) * 50L);
        this.reachMovementThreshold = Math.max(0.0,
                plugin.getConfig().getDouble("reach-movement-threshold", 1.0));
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        data.load(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastReachCheck.remove(playerId);
        data.saveAndRemove(playerId);
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
        for (var target : quests.objectives(ObjectiveType.INTERACT_NPC, npc.id())) {
            progress.add(player, target.quest().id(), target.objective().id(), 1);
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
                ) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0) return;
        if (event.getRawSlot() >= event.getInventory().getSize()
                || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;

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
                || holder instanceof DetailQuestHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void autoClaim(PlayerQuestObjectivesCompleteEvent event) {
        Quest quest = event.getQuest();
        if (quest.autoClaim()) {
            claims.claim(event.getPlayer(), quest.id(), quest.claimNpc());
        } else {
            messages.send(event.getPlayer(), "quest-completed", Map.of("quest", quest.displayName()));
        }
    }

    @EventHandler
    public void kill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        for (var target : quests.objectives(ObjectiveType.KILL_ENTITY, event.getEntityType().name())) {
            progress.add(player, target.quest().id(), target.objective().id(), 1);
        }
    }

    @EventHandler
    public void blockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        prunePlacedBlocks();
        placedBlocks.put(key(event.getBlock().getLocation()), System.currentTimeMillis());
        for (var target : quests.objectives(ObjectiveType.PLACE_BLOCK, event.getBlock().getType().name())) {
            progress.add(event.getPlayer(), target.quest().id(), target.objective().id(), 1);
        }
    }

    @EventHandler
    public void blockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        prunePlacedBlocks();
        String locationKey = key(event.getBlock().getLocation());
        boolean placed = placedBlocks.remove(locationKey) != null;
        for (var target : quests.objectives(ObjectiveType.BREAK_BLOCK, event.getBlock().getType().name())) {
            if (!placed || Boolean.TRUE.equals(target.objective().settings().get("count-player-placed-blocks"))) {
                progress.add(event.getPlayer(), target.quest().id(), target.objective().id(), 1);
            }
        }
    }

    @EventHandler
    public void craft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.isCancelled()) return;
        int amount = event.getRecipe().getResult().getAmount();
        for (var target : quests.objectives(ObjectiveType.CRAFT_ITEM, event.getRecipe().getResult().getType().name())) {
            progress.add(player, target.quest().id(), target.objective().id(), amount);
        }
    }

    @EventHandler
    public void pickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.isCancelled()) return;
        ItemStack stack = event.getItem().getItemStack();
        for (var target : quests.objectives(ObjectiveType.COLLECT_ITEM, stack.getType().name())) {
            progress.add(player, target.quest().id(), target.objective().id(), stack.getAmount());
        }
    }

    @EventHandler
    public void command(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        for (var target : quests.objectives(ObjectiveType.EXECUTE_COMMAND, command)) {
            progress.add(event.getPlayer(), target.quest().id(), target.objective().id(), 1);
        }
    }

    @EventHandler
    public void reach(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        if (event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().distanceSquared(event.getTo()) < reachMovementThreshold * reachMovementThreshold) return;
        long now = System.currentTimeMillis();
        long last = lastReachCheck.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < reachCheckIntervalMillis) return;
        lastReachCheck.put(player.getUniqueId(), now);

        Location location = player.getLocation();
        for (var target : quests.objectives(ObjectiveType.REACH_LOCATION)) {
            var objective = target.objective();
            String world = objective.settings().getOrDefault("world", "").toString();
            if (!world.isBlank() && !player.getWorld().getName().equalsIgnoreCase(world)) continue;
            double x = number(objective.settings().get("x"));
            double y = number(objective.settings().get("y"));
            double z = number(objective.settings().get("z"));
            double radius = number(objective.settings().getOrDefault("radius", 2));
            double dx = location.getX() - x;
            double dy = location.getY() - y;
            double dz = location.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                progress.add(player, target.quest().id(), objective.id(), objective.amount());
            }
        }
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void prunePlacedBlocks() {
        long cutoff = System.currentTimeMillis() - PLACED_BLOCK_RETENTION_MILLIS;
        placedBlocks.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
