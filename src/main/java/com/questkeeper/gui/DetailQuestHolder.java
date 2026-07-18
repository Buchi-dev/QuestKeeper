package com.questkeeper.gui;
import org.bukkit.inventory.*;
public final class DetailQuestHolder implements InventoryHolder { private Inventory inventory; public final String questId,npcId; public DetailQuestHolder(String q,String n){questId=q;npcId=n;} public void inventory(Inventory i){inventory=i;} public Inventory getInventory(){return inventory;} }
