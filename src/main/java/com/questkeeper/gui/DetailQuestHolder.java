package com.questkeeper.gui;
import org.bukkit.inventory.*;
public final class DetailQuestHolder implements InventoryHolder {
    private Inventory inventory;
    public final String questId;
    public final String npcId;
    public final String chainId;

    public DetailQuestHolder(String questId, String npcId, String chainId) {
        this.questId = questId;
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
