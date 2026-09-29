# Litematica Printer 

A high-performance, rotation-accurate printer designed for integration into custom Minecraft utility clients, Fabric mods, or standalone forks working alongside **Litematica**.

> **Note:** This repository is **not a standalone `.jar` mod**. It contains core logic and packet handling designed to be adapted, hooked into an event bus, and integrated into your own client-side utility mod or custom build.

---

## Showcase

https://github.com/user-attachments/assets/69ca2089-bcb6-4149-82a2-062e4a4c22c1

---

## Overview

This module provides the packet handling, rotation logic, and tick-window batching needed to push schematic placement to the theoretical limits allowed by vanilla interaction rates and modern anti-cheat frameworks.

### Key Capabilities

- **Air-Placement**  
  Uses airplacements to place blocks without needing any neighboring blocks

- **Dynamic Interaction-Limit**  
  The placement pipeline dynamically checks whether a target block is orientation-dependent and throttles throughput on the fly:
  - **Standard Blocks (1.5 Blocks/Tick):** Batches placements within the configured tick delay, pausing through the remainder of the window to saturate the vanilla packet interaction ceiling without overflow flags.
  - **Rotation-Dependent Blocks (1 Block/Tick):** Because orientation requires explicit look-angle synchronization, the routine automatically dials down to **1 placement per tick** for directional blocks (pistons, stairs, observers, logs) to ensure server-side angle resolution stays completely consistent.

- **100% Directional & Rotation Accuracy**  
  Calculates required yaw, pitch, and block-hit vectors on dispatch so directional blocks place correctly on the first attempt:
  - Preserves vertical/horizontal quadrant states for stairs, slabs, trapdoors, etc.
  - Aligns directional faces for pistons, observers, droppers, hoppers, logs, etc.

- **GrimAC Compatibility**  
  Constructs interaction sequences aligned with GrimAC's raycast and reach validation checks:
  - **Clean Angle Simulation:** In `Simulation` mode, the client simulates server-side look vectors rather than sending abrupt, raw rotation packets, satisfying Grim's prediction engine without triggering rotation flags.
  - **Zero-Rotation Air Placement:** Because air-placements do not target a physical block face, they bypass orientation checks entirely. The routine skips look-vector calculations for these blocks.
---

## Settings & Descriptions

| Setting | Default | Range / Modes | Description |
|---|---|---|---|
| **PrintingRange** | `5` | `1` – `10` | Maximum reach distance for placement packet validation. |
| **PrintingDelay** | `6` | `0` – `20` | Delay between printing blocks in ticks. Dynamically caps `RotationDelay` and `NormalPlaceDelay`. |
| **RotationDelay** | `1` | `0` – `PrintingDelay` | Delay after printing a rotation-dependent block. Forces tick spacing for proper angle resolution. |
| **NormalPlaceDelay** | `0` | `0` – `PrintingDelay` | Delay for blocks that cannot be placed via air-placement. |
| **BlocksPerTick** | `9` | `1` – `10` | Maximum number of blocks to place per tick. |
| **Grim3AirPlace** | `false` | Boolean | Specialized air-place handling for Grim v3 environments. |
| **Swap** | `Inventory` | `Silent`, `Inventory` | `Silent` client accesses hotbar only. `Inventory` client accesses the whole inventory. |
| **PauseOnEat** | `true` | Boolean | Suspends placement packets while actively consuming food. |
| **PauseOnMove** | `false` | Boolean | Pauses placement while the player is in motion. |
| **Rotation** | `Simulation` | `Packet`, `Simulation` | Client-side angle spoofing mode (`Packet` exposes `PauseOnRotate`). |
| **StrictRedstone** | `false` | Boolean | If enabled, forces player position to align with vanilla requirements for redstone type blocks. |
| **FirstSortMode** | `Nearest` | Sorting modes | Primary queue sort prioritization by player proximity. |
| **SecondSortMode** | `Nearest` | Sorting modes | Secondary queue sort prioritization. |
| **Render** | `true` | Boolean | Displays visual placement overlays. |

---

## Integration Guide

Because this repository provides raw logic rather than a compiled standalone mod, you will need to wire it into your client's tick loop, rotation system, and mixin environment.
