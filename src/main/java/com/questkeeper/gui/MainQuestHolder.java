package com.questkeeper.gui;
import org.bukkit.inventory.*; import java.util.*;
public final class MainQuestHolder implements InventoryHolder { private Inventory inventory; public final String npcId; public final int page; public final Map<Integer,String> quests=new HashMap<>(); public MainQuestHolder(String npcId,int page){this.npcId=npcId;this.page=page;} public void inventory(Inventory i){inventory=i;} public Inventory getInventory(){return inventory;} }
