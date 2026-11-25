<h1 align="center">AdvancedWayPoints</h1>

<p align="center"><b>Your players will never ask "where was my base?" in chat again.</b></p>

<p align="center">
Personal waypoints that float in the world, face the player and count down the meters to home.<br>
Free and open source for Spigot, Paper, Purpur and Folia 1.19.4+.
</p>

<p align="center">
<a href="https://github.com/Obed-source/AdvancedWayPoints/releases/latest"><b>⬇ Download the latest release</b></a>
&nbsp;·&nbsp;
<a href="#commands">Commands</a>
&nbsp;·&nbsp;
<a href="#developer-api">Developer API</a>
&nbsp;·&nbsp;
<a href="https://paypal.me/obedmontano">☕ Support the project</a>
</p>

---

## What your players get

A player stands at the entrance of their mine and types:

```
/wp create north mine
```

From then on, a label like `⛏ north mine (342m)` hangs in the sky in the exact direction of the mine, readable from any distance and visible only to them. They walk, the meters go down. They switch to the Nether, the label waits and comes back the moment they return to the Overworld.

When they die, a red `☠ Death (12m) ⌛9:41` marker appears where they fell and disappears when the timer runs out, so they know exactly how long they have to recover their items. Die three times on a bad night and all three spots stay marked, each with its own timer.

## Why server owners pick it

**It respects your TPS.** Database reads, writes and update checks run on their own threads. Player data loads while the player is still on the login screen, stays in memory while they play and is released when they leave. Menus are built once when the plugin starts; opening one only fills in the player's own waypoints.

**Everything players read is yours to change.** Every message, menu title, lore line, help page and color name lives in `lang/en.yml`, `lang/es.yml`, `lang/fr.yml`, `lang/de.yml` or `lang/pt.yml`. Menu sizes, item materials, button positions and the first slot of the list live in `menus.yml`. Every sound lives in `sounds.yml`.

**Updates never wipe your setup.** Each file is commented line by line. When a new version adds options, they are written into your files with their comments and your values stay as they were. A `.bak` copy is saved first.

**It runs on the server you already have.** One jar works on Spigot, Paper, Purpur and Folia from 1.19.4 up, with Java 17. WorldGuard, Vault and PlaceholderAPI are optional.

**It can earn its keep.** Charge money through Vault, or items from the inventory, for creating, sharing or teleporting, with separate prices for death waypoints. Items can be plain (`5x Diamond`) or your own special items, matched by NBT key, custom model data or name. All prices are off by default, and lore lines can show them with `{teleport_price}` or `{share_price}`.

## Features

### Floating labels
- Text Display labels that always face the player, with the distance (`❤ home (30m)`, `1.2km` past a kilometer).
- Far waypoints are drawn in their real direction and scaled so they stay readable; they hold still when the player jumps.
- Each label shows to its owner only. Waypoints in another world are kept selected and reappear automatically.
- Two view modes: **one at a time** (picking a waypoint hides the previous one, picking it again hides it) or **accumulate**.

### Menus
- `/wp menu` lists every waypoint as a map item with world, coordinates, distance, creation date and status.
- Left click shows or hides it, right click edits it, shift + left click teleports (only for players with permission). Every lore line is free text with placeholders, so you decide the wording and the casing.
- The edit menu covers name, text color, icon, icon color, background color, favorite, share, teleport and delete with confirmation.
- Favorites go to the top in the order they were marked; the rest follow by date.
- Pagination is always on. Buttons a player can't use show a "no permission" line, or hide completely if you prefer.

### Typing names without leaving the menu
Pick how players type a new name or the player to share with:

| Mode | How it works |
|---|---|
| `ANVIL` (default) | Anvil rename field (Paper, Purpur, Folia, or Spigot 1.21+) |
| `SIGN` | A temporary sign that only that player edits |
| `COMMAND` | A clickable message that pre-fills the command |
| `CHAT` | The next chat message becomes the value |

If the chosen mode isn't available on your server, the next one is used.

### Sharing
`/wp share <id> <player>` sends an online player a chat message with clickable **[ACCEPT]** and **[DECLINE]** buttons. If they accept, they get their own copy, shown to them right away, and the sender pays the price (if any). No link remains between the two. Sharing can be switched off for normal waypoints and for death waypoints separately.

