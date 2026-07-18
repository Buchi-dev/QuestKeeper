package com.questkeeper.gui;

import com.questkeeper.message.MessageManager;
import com.questkeeper.npc.NpcManager;
import com.questkeeper.quest.QuestManager;
import com.questkeeper.quest.model.ObjectiveDefinition;
import com.questkeeper.quest.model.Quest;
import com.questkeeper.quest.model.QuestStatus;
import com.questkeeper.quest.service.QuestProgressService;
import com.questkeeper.quest.service.QuestStateService;
import com.questkeeper.quest.service.RequirementService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GuiManager {
    private static final int PAGE_SIZE = 36;
    private static final int[] CHAIN_SLOTS = {10, 11, 12, 13, 14};

    private final JavaPlugin plugin;
    private final QuestManager quests;
    private final NpcManager npcs;
    private final QuestStateService states;
    private final QuestProgressService progress;
    private final RequirementService requirements;
    private final MessageManager messages;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private FileConfiguration guiConfig;

    public GuiManager(JavaPlugin plugin, QuestManager quests, NpcManager npcs, QuestStateService states,
                      QuestProgressService progress, RequirementService requirements, MessageManager messages) {
        this.plugin = plugin;
        this.quests = quests;
        this.npcs = npcs;
        this.states = states;
        this.progress = progress;
        this.requirements = requirements;
        this.messages = messages;
        reload();
    }

    public void reload() {
        guiConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "guis.yml"));
    }

    public void openNpc(Player player, String npcId) {
        openCatalog(player, npcId, 0, QuestCatalogFilter.ALL);
    }

    public void openJournal(Player player) {
        openCatalog(player, "", 0, QuestCatalogFilter.ALL);
    }

    public void openCatalog(Player player, String npcId, int page, QuestCatalogFilter filter) {
        List<Chain> chains = chainsFor(player, npcId, filter);
        int maxPage = Math.max(0, (chains.size() - 1) / PAGE_SIZE);
        int safePage = Math.min(Math.max(page, 0), maxPage);
        String source = npcId.isBlank()
                ? "<reset>" + guiString("titles.journal", "<gold>Your Quest Journal")
                : "<reset>" + guiString("titles.npc", "<gold>Quests: %npc%").replace("%npc%", npcName(npcId));
        QuestCatalogHolder holder = new QuestCatalogHolder(npcId, safePage, filter);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.component("<gold>" + source + " <dark_gray>• <yellow>" + filterLabel(filter), Map.of()));
        holder.inventory(inventory);
        int questCount = npcId.isBlank() ? quests.all().size() : npcs.get(npcId).quests().size();

        inventory.setItem(4, item(Material.COMPASS, "<gold>Dungeon Quest Catalog", List.of(
                "<gray>" + chains.size() + " mob chains • " + questCount + " quest levels",
                "<gray>Select a mob to view its five-level progression.",
                "<yellow>Filter: <white>" + filterLabel(filter)
        )));

        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < chains.size(); i++) {
            Chain chain = chains.get(start + i);
            int slot = 9 + i;
            holder.chains.put(slot, chain.id());
            inventory.setItem(slot, chainItem(player, chain));
        }

        inventory.setItem(45, item(guiMaterial("items.previous", Material.ARROW), "<yellow>Previous Page", List.of(
                safePage == 0 ? "<gray>This is the first page." : "<gray>Go to page " + safePage
        )));
        inventory.setItem(46, filterItem(QuestCatalogFilter.ALL, filter));
        inventory.setItem(47, filterItem(QuestCatalogFilter.ACTIVE, filter));
        inventory.setItem(48, item(guiMaterial("items.close", Material.BARRIER), "<red>Close", List.of()));
        inventory.setItem(49, item(guiMaterial("items.refresh", Material.NETHER_STAR), "<aqua>Refresh", List.of(
                "<gray>Reload the current quest catalog."
        )));
        inventory.setItem(50, filterItem(QuestCatalogFilter.AVAILABLE, filter));
        inventory.setItem(51, filterItem(QuestCatalogFilter.COMPLETED, filter));
        inventory.setItem(53, item(guiMaterial("items.next", Material.ARROW), "<yellow>Next Page", List.of(
                safePage >= maxPage ? "<gray>This is the last page." : "<gray>Go to page " + (safePage + 2)
        )));
        player.openInventory(inventory);
    }

    public void openChain(Player player, String npcId, String chainId) {
        List<Chain> chains = chainsFor(player, npcId, QuestCatalogFilter.ALL);
        Chain chain = chains.stream().filter(value -> value.id().equals(chainId)).findFirst().orElse(null);
        if (chain == null) return;

        QuestChainHolder holder = new QuestChainHolder(npcId, chainId);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.component("<gold>" + chainLabel(chain) + " <dark_gray>• <yellow>Levels", Map.of()));
        holder.inventory(inventory);
        inventory.setItem(4, item(Material.COMPASS, "<gold>" + chainLabel(chain), List.of(
                "<gray>Complete each level in order.",
                "<yellow>Progress: <white>" + completedLevels(player, chain) + "/" + chain.quests().size() + " levels completed"
        )));

        List<Quest> levels = chain.quests();
        for (int i = 0; i < levels.size() && i < CHAIN_SLOTS.length; i++) {
            Quest quest = levels.get(i);
            int slot = CHAIN_SLOTS[i];
            holder.quests.put(slot, quest.id());
            inventory.setItem(slot, levelItem(player, quest, i + 1));
        }
        inventory.setItem(18, item(guiMaterial("items.back", Material.OAK_DOOR), "<yellow>Back to Mob List", List.of()));
        inventory.setItem(22, item(guiMaterial("items.refresh", Material.NETHER_STAR), "<aqua>Refresh", List.of("<gray>Refresh level statuses.")));
        inventory.setItem(26, item(guiMaterial("items.close", Material.BARRIER), "<red>Close", List.of()));
        player.openInventory(inventory);
    }

    public void openDetails(Player player, String questId, String npcId, String chainId) {
        Quest quest = quests.get(questId);
        if (quest == null) return;
        DetailQuestHolder holder = new DetailQuestHolder(questId, npcId, chainId);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.component(guiString("titles.details", "<gold>%quest%"), Map.of("quest", quest.displayName())));
        holder.inventory(inventory);
        inventory.setItem(4, item(Material.matchMaterial(quest.iconMaterial()), quest.displayName(), quest.description()));

        List<String> objectiveLore = new ArrayList<>();
        for (ObjectiveDefinition objective : quest.objectives().values()) {
            objectiveLore.add(mini.serialize(component(objective.display(), Map.of(
                    "progress", String.valueOf(progress.progress(player, quest, objective.id())),
                    "required", String.valueOf(objective.amount())
            ))));
        }
        inventory.setItem(10, item(Material.WRITABLE_BOOK, "<yellow>Objectives", objectiveLore));
        List<String> missing = new ArrayList<>(requirements.missing(player, quest));
        if (missing.isEmpty()) missing.add("<green>All requirements met");
        inventory.setItem(12, item(Material.PAPER, "<yellow>Requirements", missing));
        inventory.setItem(14, item(Material.CHEST, "<yellow>Rewards", quest.rewards().stream()
                .map(reward -> "<white>" + reward.type() + " " + reward.amount()).toList()));

        QuestStatus status = states.state(player, quest);
        inventory.setItem(16, item(materialFor(status), "<yellow>Status: <white>" + status, List.of(
                "<gray>Level " + levelOf(quest.id()) + " of this mob chain"
        )));
        if (status == QuestStatus.AVAILABLE) inventory.setItem(22, item(guiMaterial("items.accept", Material.LIME_DYE), "<green>Accept Quest", List.of()));
        if (status == QuestStatus.READY_TO_CLAIM) inventory.setItem(22, item(guiMaterial("items.claim", Material.CHEST), "<green>Claim Reward", List.of()));
        if (status == QuestStatus.ACTIVE && quest.allowCancel()) inventory.setItem(22, item(guiMaterial("items.cancel", Material.RED_DYE), "<red>Cancel Quest", List.of()));
        inventory.setItem(18, item(guiMaterial("items.back", Material.OAK_DOOR), "<yellow>Back to Levels", List.of()));
        player.openInventory(inventory);
    }

    private List<Chain> chainsFor(Player player, String npcId, QuestCatalogFilter filter) {
        Collection<Quest> source;
        if (npcId.isBlank()) {
            source = quests.all();
        } else {
            var npc = npcs.get(npcId);
            if (npc == null) return List.of();
            source = npc.quests().stream().map(quests::get).filter(Objects::nonNull).toList();
        }

        Map<String, List<Quest>> grouped = new LinkedHashMap<>();
        for (Quest quest : source) grouped.computeIfAbsent(chainId(quest.id()), ignored -> new ArrayList<>()).add(quest);
        List<Chain> chains = grouped.entrySet().stream()
                .map(entry -> new Chain(entry.getKey(), entry.getValue().stream().sorted(Comparator.comparingInt(q -> levelOf(q.id()))).toList()))
                .filter(chain -> matchesFilter(player, chain, filter))
                .sorted(Comparator.comparing(this::chainLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return chains;
    }

    private boolean matchesFilter(Player player, Chain chain, QuestCatalogFilter filter) {
        if (filter == QuestCatalogFilter.ALL) return true;
        if (filter == QuestCatalogFilter.ACTIVE) return chain.quests().stream()
                .map(q -> states.state(player, q)).anyMatch(status -> status == QuestStatus.ACTIVE || status == QuestStatus.READY_TO_CLAIM);
        if (filter == QuestCatalogFilter.AVAILABLE) return chain.quests().stream()
                .map(q -> states.state(player, q)).anyMatch(status -> status == QuestStatus.AVAILABLE);
        return chain.quests().stream().map(q -> states.state(player, q)).anyMatch(status -> status == QuestStatus.COMPLETED);
    }

    private ItemStack chainItem(Player player, Chain chain) {
        QuestStatus status = chainStatus(player, chain);
        int completed = completedLevels(player, chain);
        Quest current = chain.quests().stream().filter(q -> states.state(player, q) != QuestStatus.COMPLETED).findFirst()
                .orElse(chain.quests().get(chain.quests().size() - 1));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Levels completed: <white>" + completed + "/" + chain.quests().size());
        lore.add("<gray>Current status: <white>" + status);
        lore.add("<gray>Next: <yellow>Level " + levelOf(current.id()));
        for (ObjectiveDefinition objective : current.objectives().values()) {
            lore.add(objective.display().replace("%progress%", String.valueOf(progress.progress(player, current, objective.id())))
                    .replace("%required%", String.valueOf(objective.amount())));
        }
        lore.add("<dark_gray>Click to view all five levels.");
        return item(materialFor(status), chainLabel(chain), lore);
    }

    private ItemStack levelItem(Player player, Quest quest, int level) {
        QuestStatus status = states.state(player, quest);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Level: <white>" + level);
        for (ObjectiveDefinition objective : quest.objectives().values()) {
            lore.add(objective.display().replace("%progress%", String.valueOf(progress.progress(player, quest, objective.id())))
                    .replace("%required%", String.valueOf(objective.amount())));
        }
        lore.add("<gray>Status: <white>" + status);
        lore.add("<dark_gray>Click to view details.");
        return item(materialFor(status), quest.displayName(), lore);
    }

    private ItemStack filterItem(QuestCatalogFilter value, QuestCatalogFilter selected) {
        Material material = value == selected ? Material.GLOWSTONE_DUST : Material.PAPER;
        return item(material, (value == selected ? "<green>" : "<yellow>") + filterLabel(value), List.of(
                value == selected ? "<green>Currently selected" : "<gray>Show " + filterLabel(value).toLowerCase() + " quest chains"
        ));
    }

    private QuestStatus chainStatus(Player player, Chain chain) {
        for (Quest quest : chain.quests()) {
            QuestStatus status = states.state(player, quest);
            if (status == QuestStatus.READY_TO_CLAIM || status == QuestStatus.ACTIVE || status == QuestStatus.AVAILABLE) return status;
        }
        return chain.quests().stream().allMatch(q -> states.state(player, q) == QuestStatus.COMPLETED)
                ? QuestStatus.COMPLETED : QuestStatus.LOCKED;
    }

    private int completedLevels(Player player, Chain chain) {
        return (int) chain.quests().stream().filter(q -> states.state(player, q) == QuestStatus.COMPLETED).count();
    }

    private String chainId(String questId) {
        return questId.replaceFirst("_[1-5]$", "");
    }

    private int levelOf(String questId) {
        if (questId.isEmpty()) return 1;
        char last = questId.charAt(questId.length() - 1);
        return last >= '1' && last <= '5' && questId.endsWith("_" + last) ? last - '0' : 1;
    }

    private String chainLabel(Chain chain) {
        return chainLabel(chain.quests().get(0));
    }

    private String chainLabel(Quest quest) {
        return quest.displayName().replaceFirst("\\s+(I|II|III|IV|V)$", "");
    }

    private String filterLabel(QuestCatalogFilter filter) {
        return switch (filter) {
            case ALL -> "All Mob Chains";
            case ACTIVE -> "Active / Ready";
            case AVAILABLE -> "Available";
            case COMPLETED -> "Started / Completed";
        };
    }

    private List<String> lore(Player player, Quest quest) {
        List<String> lore = new ArrayList<>(quest.description());
        lore.add("<yellow>Objectives:");
        for (ObjectiveDefinition objective : quest.objectives().values()) {
            lore.add(objective.display().replace("%progress%", String.valueOf(progress.progress(player, quest, objective.id())))
                    .replace("%required%", String.valueOf(objective.amount())));
        }
        lore.add("<yellow>Status: <white>" + states.state(player, quest));
        return lore;
    }

    private Material materialFor(QuestStatus status) {
        return switch (status) {
            case AVAILABLE -> Material.WRITABLE_BOOK;
            case ACTIVE -> Material.CLOCK;
            case READY_TO_CLAIM -> Material.CHEST;
            case COMPLETED -> Material.LIME_DYE;
            case LOCKED -> Material.BARRIER;
            case COOLDOWN -> Material.GRAY_DYE;
        };
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(mini.deserialize(name));
        meta.lore(lore.stream().map(mini::deserialize).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private Component component(String raw, Map<String, String> replacements) {
        return messages.component(raw, replacements);
    }

    private String guiString(String path, String fallback) {
        return guiConfig.getString(path, fallback);
    }

    private Material guiMaterial(String path, Material fallback) {
        String configured = guiConfig.getString(path);
        Material material = configured == null ? null : Material.matchMaterial(configured.trim());
        if (material == null) {
            if (configured != null && !configured.isBlank()) {
                plugin.getLogger().warning("Invalid GUI material '" + configured + "' at " + path
                        + "; using " + fallback.name() + ".");
            }
            return fallback;
        }
        return material;
    }

    private String npcName(String npcId) {
        var npc = npcs.get(npcId);
        return npc == null ? npcId : npc.displayName();
    }

    private record Chain(String id, List<Quest> quests) {
    }
}
