# QuestKeeper

QuestKeeper is a lightweight Paper 1.21.x quest plugin. Administrators define quests in YAML, assign them to persistent villager NPCs, and players accept, progress, and claim rewards through inventory GUIs.

## Features

- Persistent, marked villager NPCs with configurable greetings, professions, locations, and quest assignments.
- Custom-holder 54-slot NPC quest GUI, 27-slot quest details GUI, and player journal.
- Centralized `LOCKED`, `AVAILABLE`, `ACTIVE`, `READY_TO_CLAIM`, `COMPLETED`, and `COOLDOWN` state rules.
- SQLite player cache with asynchronous load/save, autosave, reconnect persistence, and claim locks.
- YAML quest validation that skips invalid files without stopping the plugin.
- Kill, break, place, craft, command/API progress, and NPC interaction objective foundations.
- Experience, item, and console command rewards; Vault, MythicMobs, PlaceholderAPI, WorldGuard, and LuckPerms are optional soft integrations.
- Public Java API and Bukkit quest events.

## Requirements and installation

Use Java 21 and Paper 1.21.x. Copy the shaded `target/questkeeper-1.0.0.jar` to `plugins/`, start the server once, and edit the generated files under `plugins/QuestKeeper/`.

Quick start:

1. Install QuestKeeper.
2. Restart the server.
3. Run `/questadmin npc create savanna_keeper`.
4. Run `/questadmin quest create savanna_hunter_1`.
5. Configure the quest through YAML.
6. Run `/questadmin npc assign savanna_keeper savanna_hunter_1`.
7. Run `/questadmin reload`.
8. Right-click the NPC and test the quest.

## Quest configuration

Quest files live in `plugins/QuestKeeper/quests/`. See `src/main/resources/quests/example_quest.yml` for a working example. IDs must match `^[a-z0-9_]{3,50}$`. The quest's `npc.start` controls where it can be accepted and `npc.claim` controls where it can be claimed. Rewards are not granted automatically unless `settings.auto-claim` is enabled by a future event integration; the normal claim flow always requires returning to the claim NPC.

Invalid materials, enum values, IDs, amounts, and missing objective sections are logged with the file and YAML path, then skipped.

## Commands and permissions

Player commands: `/quests`, `/quests active`, `/quests completed`, `/quests tracked`, `/quests cancel <quest>`, `/quests help`. Required permission is `questkeeper.quests`; cancellation also requires `questkeeper.cancel`.

Admin commands: `/questadmin reload`, `quest list|create|validate`, `npc list|create|remove|assign|unassign`, `progress`, `complete`, `reset`, `resetall`, and `debug`. Required permission is `questkeeper.admin`.

## Optional dependencies

The plugin starts without Vault, MythicMobs, PlaceholderAPI, WorldGuard, or LuckPerms. Vault money rewards are disabled without an economy provider; MythicMob objectives are ignored with a clear warning when MythicMobs is absent.

## Developer API

```java
QuestKeeperAPI api = Bukkit.getServicesManager()
    .load(QuestKeeperAPI.class);
api.addProgress(player, "example_quest", "zombies", 1);
```

The API exposes quest lookup, state lookup, accept, progress, objective completion, and reset operations. Gameplay events are in `com.questkeeper.api.event`.

## Troubleshooting

Check `plugins/QuestKeeper/quests/` for invalid YAML messages. Confirm the configured world exists before loading NPCs. If a quest does not appear, verify that its ID is assigned under the NPC's `quests` list and run `/questadmin reload`. A full inventory prevents item-reward claims by default.

## Building from source

Run `mvn clean package` with Java 21. The plugin JAR is written to `target/`.

## Manual test checklist

NPC spawn/persistence, trade cancellation, quest acceptance, kill/break/place/craft progress, ready-to-claim transition, claim NPC validation, duplicate clicks, full inventory, cancellation, reconnect/restart persistence, daily/weekly reset, missing integrations, invalid YAML, reload, and admin permission checks.