### Death waypoints
On by default, with a skull icon, red text and a countdown on the label. Two modes: **ACCUMULATE** keeps up to 3 deaths (configurable), replacing the oldest, each with its own timer; **LATEST** keeps only the last one. Death points sit above everything else in the menu, even favorites, and their look is locked for players. They have their own switches for sharing and teleporting, their own permission (`wp.teleport.death`) and their own prices. You choose the duration, the worlds and WorldGuard regions where they can be left (their own whitelist or blacklist, separate from the creation rules) and whether they use a waypoint slot.

### Teleport
`wp.teleport` for normal waypoints, `wp.teleport.death` for death points, `wp.admin.tp` for admins: three separate permissions. Players only teleport within the world they are in; `wp.teleport.crossworld` lets them reach waypoints in other worlds, and admin teleports have no world limit. Use shift + left click in the menu, the edit menu button or `/wp tp <id>`. An optional countdown (off by default) announces every 10 seconds, then every 5, then every second, and can be cancelled by taking damage or by moving away. Asking to teleport again, to the same waypoint or another, cancels it too.

### Rules and limits
- Waypoint limits from permissions: `wp.limit.5`, `wp.limit.20`... the highest wins.
- Whitelist or blacklist worlds and WorldGuard regions; players get a message when a spot is not allowed.
- Names up to 32 characters (configurable) with accents and `ñ`; `&` color codes with `wp.name.format`.
- Per-color permissions for text, icon and background, plus custom hex colors with `.custom`.

### Admin control
`/wp admin` reaches every player's waypoints, online or offline: list, open their menu, edit, recolor, rename, create, teleport, delete or clear. `/wp admin help` shows every command with colors and pages. `/wp restart` reloads the configuration without touching the database.

### Auto updates
On by default. The plugin reads [`update.json`](update.json) from this repository, downloads new versions into your server's `update` folder after checking their SHA-256, and they install on the next restart.

## Install

