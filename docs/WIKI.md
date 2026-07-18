# QuestKeeper Wiki

QuestKeeper is a lightweight quest engine for Paper 1.21.x. Administrators create quests in YAML, assign them to persistent villager NPCs, and players accept, complete, and claim rewards through inventory interfaces.

This wiki documents the current QuestKeeper MVP accurately, including supported settings and optional integration behavior.

## Contents

- Requirements and installation
- First quest in five minutes
- Player workflow and quest states
- NPC configuration
- Quest configuration
- Objective examples
- Requirements and quest chains
- Repeatable quests
- Rewards
- GUI behavior
- Commands and permissions
- Messages and GUI customization
- Optional integrations and PlaceholderAPI
- Developer API and events
- Persistence, security, reloading, and troubleshooting
- Testing and building

## Requirements

| Requirement | Version or behavior |
| --- | --- |
| Server | Paper 1.21.x |
| Java | Java 21 |
| Database | SQLite, bundled by the plugin build |
| Text | Adventure API and MiniMessage |
| Required external plugin | None |
| Optional plugins | Vault, MythicMobs, PlaceholderAPI, WorldGuard, LuckPerms |

QuestKeeper does not use NMS, CraftBukkit internals, fake-player NPCs, or version-specific reflection.

## Installation

1. Build or download questkeeper-1.0.0.jar.
2. Stop the Paper server or use your normal controlled deployment process.
3. Copy the JAR into the server plugins/ directory.
4. Start the server with Java 21.
5. Confirm that the console reports QuestKeeper enabled.
6. Back up plugins/QuestKeeper/ before making major configuration changes.

The first startup creates:

~~~text
plugins/QuestKeeper/
├── config.yml
├── guis.yml
├── messages.yml
├── npcs.yml
├── quests/
│   └── example_quest.yml
└── quests.db
~~~

Do not edit quests.db while the server is running. Stop the server before making a database backup or manual database change.

## First quest in five minutes

Run these commands as an administrator:

~~~text
/questadmin npc create example_keeper
/questadmin quest create example_quest
/questadmin npc assign example_keeper example_quest
/questadmin reload
~~~

The NPC is created at the administrator's current location. The quest command creates a starter file under plugins/QuestKeeper/quests/. Edit the file and ensure npc.start and npc.claim both reference example_keeper.

Test the quest:

1. Right-click the villager.
2. Open the quest details item.
3. Click Accept Quest.
4. Kill the configured zombies.
5. Return to the configured claim NPC.
6. Click Claim Reward.

Rewards are not automatically granted when the final objective is completed. The default flow requires the player to return to the claim NPC.

## Player workflow

1. A player right-clicks a marked QuestKeeper villager.
2. Normal villager trading is cancelled.
3. The greeting is displayed and the 54-slot quest GUI opens.
4. The player reviews a quest and opens its 27-slot details GUI.
5. Accept Quest changes the record to ACTIVE.
6. Relevant Paper events update matching objectives.
7. The final objective changes the record to READY_TO_CLAIM.
8. The player returns to the quest's npc.claim NPC.
9. QuestKeeper revalidates state, requirements, objectives, claim location, inventory capacity, and the claim lock.
10. Rewards are applied synchronously, then the record becomes COMPLETED or COOLDOWN.

Quest GUIs use custom InventoryHolder implementations. Titles are not used as the identity source.

## Quest states

~~~text
LOCKED -> AVAILABLE -> ACTIVE -> READY_TO_CLAIM -> COMPLETED
                                      |
                                      +-> COOLDOWN -> AVAILABLE
~~~

- LOCKED: a permission, level, world, or completed-quest requirement is missing.
- AVAILABLE: the player can accept the quest.
- ACTIVE: the player has accepted it.
- READY_TO_CLAIM: every objective has reached its required amount.
- COMPLETED: a one-time quest has been claimed, or a calendar quest is waiting for its reset.
- COOLDOWN: a repeatable quest is waiting for its configured cooldown.

Default GUI icons are WRITABLE_BOOK for available, CLOCK for active, CHEST for ready, LIME_DYE for completed, BARRIER for locked, and GRAY_DYE for cooldown.

## NPC configuration

NPC definitions live in plugins/QuestKeeper/npcs.yml.

