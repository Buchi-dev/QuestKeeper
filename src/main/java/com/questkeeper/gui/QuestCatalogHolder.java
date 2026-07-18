package com.questkeeper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class QuestCatalogHolder implements InventoryHolder {
    private Inventory inventory;
    public final String npcId;
    public final int page;
    public final QuestCatalogFilter filter;
    public final Map<Integer, String> chains = new HashMap<>();

    public QuestCatalogHolder(String npcId, int page, QuestCatalogFilter filter) {
        this.npcId = npcId;
        this.page = page;
        this.filter = filter;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
