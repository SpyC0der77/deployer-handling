# Deployer Hold

A NeoForge 1.21.1 Create addon that adds a third **Hold** mode to Deployers.

## What it does

Create Deployers normally have two wrench modes: **Use** and **Attack**. This mod adds **Hold**.

In Hold mode, a Deployer on Sable sub-level A can grab a [Create Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) handle on sub-level B. While holding, movement of the Deployer's sub-level pulls the handle (and its sub-level) with it via a Sable physics constraint — the same approach Simulated uses for player handle grabs.

## Requirements

- Minecraft 1.21.1 + NeoForge
- Create 6.0.9+
- Sable 2.0+
- Create Simulated 1.0+

## Usage

1. Place a Deployer on one Sable sub-level and a Simulated handle on another.
2. Point the Deployer at the handle (2–3 blocks ahead).
3. Wrench the Deployer's front face until goggles show `Mode: Hold`.
4. Power the Deployer. Its arm extends, grabs the handle, and stays extended while the grip holds.
5. Moving sub-level A moves the held handle / sub-level B with it.
6. Redstone lock or stopping rotation releases the grip.

Wrench cycle: **Use → Hold → Attack → Use**.

## Build

```bash
./gradlew build
./gradlew runClient
```

## License

MIT