~~~yaml
npcs:
  savanna_keeper:
    display-name: '<gold><bold>Savanna Keeper'
    profession: WEAPONSMITH
    villager-type: SAVANNA
    silent: true
    location:
      world: survival
      x: 120.5
      y: 65.0
      z: -340.5
      yaw: 90.0
      pitch: 0.0
    greeting:
      - '<yellow>The savanna is becoming dangerous.'
      - '<gray>Select a quest if you are prepared.'
    quests:
      - savanna_hunter_1
      - savanna_hunter_2
~~~

NPC IDs must match ^[a-z0-9_]{3,50}$. QuestKeeper stores a persistent marker and the NPC ID in the entity PersistentDataContainer. UUIDs and display names are not used as identity.

NPCs are invulnerable, persistent, no-AI, optionally silent, non-collidable where supported, protected from targeting, and protected from ordinary trading. A scheduled validation task respawns missing entities.

## Quest configuration

Each quest is a separate YAML file in plugins/QuestKeeper/quests/. The id field is authoritative.

~~~yaml
id: savanna_hunter_1
display-name: '<gold>Savanna Hunter I: First Blood'
description:
  - '<gray>The savanna has been overrun by corrupted creatures.'
  - '<gray>Defeat them and return to the Savanna Keeper.'
icon:
  material: IRON_SWORD
npc:
  start: savanna_keeper
  claim: savanna_keeper
objectives:
  kill_zombies:
    type: KILL_ENTITY
    entity: ZOMBIE
    amount: 12
    display: '<yellow>Kill zombies: <white>%progress%/%required%'
requirements:
  permission: 'questkeeper.quest.savanna'
  minimum-level: 5
  world: survival
  completed-quests: []
rewards:
  experience: 250
  money: 500
  commands:
    - 'give %player% experience_bottle 8'
  items:
    corrupted_fragment:
      material: ENDER_PEARL
      amount: 10
      name: '<dark_purple>Corrupted Fragment'
      lore:
        - '<gray>A fragment obtained from corrupted enemies.'
settings:
  repeat-type: ONE_TIME
  cooldown-seconds: 0
  auto-claim: false
  allow-cancel: true
  consume-collected-items: false
~~~

QuestLoader validates IDs, materials, objective types, repeat types, objective amounts, and the existence of an objective section. Invalid files are skipped and the exact file/path is logged; valid files still load.

## Objective examples

Every objective has a type, positive amount, and display. Settings are placed below the objective key.

### KILL_ENTITY

~~~yaml
objectives:
  skeletons:
    type: KILL_ENTITY
    entity: SKELETON
    amount: 20
    display: '<yellow>Defeat skeletons: <white>%progress%/%required%'
~~~

The current listener matches the Paper entity type name.

### KILL_MYTHIC_MOB

~~~yaml
objectives:
  corrupted_guardians:
    type: KILL_MYTHIC_MOB
    mob-id: Corrupted_Guardian
    amount: 3
    display: '<yellow>Defeat guardians: <white>%progress%/%required%'
~~~

MythicMobs is optional. Without it, QuestKeeper starts normally and logs that MythicMob objectives are unavailable.

### COLLECT_ITEM

~~~yaml
objectives:
  fragments:
    type: COLLECT_ITEM
    material: ENDER_PEARL
    amount: 10
    display: '<yellow>Collect fragments: <white>%progress%/%required%'
~~~

The current implementation observes player item pickups and matches material. Custom model data, custom tags, and claim-time consumption are reserved for the expanded item-objective implementation.

### BREAK_BLOCK

~~~yaml
objectives:
  ancient_stone:
    type: BREAK_BLOCK
    material: STONE
    amount: 64
    count-player-placed-blocks: false
    display: '<yellow>Break stone: <white>%progress%/%required%'
~~~

Player-placed blocks are tracked in a lightweight in-memory anti-exploit cache during the current server session.

### CRAFT_ITEM

~~~yaml
objectives:
  bread:
    type: CRAFT_ITEM
    material: BREAD
    amount: 16
    display: '<yellow>Craft bread: <white>%progress%/%required%'
~~~

Crafting progress comes from CraftItemEvent. Shift-click crafting uses the recipe output stack size as the batch amount in the current MVP.

### PLACE_BLOCK

~~~yaml
objectives:
  campfires:
    type: PLACE_BLOCK
    material: CAMPFIRE
    amount: 3
    display: '<yellow>Place campfires: <white>%progress%/%required%'
