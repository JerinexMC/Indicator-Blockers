# IndicatorBlocker (Paper 1.21.11)

Server-side packet sanitizer for **Paper 1.21.11** that neutralizes client-side:
- **Health & Absorption Indicators** (reports a static `20.0` HP and `0.0` absorption for living opponents)
- **Opponent Armor & Item Durability Indicators** (strips the `minecraft:damage` item component from outgoing equipment packets)
- **Opponent Potion Status Effect Indicators & Particles** (blocks `ENTITY_EFFECT` / `REMOVE_ENTITY_EFFECT` packets and potion swirl particles for other players while keeping your own effects 100% intact)

## Requirements
- **Paper 1.21.11** (Java 21)
- **PacketEvents** (`packetevents-spigot` 2.11.2+ installed in `/plugins`)

## Building with GitHub Actions
1. Push all files in this folder to a GitHub repository.
2. Open the **Actions** tab and run **Build IndicatorBlocker** (or push to `main`).
3. Download the compiled `IndicatorBlocker-1.0.0.jar` from the workflow **Artifacts**.
