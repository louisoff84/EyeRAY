# EyeRAY

EyeRAY is a dependency-free, server-side anti-XRay plugin inspired by Orebfuscator. It obfuscates valuable blocks only for the Minecraft client: the real world data is never replaced or modified.

## Compatibility

**One universal JAR supports Minecraft/Bukkit/Spigot/Paper from 1.8.8 through Paper 26.2.**

- The plugin is compiled to Java 8 bytecode so legacy 1.8.8 servers can load it.
- The build compiles against the Spigot 1.8.8 API to prevent accidental use of newer-only Bukkit methods.
- CI also compiles the same source against Paper 26.2 to catch APIs removed on modern servers.
- `api-version` is intentionally omitted so the same JAR can load on pre-1.13 servers. Modern Paper will report EyeRAY as a legacy plugin; this is expected for the universal build.
- Materials that do not exist on the running version are ignored automatically.
- Modern negative world heights are detected at runtime while old worlds safely use Y=0 as their minimum.

### Server Java

EyeRAY itself is Java 8 bytecode. Use the Java version required by your Minecraft server:

- Old 1.8.8-era servers can run EyeRAY on Java 8.
- Modern Paper versions use their own newer Java requirement (Paper 26.2 currently uses Java 25).

## Features

- Hides ores and Ancient Debris behind Stone, Deepslate or Netherrack client-side.
- Supports old `QUARTZ_ORE` and modern `NETHER_QUARTZ_ORE` naming.
- Proximity reveal: the real block is restored before a legitimate player reaches it.
- Optional deterministic fake-ore decoys to make XRay views noisy.
- Per-chunk scan cache and a configurable scan budget to reduce tick spikes.
- Does not force-load chunks.
- Restores client blocks when disabled/reloaded.
- Invalidates caches after breaks, placements, entity explosions and pistons.
- World whitelist/blacklist.
- `eyeray.bypass` permission.
- Admin commands and runtime statistics.
- GitHub Actions build artifact on every push/PR.
- GitHub Release automation with the compiled JAR attached.

## Build

```bash
gradle clean build
```

The universal plugin JAR is generated in `build/libs/EyeRAY-<version>.jar`.

## Commands

- `/eyeray status`
- `/eyeray reload`
- `/eyeray toggle`
- `/eyeray rescan [player]`

Aliases: `/eray`, `/antixray`

## Permissions

- `eyeray.admin` - manage EyeRAY (default: OP)
- `eyeray.bypass` - bypass all obfuscation

## How it works

EyeRAY scans already-loaded chunks around each protected player and caches positions that should be obfuscated. It then uses Bukkit client block changes (`Player#sendBlockChange`) to send a fake view without changing the server's real block state.

Real protected ores are replaced visually with their surrounding base rock. Optional decoy positions do the inverse and display fake ore inside solid rock. When the player moves inside the configured reveal distance, EyeRAY sends the real block back to the client.

The universal build deliberately avoids NMS and version-specific packet classes. It uses the legacy `sendBlockChange(Location, Material, byte)` API because that method exists on 1.8.8 and remains available on Paper 26.2.

## Performance

Defaults are conservative: a 3x3-chunk protection area, one uncached chunk scan per tick, and a vertical scan range from Y -64 to 128. On old servers the lower bound is automatically clamped to Y=0. Larger chunk radii or scan ranges increase CPU cost. Use `/eyeray status` to monitor the cache and queue.

## Releases

The `Release EyeRAY` GitHub Action builds the universal JAR and creates/updates a GitHub Release for the version declared in `build.gradle`.

## License

Copyright (c) 2026 louisoff84. All rights reserved unless a separate license is added to this repository.