~~~

Progress is updated from successful block placement events.

### REACH_LOCATION

~~~yaml
objectives:
  outpost:
    type: REACH_LOCATION
    world: survival
    x: 120.0
    y: 65.0
    z: -340.0
    radius: 4.0
    amount: 1
    display: '<yellow>Reach the outpost: <white>%progress%/%required%'
~~~

Movement is checked after a meaningful movement threshold rather than scanning every online player every tick.

### INTERACT_NPC

~~~yaml
objectives:
  speak_to_keeper:
    type: INTERACT_NPC
    npc-id: savanna_keeper
    amount: 1
    display: '<yellow>Speak to the keeper: <white>%progress%/%required%'
~~~

Progress occurs when the player right-clicks the matching QuestKeeper NPC.

### EXECUTE_COMMAND

~~~yaml
objectives:
  report:
    type: EXECUTE_COMMAND
    command: quest report
    amount: 1
    display: '<yellow>Use the report command: <white>%progress%/%required%'
~~~

For reliable cross-plugin updates, prefer the admin progress command or public Java API.

## Requirements and quest chains

The implemented requirement fields are:

~~~yaml
requirements:
  permission: 'questkeeper.quest.savanna'
  minimum-level: 5
  world: survival
  completed-quests:
    - introduction
    - savanna_hunter_1
~~~

- permission: the player must have this permission.
- minimum-level: the player's Minecraft experience level must be at least this value.
- world: the player must be in this world.
- completed-quests: every listed quest must be COMPLETED.

Quest chain example:

~~~yaml
id: savanna_hunter_2
display-name: '<gold>Savanna Hunter II'
npc:
  start: savanna_keeper
  claim: savanna_keeper
objectives:
  elite:
    type: KILL_ENTITY
    entity: HUSK
    amount: 5
requirements:
  completed-quests:
    - savanna_hunter_1
~~~

The details GUI displays missing requirements for locked quests.

## Repeatable quests

Supported repeat types are ONE_TIME, REPEATABLE, DAILY, and WEEKLY.

~~~yaml
quest-resets:
  timezone: 'Asia/Manila'
  daily-reset-time: '00:00'
  weekly-reset-day: MONDAY
  weekly-reset-time: '00:00'
~~~

A repeatable quest example:

~~~yaml
settings:
  repeat-type: REPEATABLE
  cooldown-seconds: 3600
  allow-cancel: true
~~~

A daily quest example:

~~~yaml
settings:
  repeat-type: DAILY
  cooldown-seconds: 0
  allow-cancel: false
~~~

Timestamps are stored as UTC epoch milliseconds. Calendar comparisons use the configured timezone.

## Rewards

### Experience

~~~yaml
rewards:
  experience: 250
~~~

The value is raw Minecraft experience points.

### Console commands

~~~yaml
rewards:
  commands:
    - 'give %player% diamond 3'
    - 'say %player% completed %quest_id%'
~~~

Supported placeholders are %player%, %player_uuid%, and %quest_id%. Commands run from the console.

### Items

~~~yaml
rewards:
  items:
    hunter_badge:
      material: GOLD_NUGGET
      amount: 1
      name: '<gold>Hunter Badge'
      lore:
        - '<gray>Proof of your first hunt.'
~~~

Inventory capacity is checked before claiming. The default behavior prevents a claim when there is not enough room instead of dropping items.

### Money

~~~yaml
rewards:
  money: 500
~~~

Money requires Vault and a compatible economy provider. Without them, QuestKeeper logs a warning and does not provide the money reward.

### Permission rewards

For permission changes, use a console command reward in the current MVP:

~~~yaml
rewards:
  commands:
    - 'lp user %player% permission set server.hunter true'
~~~

## GUI behavior

The NPC GUI uses 54 slots and provides quest pages, previous/next navigation, refresh, close, status icons, and quest details. The details GUI uses 27 slots and shows description, objectives, progress, requirements, rewards, status, and the current action.

Action button behavior:

- AVAILABLE: Accept Quest.
- ACTIVE: Progress and optional Cancel Quest.
- READY_TO_CLAIM: Claim Reward.
- LOCKED: Missing requirements.
- COMPLETED: Completion information.
- COOLDOWN: Cooldown information.

