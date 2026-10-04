# ResourceControlPlugin

Paper plugin for Minecraft 1.21.11. Allows administrators to assign resource packs to individual players
and groups. Packs are applied by the client mod [ResourceControl](https://github.com/Dvmn2/ResourceControl/).

A pack is a regular zip file. It can change the main menu panorama, inventory interface, HUD,
fonts, sounds, models, and shaders.

## Requirements

| Component | Version |
|---|---|
| Server | Paper 1.21.11 |
| Java | 21 |
| Client | Fabric + ResourceControl mod |
| Pack hosting | direct HTTPS links to zip files |

## Installation

1. Place `ResourceControlPlugin-<version>.jar` in the `plugins` folder.
2. Start the server. `config.yml`, `packs.yml` and `players.yml` will be created.
3. Upload a pack to a host (see "Pack hosting").
4. Add the pack to the library and give it to a player.

```
/resourcecontrol pack add panorama-winter https://github.com/<user>/<repo>/releases/download/<tag>/panorama-winter.zip
/resourcecontrol pack set panorama-winter persistent true
/resourcecontrol give <player> panorama-winter
```

## How it works

1. An administrator adds a pack to the library. The plugin downloads the file, computes its SHA-1 and size,
   checks for `pack.mcmeta` in the archive root, and detects shaders.
2. The administrator assigns the pack to a player by command, through the GUI, or through a group permission.
3. The plugin computes the player's effective set: individual packs, group packs, and temporary previews.
4. The set is sent to the client mod over the `dvmn2:rc_sync` channel. The mod downloads the packs and reports
   its status over the `dvmn2:rc_status` channel.
5. On any change to the set (command, permission change, pack update), a new set is sent to the player.

A player without the mod receives no packet and is disconnected if `require-client-mod` is enabled.

## Commands

Permission: `resourcecontrol.admin` (operators by default).

### Pack library

| Command | Action |
|---|---|
| `/resourcecontrol pack add <name> <url>` | Add a pack. The file is downloaded and validated. |
| `/resourcecontrol pack refresh <pack>` | Re-download the file from the stored URL and recompute SHA-1. |
| `/resourcecontrol pack remove <pack>` | Remove from the library and unassign from all players. |
| `/resourcecontrol pack list` | List packs. |
| `/resourcecontrol pack info <pack>` | Show pack parameters. |
| `/resourcecontrol pack set <pack> persistent <true\|false>` | Keep the pack on the client after leaving the server. |
| `/resourcecontrol pack set <pack> priority <-1000..1000>` | Layer priority. A higher value places the pack higher. |

Pack name: `a-z`, `0-9`, `_`, `-`, up to 32 characters. URL: HTTPS, up to 512 characters.

### Players

| Command | Action |
|---|---|
| `/resourcecontrol give <player> <pack>` | Give a pack. |
| `/resourcecontrol remove <player> <pack>` | Remove a pack. |
| `/resourcecontrol reset <player>` | Remove all individual packs. |
| `/resourcecontrol get <player>` | Individual, group and temporary packs; client status. |
| `/resourcecontrol gui <player>` | Pack assignment window. Clicking a block gives or removes the pack. |
| `/resourcecontrol preview <player> <pack> [seconds]` | Temporarily enable a pack. Default 30 seconds, maximum 3600. |
| `/resourcecontrol refresh <player>` | Re-send the set to the player. |

The `<player>` argument accepts selectors (`@a`, `@p`). Commands work only with online players.

### Utility

| Command | Action |
|---|---|
| `/resourcecontrol reload` | Reload `config.yml`, `packs.yml`, `players.yml` and re-send sets. |

### GUI

| Block color | Meaning |
|---|---|
| Green | Pack is assigned to the player individually |
| Light blue | Pack is granted through a group |
| Gray | Pack is not assigned |

The window holds 45 packs. The rest are available through commands only.

## Permissions

| Permission | Purpose | Default |
|---|---|---|
| `resourcecontrol.admin` | `/resourcecontrol` commands | operators |
| `resourcecontrol.bypass` | Exempt from the missing-mod disconnect | nobody |
| `resourcecontrol.group.<group>` | Membership in a group pack set | nobody |

## Configuration

`config.yml`:

```yaml
settings:
  language: auto                    # ru, en, auto
  require-client-mod: true          # disconnect players without the mod
  mod-check-timeout-seconds: 10     # time to wait for the mod after join
  allow-shaders: true               # allow packs with core shaders
  max-pack-size-mb: 256             # size limit for pack add / refresh
  group-check-interval-seconds: 5   # set recalculation interval

groups:
  vip:
    - hud-minimal
    - inventory-dark
```

A player with the `resourcecontrol.group.vip` permission receives the packs of group `vip` in addition to
individually assigned ones. LuckPerms example:

```
/lp group vip permission set resourcecontrol.group.vip true
```

Permission changes take effect without relogging within `group-check-interval-seconds`.

If `allow-shaders: false`, packs containing shaders are not sent to clients.

## Plugin data

| File | Contents |
|---|---|
| `packs.yml` | Library: URL, SHA-1, size, priority, flags. |
| `players.yml` | Individual assignments by UUID. |

`packs.yml` may be edited manually. After editing, run `/resourcecontrol reload`.
It is more reliable to obtain SHA-1 and size with the `pack add` and `pack refresh` commands.

```yaml
packs:
  panorama-winter:
    url: https://github.com/<user>/<repo>/releases/download/<tag>/panorama-winter.zip
    sha1: <40 characters>
    size: 1234567
    priority: 0
    persistent: true
    shaders: false
```

## The persistent flag

| Value | Client behavior |
|---|---|
| `true` | The pack is saved and applied on every game launch, including in the main menu. |
| `false` | The pack is active only while connected to the server. |

The main menu panorama changes after the player first connects to a server with such a pack
and then restarts the game. The flag belongs to the pack and applies to everyone who has the pack.

## Priority

Layer order, bottom to top: vanilla resources, vanilla server resource pack,
ResourceControl packs in ascending priority. Equal priorities are ordered by pack name.

## Pack hosting

A direct HTTPS link to a zip file is required. The repository or storage must be public.

### GitHub Releases

1. Create a public repository.
2. Open Releases → Create a new release and set a tag (for example, `panorama-v1`).
3. Attach the pack zip file and publish the release.
4. Link: `https://github.com/<user>/<repo>/releases/download/<tag>/<file>.zip`.

Updating a pack: publish a new release with a new tag and a new link. Replacing a file under the same
tag may cause the CDN cache to serve a stale version and produce a SHA-1 mismatch.
After changing the link in `packs.yml`, run `/resourcecontrol reload` and `/resourcecontrol pack refresh <pack>`.

### raw.githubusercontent.com

Link format: `https://raw.githubusercontent.com/<user>/<repo>/<commit-hash>/<path>.zip`.
Use a commit hash rather than a branch name. File size limit: 100 MB. Git LFS is not supported.

## Pack requirements

- `pack.mcmeta` is located in the archive root, next to the `assets/` directory.
- The `pack_format` value matches version 1.21.11. On `pack add` the plugin prints the value it found.
- Size does not exceed `max-pack-size-mb` and 256 MB (the client's hard limit).

Example paths:

| Purpose | Path |
|---|---|
| Main menu panorama | `assets/minecraft/textures/gui/title/background/panorama_0.png` … `panorama_5.png` |
| Inventory background | `assets/minecraft/textures/gui/container/inventory.png` |
| Hotbar | `assets/minecraft/textures/gui/sprites/hud/hotbar*.png` |

Verify paths against the contents of the 1.21.11 client jar.

## Limitations

- Packs can be assigned only to online players.
- Maximum 32 packs per player. When exceeded, packs with the lowest priority are dropped.
- The GUI shows at most 45 packs.
- Applying a set triggers a single resource reload on the client.
- Core shaders may conflict with Sodium and Iris.

## Building

```
./gradlew build
```

Output: `build/libs/ResourceControlPlugin-<version>.jar`.

## Protocol

Channel `dvmn2:rc_sync` (server → client):

```
VarInt  protocol = 1
Boolean allowShaders
VarInt  count
count × { String name, String url, String sha1, VarLong size, VarInt priority, Boolean persistent }
```

Channel `dvmn2:rc_status` (client → server):

```
VarInt protocol
VarInt state    0 — downloading, 1 — ready, 2 — failed
String message
```

## License

All Rights Reserved.
