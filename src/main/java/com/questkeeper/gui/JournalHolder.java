package com.questkeeper.gui;
import org.bukkit.inventory.*;
public final class JournalHolder implements InventoryHolder { private Inventory inventory; public void inventory(Inventory i){inventory=i;} public Inventory getInventory(){return inventory;} }