All clicks, shift-clicks, number-key movement, dragging, and item stealing are cancelled inside QuestKeeper inventories.

The player journal opens with /quests. The current MVP opens the journal for active, completed, tracked, and help subcommands as well; filtering and tracked-quest persistence remain intentionally simple.

## Commands and permissions

Player permissions:

- questkeeper.use
- questkeeper.quests
- questkeeper.cancel

Player commands:

| Command | Description |
| --- | --- |
| /quests | Open the quest journal. |
| /quest and /q | Aliases for /quests. |
| /quests cancel <quest> | Cancel an active cancellable quest. |
| /quests active | Open the journal. |
| /quests completed | Open the journal. |
| /quests tracked | Open the journal. |
| /quests help | Open the journal. |

Administrator permission: questkeeper.admin.

Administrator commands:

| Command | Description |
| --- | --- |
| /questadmin reload | Save pending data, reload files, and respawn NPCs. |
| /questadmin quest list | List loaded quests. |
| /questadmin quest create <id> | Create a starter quest YAML. |
| /questadmin quest validate <id> | Confirm a loaded quest. |
| /questadmin npc list | List configured NPCs. |
| /questadmin npc create <id> | Create an NPC at the current location. |
| /questadmin npc remove <id> | Remove an NPC definition and entity. |
| /questadmin npc assign <npc> <quest> | Assign a quest to an NPC. |
| /questadmin npc unassign <npc> <quest> | Remove a quest assignment. |
| /questadmin progress <player> <quest> <objective> <amount> | Add progress for an online player. |
| /questkeeper progress <player> <quest> <objective> <amount> | Progress command alias. |
| /questadmin complete <player> <quest> | Complete every objective for an online player. |
| /questadmin reset <player> <quest> | Remove one quest record. |
| /questadmin debug <player> | Print quest states. |

## Configuration

Important config.yml settings:

~~~yaml
database:
  file: quests.db
autosave-interval-seconds: 60
active-quest-limit: 3
gui-click-cooldown-millis: 250
npc-validation-interval-seconds: 30
reward-inventory-behavior: PREVENT
reach-check-interval-ticks: 20
reach-movement-threshold: 1.0
debug: false
integrations:
  vault: true
  placeholderapi: true
  mythicmobs: true
  worldguard: true
quest-resets:
  timezone: Asia/Manila
  daily-reset-time: '00:00'
  weekly-reset-day: MONDAY
  weekly-reset-time: '00:00'
~~~

Messages use MiniMessage and are stored in messages.yml:

~~~yaml
prefix: '<dark_gray>[<gold>QuestKeeper</gold>]</dark_gray> '
quest-accepted: '%prefix%<green>You accepted <yellow>%quest%</yellow>.'
inventory-full: '%prefix%<red>You need more inventory space before claiming.'
~~~

guis.yml provides the supported title and item material configuration surface. The custom-holder logic remains authoritative for identifying inventories.

## Optional integrations

- Vault: detected at startup; money requires Vault plus an economy provider.
- MythicMobs: required for MythicMob objectives.
- PlaceholderAPI: registers the questkeeper expansion when installed.
- WorldGuard: declared as a soft dependency for future region-aware filters; ordinary objectives do not require it.
- LuckPerms: not required; use console command rewards for permission changes.

QuestKeeper starts normally when none of these plugins are installed.

## PlaceholderAPI

When PlaceholderAPI is installed, these placeholders are available:

~~~text
%questkeeper_active_count%
%questkeeper_completed_count%
%questkeeper_ready_count%
%questkeeper_quest_example_quest_status%
%questkeeper_quest_example_quest_progress%
%questkeeper_tracked_quest%
~~~

Unknown quest IDs return safe values such as UNKNOWN or 0. The tracked quest placeholder is empty until tracked-quest persistence is configured.

## Developer API and events

Get the API through Bukkit's service manager:

~~~java
QuestKeeperAPI api = Bukkit.getServicesManager().load(QuestKeeperAPI.class);
if (api != null) {
    Quest quest = api.getQuest("example_quest");
    api.addProgress(player, "example_quest", "zombies", 1);
}
~~~

Available API methods include:

