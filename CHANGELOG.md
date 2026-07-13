# Changelog

All notable changes to Deployer Hold are documented here.

## 0.1.0 — 2026-07-12

### Added

- **Grip: Pull** and **Grip: Hitch** Deployer wrench modes for latching [Create Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) handles across [Sable](https://github.com/ryanhcode/sable) sub-levels
- Latch lifecycle: extend → grab → retract while constrained → redstone-lock to release
- Facing-alignment constraint (pitch/yaw toward each other; twist around facing stays free)
- Engineer's Goggles lines for grip mode and latch status (Open / Handle in range / Latched / Target locked)
- Latch persistence by handle `BlockPos` (+ sub-level UUID when available) across world leave/rejoin
- Server config via Create Mod Config UI (`config/deployerhold-server.toml`): grab range, tip geometry, constraint stiffness/damping
- Ponder scene on the Create Deployer explaining Grip: Pull vs Grip: Hitch
- Unit tests and CI for resource validation, JVM tests, and jar integrity

### Notes

- Requires Minecraft 1.21.1, NeoForge, Create 6.0.9+, Sable 2.0.x, Create Simulated 1.x
- Wrench cycle: Use → Grip: Pull → Grip: Hitch → Attack → Use
