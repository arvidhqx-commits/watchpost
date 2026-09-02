# WatchPost

**Security cameras and monitors — and nobody ever gets stuck in spectator mode.**

---

Security cameras and monitors for Paper servers — and nobody ever gets stuck in
spectator mode.

Tested on **Paper 1.21.11 and Paper 26.2**. One file, no dependencies, no NMS.

## Why this exists

[Security Camera Plugin - CCTV](https://www.spigotmc.org/resources/60310/) is the
reference plugin for this job — **197,532 downloads**, 4.2 stars, 82 ratings. Its
last release is from **November 2024**. We downloaded that release and started it
ourselves on both current Paper versions. It fails to enable on **both**:

```
java.lang.NoSuchMethodError:
  'com.mojang.authlib.properties.PropertyMap com.mojang.authlib.GameProfile.getProperties()'
    at io.github.tanguygab.cctv.utils.Heads.createSkull(Heads.java:53)
    at io.github.tanguygab.cctv.CCTV.onEnable(CCTV.java:74)
```

It did not die in its camera logic. It died building a decorative player head
through `com.mojang.authlib` — a Mojang-internal class that is not part of the
Bukkit API and changes without notice.

WatchPost is built so that this failure mode cannot happen:

* **no `com.mojang.authlib`, no NMS, no reflection into the server**
* **no player heads** — menu icons are ordinary items, looked up by name at
  runtime with a fallback, so a renamed or removed material cannot stop the plugin
* **no entities** — CCTV spawns an armor stand and a creeper per camera.
  A WatchPost camera is a row in a file. Nothing to lag, leak or clean up.
* **the server version string is never read or parsed**

## The guarantee no other camera plugin gives

Looking through a camera means being put into spectator mode somewhere else. The
expensive failure is obvious once you have seen it: the server restarts while
someone is watching, and that player logs back in as a spectator, inside a wall,
with no idea where they came from.

WatchPost writes the way back **to disk before it touches the player**, and there
is exactly one code path that reads it back:

| what happens | what WatchPost does |
|---|---|
| player sneaks or types `/watchpost leave` | position, game mode, flight and night vision restored |
| the view times out | same |
| the player disconnects while watching | entry stays on disk, restored on next login |
| the server crashes or is killed | restored on next login |
| the plugin is reloaded or disabled | every open session is ended first |
| the camera's world is unloaded | session ends, player returns |
| someone else changes their game mode | WatchPost lets go instead of fighting for control |
| the return world no longer exists | player goes to the main spawn, with a warning in the console |

Flight permission and flight state are part of that record, so a `/fly` granted by
another plugin survives the camera. Fall distance is reset on return.

## Features

* `/watchpost camera create <name>` — a camera at your eyes, aimed where you look
* Camera groups, and monitor blocks that open a group as a menu
* Right-click a monitor block, click a camera, look around; sneak to leave
* Mouse wheel switches to the next camera in the group
* Per-monitor access lists, or public monitors
* Optional night vision while watching (removed on exit — but only if WatchPost
  added it)
* Commands are blocked during the view, so nobody teleports out of a camera
  into a half-restored state
* Time limit per view (5 minutes by default, `0` disables it)
* A camera placed inside a solid block is reported immediately, instead of showing
  a black picture to the first viewer

## Coming from CCTV?

```
/watchpost import
```

Reads `plugins/CCTV/cameras.yml`, `cameragroups.yml` and `computers.yml`, and
never writes to them. Whatever cannot be taken over is named, not swallowed:
missing worlds, cameras a group refers to that do not exist, entries that are not
cameras at all, and the custom heads (`skin`) — WatchPost has no player heads,
which is the whole reason it still starts.

## Commands

| command | what it does |
|---|---|
| `/watchpost camera create\|delete\|list\|enable\|disable [name]` | manage cameras |
| `/watchpost group create\|delete\|add\|remove\|list [group] [camera]` | manage groups |
| `/watchpost monitor create <group>` / `delete` | bind the block you are looking at |
| `/watchpost view <name>`, `next`, `prev`, `leave` | watch |
| `/watchpost import` | take over a CCTV installation |
| `/watchpost reload` | re-read config and data |

Aliases: `/cams`, `/cctv`.

Permissions: `watchpost.monitor.use` (default true), `watchpost.view`,
`watchpost.camera.create`, `watchpost.camera.delete`, `watchpost.camera.list`,
`watchpost.group.manage`, `watchpost.monitor.create`, `watchpost.admin` (op).

## What WatchPost does not do, and will not pretend to

* **Spectator mode sees through walls.** That is how Minecraft works, and every
  server-side camera plugin has it. Deciding who may place a camera is therefore
  a trust decision — `watchpost.camera.create` is op-only for a reason. There is
  no recording, no motion detection and no rendering of a camera picture onto a
  screen; those need a client mod or a resource pack.
* No camera heads or decorative models. See above: that is exactly what killed
  the plugin this one replaces.
* One monitor shows up to 54 cameras.

## Development note

This plugin was developed with AI assistance (human-led: requirements, testing and
release decisions by the maintainer). It is tested on live Paper servers before
every release — the run for 0.1.0 asserted 44 runtime properties against the
shipped classes on Paper 1.21.11 and Paper 26.2, alongside 15 other plugins, with
zero errors.

MIT licensed.