~~~java
Quest getQuest(String questId);
Collection<Quest> getQuests();
QuestStatus getPlayerQuestState(UUID playerId, String questId);
boolean acceptQuest(Player player, String questId);
void addProgress(Player player, String questId, String objectiveId, int amount);
boolean completeQuest(Player player, String questId);
void resetQuest(UUID playerId, String questId);
~~~

Published events are under com.questkeeper.api.event:

- PlayerQuestAcceptEvent — cancellable.
- PlayerQuestProgressEvent.
- PlayerQuestObjectivesCompleteEvent.
- PlayerQuestClaimEvent — cancellable.
- PlayerQuestCompleteEvent.
- PlayerQuestCancelEvent — cancellable.

Example listener:

~~~java
@EventHandler
public void onQuestAccept(PlayerQuestAcceptEvent event) {
    if (event.getQuest().id().startsWith("admin_")) {
        event.setCancelled(true);
    }
}
~~~

Progress calls involving a Bukkit Player should run on the server thread. QuestKeeper owns database work with its asynchronous SQLite executor.

## Persistence and performance

SQLite stores player records, quest states, objective progress, and placed-block tracking. Bukkit entity, inventory, event, and reward operations stay on the server thread. Database work uses a dedicated executor.

Recommended production settings:

- Keep autosave between 30 and 120 seconds.
- Keep the active quest limit low enough to avoid unnecessary event matching.
- Keep NPC validation at 30 seconds or higher.
- Use a practical reach radius.
- Back up quests.db during scheduled maintenance.

## Security and validation

Quest and NPC IDs are restricted to lowercase letters, numbers, and underscores, length 3–50. This prevents path traversal and accidental writes outside the plugin directory.

QuestLoader validates materials, enum values, objective amounts, and quest structure. Rewards are only marked claimed after state, requirements, objective, claim-NPC, inventory, and claim-lock checks pass.

## Reloading

Use /questadmin reload.

The reload process saves pending cache data, reloads messages and quests, replaces the quest registry, removes old NPC entities, loads new NPC definitions, and respawns the current entities. Player progress remains in SQLite and is not reset.

Close open QuestKeeper inventories before editing quest YAML and reloading.

## Troubleshooting

### NPC does not spawn

Check that the world exists, the location is valid, and the ID is valid. Search the console for Failed to load NPC.

### Quest does not appear

Verify the assignment:

~~~yaml
npcs:
  example_keeper:
    quests:
      - example_quest
~~~

Then run /questadmin quest validate example_quest and /questadmin reload.

### Quest is locked

Read the Requirements item in the details GUI. Check permission, Minecraft level, world, and every completed-quests entry.

### Progress does not change

Confirm the player accepted the quest and that the objective setting matches the event. Entity objectives use entity: ZOMBIE; block objectives use material: STONE.

### Claim is rejected

The player must have READY_TO_CLAIM state and must return to the exact npc.claim NPC. The claim NPC must not merely be another NPC with the same quest assigned.

### Inventory is full

Free inventory space and retry. The default policy prevents the claim rather than dropping rewards.

### Optional integration warnings

Warnings for missing Vault or MythicMobs are expected. Ordinary quests continue to work without optional dependencies.

### Data appears missing

Confirm quests.db exists, wait for player data to load after joining, and inspect database warnings. Never copy or edit the live database.

## Testing checklist

Functional:

- NPC creation, spawn, persistence, respawn, and trade prevention.
- Greeting, GUI navigation, quest acceptance, status icons, and details.
- Kill, collect, break, craft, place, reach, NPC, and command objectives.
- Progress cap and READY_TO_CLAIM transition.
- Correct claim NPC, inventory validation, reward delivery, and duplicate-click prevention.
- Cancellation, one-time completion, cooldown, daily reset, and weekly reset.
- Reconnect, restart, reload, invalid YAML, and missing integrations.

Security and resilience:

- GUI movement, dragging, number-key transfers, and stealing are blocked.
- Admin permission checks reject unauthorized users.
- Invalid IDs cannot escape the plugin directory.
- Database errors are logged without taking down the server.

## Building from source

Install Java 21 and Maven:

~~~bash
mvn clean test
mvn clean package
~~~

The first command runs pure unit tests. The second creates target/questkeeper-1.0.0.jar.

The project separates bootstrap, database, domain models, state rules, progress, claims, NPCs, GUIs, commands, API, and integrations so new objective types and reward providers can be added without placing the entire plugin in one class.
