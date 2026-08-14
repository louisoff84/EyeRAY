# EyeRAY

EyeRAY is a server-side anti-XRay plugin for modern Paper servers. It obfuscates valuable blocks only for the Minecraft client: the real world data is never replaced or modified.

## Features

- Hides ores and Ancient Debris behind Stone, Deepslate or Netherrack client-side.
- Proximity reveal: the real block is restored before a legitimate player reaches it.
- Optional deterministic fake-ore decoys to make XRay views noisy.
- Per-chunk scan cache and a configurable scan budget to reduce tick spikes.
- Does not force-load chunks.
- Restores client blocks when disabled/reloaded.
- Invalidates caches after breaks, placements, explosions and pistons.
- World whitelist/blacklist.
- `eyeray.bypass` permission.
- Admin commands and runtime statistics.
- GitHub Actions build artifact on every push/PR.

## Requirements

- Paper 26.2+
- Java 25+

Paper 26.2 uses the new Paper versioning scheme and requires Java 25.

## Build

```bash
gradle clean build
```

The plugin JAR is generated in `build/libs/`.

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

Real protected ores are replaced visually with their surrounding base rock. Optional decoy positions do the inverse and display fake ore inside solid rock. When the player moves inside the configured reveal distance, EyeRAY immediately sends the real block state.

This architecture is intentionally dependency-free and avoids NMS/version-specific packet code.

## Performance

Defaults are conservative: a 3x3-chunk protection area, one uncached chunk scan per tick, and a vertical scan range from Y -64 to 128. Larger chunk radii or scan ranges increase CPU cost. Use `/eyeray status` to monitor the number of cached/queued chunks.

## License

Copyright (c) 2026 louisoff84. All rights reserved unless a separate license is added to this repository.
