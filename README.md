# Better PV

Better PV is a Hypixel SkyBlock **profile viewer** mod for Fabric on Minecraft 26.1.2. Type `/pv <player>` to see any player's SkyBlock profile in game: skills, dungeons, collections, pets, storage, Heart of the Mountain, trophy fish, bestiary, farming and the garden, foraging, loadouts, museum, chocolate factory, the Rift and more. There's no stats website to open, and **no API key is needed**.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for Minecraft 26.1.2. The Minecraft launcher provides the Java 25 it needs.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.1.2 and put it in your `mods` folder.
3. Download the latest Better PV jar from this repo's [Releases](https://github.com/daweapon/BetterPV/releases) and put it in your `mods` folder too.
4. Launch the game and join Hypixel. That's it: Better PV fetches profile data through its own server, so you don't need a Hypixel API key.

The first time you start the game, Better PV downloads the NotEnoughUpdates item repo, which has item icons, pets and the bestiary and HOTM layouts. It needs an internet connection for this.

### Optional

- **Item textures:** with the official Hypixel SkyBlock resource pack enabled, items show their SkyBlock textures instead of the vanilla ones.

## Usage

| Command | Description |
| --- | --- |
| `/pv [player]` | Opens the profile viewer for a player (yourself if no name is given). |
| `/peek [player]` | Prints a quick summary of a player's stats in chat. |

## Credits

- **NotEnoughUpdates.** Better PV is a port of the Profile Viewer from [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates), created by Moulberry and developed by the [NotEnoughUpdates contributors](https://github.com/NotEnoughUpdates/NotEnoughUpdates/graphs/contributors) for Forge 1.8.9. Most of the profile viewer's logic, layouts and textures are their work. Better PV brings it to modern Fabric, adapts it to Hypixel's current API and SkyBlock updates, and leaves out NEU's other features. It also uses NEU's community-maintained [item repo](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) for item, pet, bestiary and HOTM data. Their original copyright notices are kept in every ported source file.
- **SkyBlockPv.** Portions of this code are from the [SkyBlockPv](https://github.com/meowdding/skyblock-pv) mod.
- **SkyBlock level task values.** The per-task SkyBlock XP in `sblevel_tasks.json` (Level page) comes from the Hypixel wiki's SkyBlock Levels tables, via the community dataset [SkyblockXP-BAZALRIGHT-](https://github.com/8Doc/SkyblockXP-BAZALRIGHT-) by 8Doc.
- **Hypixel SkyBlock Resource Pack.** The trophy fish icons in `assets/hypixel_skyblock` are from the official SkyBlock Resource Pack, copyright Hypixel Inc. They are bundled free of charge under the pack's [license](src/main/resources/assets/hypixel_skyblock/LICENSE), which lets Hypixel-related apps use its assets. Better PV is not endorsed by Hypixel.

## License

LGPL-3.0, the same license as NotEnoughUpdates (see [COPYING](./COPYING) and [COPYING.LESSER](./COPYING.LESSER)). The code taken from SkyBlockPv is under the SkyBlockPv license (MIT with an attribution requirement).
