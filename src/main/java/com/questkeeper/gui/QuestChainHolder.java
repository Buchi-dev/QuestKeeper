package com.questkeeper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class QuestChainHolder implements InventoryHolder {
    private Inventory inventory;
    public final String npcId;
    public final String chainId;
    public final Map<Integer, String> quests = new HashMap<>();

    public QuestChainHolder(String npcId, String chainId) {
        this.npcId = npcId;
        this.chainId = chainId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
