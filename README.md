# QuestKeeper

QuestKeeper is a lightweight Paper 1.21.x quest plugin built around persistent villager NPCs, YAML quest files, inventory GUIs, automatic objective progress, and SQLite player data.

## Documentation

See [docs/WIKI.md](docs/WIKI.md) for the detailed administrator and developer guide.

The wiki covers installation, NPC and quest YAML, objective and reward examples, commands, permissions, quest states, integrations, PlaceholderAPI, the Java API, troubleshooting, and testing.

## Quick start

1. Build or download QuestKeeper and place the JAR in plugins/.
2. Start the server once with Java 21 and Paper 1.21.x.
3. Run /questadmin npc create example_keeper.
4. Run /questadmin quest create example_quest.
5. Configure the quest YAML.
6. Run /questadmin npc assign example_keeper example_quest.
7. Run /questadmin reload.
8. Right-click the villager and test the quest.

## Build

~~~bash
mvn clean package
~~~

The shaded plugin JAR is generated in target/.

## License

QuestKeeper is released under the MIT License. See LICENSE.
