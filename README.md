# Deployer Hold

A NeoForge 1.21.1 Create addon that adds **Grip: Pull** and **Grip: Hitch** modes to Deployers.

## What it does

Create Deployers normally have two wrench modes: **Use** and **Attack**. This mod adds two grip modes that latch onto [Create Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) handles across [Sable](https://github.com/ryanhcode/sable) sub-levels.

| Mode | Analogy | Result |
| --- | --- | --- |
| **Grip: Pull** | Player shift-grabbing a handle | Handle’s sub-level is dragged by the Deployer’s sub-level |
| **Grip: Hitch** | Player right-click grab (no shift) | Deployer’s sub-level rides / follows the handle’s sub-level |

## Requirements

- Minecraft 1.21.1 + NeoForge
- Create 6.0.9–6.0.x
- Sable 2.0.x
- Create Simulated 1.x

## Usage

1. Place a Deployer on one Sable sub-level and a Simulated handle on another.
2. Point the Deployer at the handle (2–3 blocks ahead).
3. Wrench the Deployer’s front face to cycle modes.
4. Power the Deployer. Its arm extends, grabs the handle, and stays extended while the grip holds.
5. Moving either linked structure follows the chosen polarity.
6. Redstone lock or stopping rotation releases the grip.

Wrench cycle: **Use → Grip: Pull → Grip: Hitch → Attack → Use**.

## Build

```bash
./gradlew test
./gradlew build
./gradlew runClient
```

CI on `main` and pull requests runs resource validation, unit tests, a full Gradle build, and jar contents checks.

## License

MIT
