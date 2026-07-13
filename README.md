# Deployer Hold

A NeoForge 1.21.1 Create addon that adds **Grip: Pull** and **Grip: Hitch** modes to Deployers.

## What it does

Create Deployers normally have two wrench modes: **Use** and **Attack**. This mod adds two grip modes that latch onto [Create Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) handles across [Sable](https://github.com/ryanhcode/sable) sub-levels.

| Mode | Analogy | Result |
| --- | --- | --- |
| **Grip: Pull** | Player shift-grabbing a handle | Handle’s sub-level is dragged by the Deployer’s sub-level |
| **Grip: Hitch** | Player right-click grab (no shift) | Deployer’s sub-level rides / follows the handle’s sub-level |

While latched, a Sable constraint pulls the **rider** toward the **anchor** and keeps them facing each other (antiparallel). Twist around that facing axis stays free.

## Requirements

- Minecraft 1.21.1 + NeoForge
- Create 6.0.9–6.0.x
- Sable 2.0.x
- Create Simulated 1.x

## Usage

1. Place a Deployer on one Sable sub-level and a Simulated handle on another.
2. Point the Deployer at the handle (2–3 blocks ahead).
3. Wrench the Deployer’s front face to cycle modes.
4. Power the Deployer. Its arm extends, latches the handle, then retracts while the grip stays active.
5. Moving either linked structure follows the chosen polarity (Pull vs Hitch).
6. Redstone-lock the Deployer to release the grip.

Wrench cycle: **Use → Grip: Pull → Grip: Hitch → Attack → Use**.

Engineer’s Goggles show the grip mode and latch line:

| Latch | Meaning |
| --- | --- |
| Open | No handle in tip range |
| Handle in range | Ready to latch on the next extend |
| Latched | Currently holding a specific handle |
| Target locked | Physics Staff lock on the side that would move |

Latch state is saved with the Deployer. After leaving and rejoining the world, the same handle BlockPos is restored once that chunk / sub-level plot is loaded again (one sub-level can have many handles — the grip tracks the handle position, not the sub-level).

Server options (grab range, tip geometry, constraint stiffness) live in Create → Mod Config → Access Configs of other Mods → Deployer Hold (`config/deployerhold-server.toml`).

Ponder the Deployer item for an in-game overview of Grip: Pull vs Grip: Hitch.

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## Build

```bash
./gradlew test
./gradlew build
./gradlew runClient
```

CI on `main` and pull requests runs resource validation, unit tests, a full Gradle build, and jar contents checks.

## License

MIT