1. Download `AdvancedWayPoints-<version>.jar` from [Releases](https://github.com/Obed-source/AdvancedWayPoints/releases/latest).
2. Drop it into `plugins/` and restart.
3. Type `/wp create home` in game.

English is the default language. Set `language` in `config.yml` to `es` (Spanish), `fr` (French), `de` (German) or `pt` (Portuguese), or add your own `lang/<code>.yml`.

## Commands

The base command is `/waypoints` with the alias `/wp`; you can rename both in `config.yml`. `<id>` also accepts the exact waypoint name.

| Command | What it does |
|---|---|
| `/wp` | Opens the menu (or the help, configurable) |
| `/wp menu` | Waypoint menu |
| `/wp create <name>` | Creates a waypoint where you stand |
| `/wp list [page]` | Clickable list: click to show or hide, shift + click to insert the ID |
| `/wp view <id>` | Shows or hides a waypoint |
| `/wp hide <id>` / `/wp hide all` | Removes waypoints from view (they stay in the list) |
| `/wp mode` | Switches between one at a time and accumulate |
| `/wp rename <id> <name>` | Renames a waypoint |
| `/wp color <id> <text\|icon\|background> <color>` | Changes a color |
| `/wp favorite <id>` | Marks or unmarks a favorite |
| `/wp share <id> <player>` | Offers a copy to an online player (they accept or decline in chat) |
| `/wp tp <id>` | Teleports to a waypoint |
| `/wp delete <id>` | Deletes a waypoint |
| `/wp help [page]` | Player help |
| `/wp admin help [page]` | Every command, player and admin |
| `/wp admin list <player> [page]` | Lists a player's waypoints |
| `/wp admin menu <player>` | Opens a player's waypoints in the menu |
| `/wp admin info <player>` | Count, limit and view mode |
| `/wp admin tp <player> <id>` | Teleports to a player's waypoint |
| `/wp admin create <player> <name>` | Creates a waypoint for a player |
| `/wp admin rename <player> <id> <name>` | Renames a player's waypoint |
| `/wp admin color <player> <id> <type> <color>` | Recolors a player's waypoint |
| `/wp admin delete <player> <id>` | Deletes a player's waypoint |
| `/wp admin clear <player>` | Deletes all of a player's waypoints |
| `/wp restart` | Reloads the configuration |

## Permissions

The plugin grants nothing itself; use your permission plugin.

| Permission | Default | Purpose |
|---|---|---|
| `wp.help`, `wp.menu`, `wp.create`, `wp.list`, `wp.view`, `wp.hide`, `wp.mode`, `wp.rename`, `wp.delete`, `wp.share`, `wp.favorite`, `wp.icon`, `wp.death` | everyone | Player features (negate `wp.help` to hide the help) |
| `wp.limit.<n>` | — | Waypoint limit; the highest number wins, `default-limit` applies otherwise |
| `wp.limit.unlimited` | op | No limit |
| `wp.teleport` | op | Teleport to own waypoints |
| `wp.teleport.death` | op | Teleport to own death waypoints |
| `wp.teleport.crossworld` | op | Teleport to own waypoints in another world (normal and death) |
| `wp.color.text` / `wp.color.icon` / `wp.color.background` | everyone | Open each color picker |
| `wp.color.<type>.<color>` | op | Use that color, e.g. `wp.color.background.red`. Default colors are always allowed |
| `wp.color.<type>.custom` | op | Custom colors by command (`#FF8800`, `&c`, `&#FF8800`) |
| `wp.name.format` | op | `&` color codes in names |
| `wp.bypass.restrictions` | op | Ignore world and region rules |
| `wp.bypass.cost` | op | Never pay |
| `wp.admin` | op | Every admin action (`wp.admin.list`, `.menu`, `.info`, `.tp`, `.create`, `.rename`, `.color`, `.delete`, `.clear`, `.restart`, `.update`, `.help`) |

## PlaceholderAPI

Identifier `awp`:

| Placeholder | Value |
|---|---|
| `%awp_count%`, `%awp_limit%`, `%awp_remaining%` | Waypoints owned, limit, slots left |
| `%awp_visible_count%`, `%awp_favorites%`, `%awp_mode%` | Visible, favorites, view mode |
| `%awp_selected_name%`, `%awp_selected_icon%`, `%awp_selected_label%` | Last selected waypoint |
| `%awp_selected_distance%` | Raw distance, e.g. `30m` |
| `%awp_selected_distance_formatted%` | Styled from config, e.g. ` (30m)` |
| `%awp_selected_distance_raw%` | Number only |
| `%awp_selected_world%`, `_x`, `_y`, `_z`, `_code` | Selected waypoint data |
| `%awp_visible_list%`, `%awp_visible_distances%` | Every visible waypoint |
| `%awp_nearest_name%`, `%awp_nearest_distance%` | Closest waypoint |
| `%awp_deaths_count%`, `%awp_deaths_max%` | Death waypoints kept and the maximum |
| `%awp_death_<field>%`, `%awp_death_<n>_<field>%` | Newest death point, or the n-th (1 = newest) |
| `%awp_nearest_<field>%`, `%awp_selected_<field>%`, `%awp_<field>_<id>%` | Any field of a waypoint |
| `%awp_cost_<action>%` | Price as this player would pay it, or "free" (`create`, `share`, `teleport`, `death_share`, `death_teleport`) |
| `%awp_cost_<action>_type%`, `_amount`, `_item` | MONEY / ITEM / NONE, amount, item material |

Fields: `name`, `name_plain`, `code`, `icon`, `label`, `world`, `x`, `y`, `z`, `distance`, `distance_formatted`, `distance_raw`, `time_left`, `created`, `type`, `favorite`, `visible`.

## Developer API

```java
AdvancedWaypointsAPI api = AdvancedWaypointsAPI.get();
api.getWaypoints(player.getUniqueId()).thenAccept(list -> { /* runs off the main thread */ });
api.createWaypoint(player.getUniqueId(), "Base", player.getLocation());
api.showWaypoint(player, "aB3x");
int limit = api.getLimit(player);
```

Events: `WaypointCreateEvent`, `WaypointDeleteEvent`, `WaypointShareEvent` (cancellable), `WaypointUpdateEvent`, `WaypointVisibilityEvent`. Add `softdepend: [AdvancedWayPoints]` to your `plugin.yml`.

## Building from source

```
./gradlew build
```

The jar is written to `build/libs/`. `./gradlew test` checks the YAML files, the SQLite storage and the helpers.

## Support the project

AdvancedWayPoints is free and will stay free. If it saves your players a few lost bases, you can buy me a coffee at **[paypal.me/obedmontano](https://paypal.me/obedmontano)**. Bug reports and ideas are welcome in [Issues](https://github.com/Obed-source/AdvancedWayPoints/issues), and a ⭐ helps other server owners find it.

## License

[Apache License 2.0](LICENSE) © Obed-Source. You can use, modify and share it, including on public servers, as long as you keep the copyright notice and the [NOTICE](NOTICE) file.
