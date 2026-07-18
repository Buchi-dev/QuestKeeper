package com.questkeeper.quest.service;

import com.questkeeper.api.event.PlayerQuestAcceptEvent;
import com.questkeeper.api.event.PlayerQuestCancelEvent;
import com.questkeeper.api.event.PlayerQuestClaimEvent;
import com.questkeeper.api.event.PlayerQuestCompleteEvent;
import com.questkeeper.message.MessageManager;
import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.model.PlayerQuestData;
import com.questkeeper.quest.model.ObjectiveType;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.QuestStatus;
import com.questkeeper.quest.model.RewardType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestClaimService {
    private final JavaPlugin plugin;
    private final QuestManager quests;
    private final PlayerQuestDataService data;
    private final QuestStateService states;
    private final RequirementService requirements;
    private final MessageManager messages;
    private final Set<UUID> locks = ConcurrentHashMap.newKeySet();

    public QuestClaimService(JavaPlugin plugin, QuestManager quests, PlayerQuestDataService data,
                             QuestStateService states, RequirementService requirements,
                             MessageManager messages) {
        this.plugin = plugin;
        this.quests = quests;
        this.data = data;
        this.states = states;
        this.requirements = requirements;
        this.messages = messages;
    }

    public boolean accept(Player player, String id) {
        Quest quest = quests.get(id);
        PlayerQuestData playerData = data.get(player.getUniqueId());
        if (quest == null || playerData == null || states.state(player, quest) != QuestStatus.AVAILABLE) {
            return false;
        }

        long active = playerData.quests().values().stream()
                .filter(record -> record.status() == QuestStatus.ACTIVE
                        || record.status() == QuestStatus.READY_TO_CLAIM)
                .count();
        if (active >= plugin.getConfig().getInt("active-quest-limit", 3)) {
            messages.send(player, "active-limit", Map.of());
            return false;
        }

        PlayerQuestAcceptEvent event = new PlayerQuestAcceptEvent(player, quest);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        var record = playerData.record(id);
        record.status(QuestStatus.ACTIVE);
        record.acceptedAt(System.currentTimeMillis());
        playerData.dirty();
        data.save(playerData);
        messages.send(player, "quest-accepted", Map.of("quest", quest.displayName()));
        return true;
    }

    public boolean cancel(Player player, String id) {
        if (!player.hasPermission("questkeeper.cancel")) {
            messages.send(player, "no-permission", Map.of());
            return false;
        }
        Quest quest = quests.get(id);
        PlayerQuestData playerData = data.get(player.getUniqueId());
        if (quest == null || playerData == null || !quest.allowCancel()
                || states.state(player, quest) != QuestStatus.ACTIVE) {
            return false;
        }

        PlayerQuestCancelEvent event = new PlayerQuestCancelEvent(player, quest);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        playerData.quests().remove(id);
        playerData.dirty();
        data.save(playerData);
        messages.send(player, "quest-cancelled", Map.of("quest", quest.displayName()));
        return true;
    }

    public boolean claim(Player player, String id, String npcId) {
        if (!locks.add(player.getUniqueId())) return false;
        try {
            Quest quest = quests.get(id);
            PlayerQuestData playerData = data.get(player.getUniqueId());
            if (quest == null || playerData == null || !Objects.equals(quest.claimNpc(), npcId)
                    || states.state(player, quest) != QuestStatus.READY_TO_CLAIM
                    || !requirements.meets(player, quest)) {
                messages.send(player, "cannot-claim", Map.of());
                return false;
            }

            PlayerQuestClaimEvent event = new PlayerQuestClaimEvent(player, quest);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            if (!hasSpace(player, quest)) {
                messages.send(player, "inventory-full", Map.of());
                return false;
            }
            if (quest.consumeCollectedItems() && !hasCollectedItems(player, quest)) {
                messages.send(player, "cannot-claim", Map.of());
                return false;
            }

            if (quest.consumeCollectedItems()) consumeCollectedItems(player, quest);
            give(player, quest);
            var record = playerData.record(id);
            long now = System.currentTimeMillis();
            record.claimedAt(now);
            record.completedAt(now);
            record.status(quest.repeatType() == com.questkeeper.quest.model.RepeatType.ONE_TIME
                    ? QuestStatus.COMPLETED
                    : quest.repeatType() == com.questkeeper.quest.model.RepeatType.REPEATABLE
                    ? QuestStatus.COOLDOWN : QuestStatus.COMPLETED);
            record.cooldownUntil(quest.repeatType() == com.questkeeper.quest.model.RepeatType.REPEATABLE
                    ? now + quest.cooldownSeconds() * 1000L : 0);
            playerData.dirty();
            data.save(playerData);
            Bukkit.getPluginManager().callEvent(new PlayerQuestCompleteEvent(player, quest));
            messages.send(player, "quest-claimed", Map.of("quest", quest.displayName()));
            return true;
        } finally {
            long cooldownTicks = Math.max(1,
                    plugin.getConfig().getLong("gui-click-cooldown-millis", 250) / 50 + 1);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> locks.remove(player.getUniqueId()), cooldownTicks);
        }
    }

    private boolean hasSpace(Player player, Quest quest) {
        Inventory inventory = player.getInventory();
        for (var reward : quest.rewards()) {
            if (reward.type() != RewardType.ITEM) continue;
            Material material = Material.matchMaterial(reward.value());
            if (material == null) continue;
            ItemStack stack = new ItemStack(material, reward.amount());
            if (inventory.firstEmpty() < 0 && !inventory.containsAtLeast(stack, reward.amount())) return false;
        }
        return true;
    }

    private boolean hasCollectedItems(Player player, Quest quest) {
        Map<Material, Integer> required = collectedItems(quest);
        for (var entry : required.entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    private void consumeCollectedItems(Player player, Quest quest) {
        for (var entry : collectedItems(quest).entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private Map<Material, Integer> collectedItems(Quest quest) {
        Map<Material, Integer> required = new HashMap<>();
        for (var objective : quest.objectives().values()) {
            if (objective.type() != ObjectiveType.COLLECT_ITEM) continue;
            Material material = Material.matchMaterial(objective.settings().getOrDefault("material", "").toString());
            if (material != null) required.merge(material, objective.amount(), Integer::sum);
        }
        return required;
    }

    private void give(Player player, Quest quest) {
        for (var reward : quest.rewards()) {
            switch (reward.type()) {
                case EXPERIENCE -> player.giveExp(reward.amount());
                case COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.value()
                        .replace("%player%", player.getName())
                        .replace("%player_uuid%", player.getUniqueId().toString())
                        .replace("%quest_id%", quest.id()));
                case ITEM -> giveItem(player, reward);
                case MONEY, PERMISSION -> plugin.getLogger().warning(
                        "Reward " + reward.type() + " requires an optional integration or command configuration.");
            }
        }
    }

    private void giveItem(Player player, com.questkeeper.quest.model.RewardDefinition reward) {
        Material material = Material.matchMaterial(reward.value());
        if (material == null) return;
        ItemStack item = new ItemStack(material, reward.amount());
        ItemMeta meta = item.getItemMeta();
        String name = (String) reward.settings().get("name");
        if (name != null) {
            meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        }
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
    }
}
