# Modrinth release kit

Everything needed to publish EchelonCore on Modrinth. Section 1 is the project page body (paste it into the description editor), section 2 the version changelog, section 3 the fields to fill in.

---

## 1. Project page

**Summary (the one line under the title, max 120 characters):**

> The Echelon RPG server's content: world bosses, boss arenas, a kingdom and its king, the Veil, armor, mounts and pets.

**Description:**

```markdown
# Echelon Core

**Echelon Core** is the content mod of the Echelon RPG server, built on [Crazy World Progression](https://modrinth.com/mod/crazy-world-progression). CWP brings the framework (skill trees, item quality, MobForge, loot pools, the admin panel); Echelon Core fills it with a world: a kingdom ruled by one player, a Veil that closes in on the edge of the map, bosses with their own mechanics, and gear to hunt for.

> Echelon Core is made for the Echelon server first. You can run it on your own server, but expect Echelon's rules, currencies and progression rather than a general-purpose content pack.

## Bosses and monsters

- **The Hollowed Penitent**: a world boss for 3–4 players (a very skilled player can solo him). A man the void hollowed out from the face inwards; all he has left is sorrow. Claw combos, a silent wail you can interrupt, a mourning kneel that opens a burst window, a lunge at whoever kites him, a grab your friends must break, void tar pools and two enraged phases.
- **The Leshen**: a forest spirit that shadow-steps around you, sends crow swarms, roots you in place, slams the ground and calls shadow wolves when it is losing.
- **The Falsekin**: a skin-thief that hides among villagers and animals, haunts the player it picked, and fights with shed skins, stolen voices and its tongue once found out.
- **Corrupted Stag** and the **Void Wraith**, which hovers and fires beams through walls.

All of them are animated GeckoLib models, and their abilities are MobForge abilities any MobForge mob can use.

## Boss encounters

Admins place boss encounters in the world from the admin panel: an area (sphere, cylinder, cube or pyramid), one or more bosses and the loot pools they pay out.

- The bosses wake up only when you come close or hit them, each with its own boss bar.
- Players outside the area cannot hurt them, and nobody can build or break blocks inside.
- Leave a running fight and the area shuts for you until the encounter resets.
- When nobody is left inside, the bosses reset to full health.
- Everyone inside when the last boss falls gets the loot; the bosses return after a respawn time.
- With JourneyMap, encounters you have been near appear on your map with the boss's picture, greyed out with a respawn timer while they are dead.

## World event: the Fallen Star

A star crashes into the world and leaves a burning core of ancient debris. Players who come to the crater take part:

- **Unbearable Heat** builds up the longer you stay near the core, up to setting you on fire.
- Waves of guards, Void Wraiths among them, keep coming and go for whoever is mining the core first. More players means more guards.
- Mine the core out to end the event. The debris drops for whoever mines it, so it can be fought over.
- Everyone still taking part at the end gets the event's loot.

## The kingdom and the Veil

- **The king**: one appointed player who alone can unlock the kingdom's global skill tree for everyone.
- **The Veil**: a world border per dimension and Veil level that hurts whoever goes beyond it, and grows as the kingdom progresses. The map is fogged over beyond it.
- **Veil anomalies**: hand-placed shapes that keep players out (or in), visible or hidden, with protected blocks.
- **Kingdom areas**: safe havens around the kingdom.

## Progression

- Four currencies: Kingdom Points, Echelon Points, Fakhrul Currency and Powerful Souls
- Skill trees: the kingdom's tree, a starting tree and branching player paths
- Echelon advancements for mining, woodworking, farming, fishing, monster hunting and mysteries, paying Echelon Points
- Soulforging: CWP's reforging, Echelon style

## Gear

Armor sets with their own set effects and models: Pathfinder, Guardian of Life, Martyr's Remorse, Icarus' Folly, Bloodletter's Pact and Stormcaller, plus rings, necklaces and charms, and the Oath of the Lifebound.

## Mounts and pets

- Mounts: the Corrupted Stag, the Shainicorn (with a speed boost), a Zombie Horse, a Camel Husk and a Zoglin
- Pets: the Little Leshen, a Mini Void Wraith, a Bubble Fish and many mini versions of vanilla mobs

## Requirements

- Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API, Java 25
- [Crazy World Progression](https://modrinth.com/mod/crazy-world-progression) 0.6.0+ and GeckoLib 5.5.5+
- Install on both the server and the client
- Optional: JourneyMap (encounters, portals and kingdom areas on the map)

Echelon Core is in beta (0.x). Expect changes before 1.0.
```

---

## 2. Changelog (version 0.6.0)

```markdown
- Now on Crazy World Progression 0.6.0
- Boss spawn points can be shown and moved with CWP's helper tools (F8/F9 in creative)
```

## Changelog (version 0.5.0)

```markdown
First public release, on Crazy World Progression 0.5.0.

- New world boss: The Hollowed Penitent
- World event: the Fallen Star
- Boss encounters: placed boss arenas with boss bars, lockout, resets, loot pools and JourneyMap markers
- The Void Wraith
- The Leshen, the Falsekin and the Corrupted Stag
- Armor sets, accessories, mounts and pets
- The king, the Veil, Veil anomalies and kingdom areas
- Echelon currencies, skill trees and advancements
```

---

## 3. Fields

| Field | Value |
| --- | --- |
| Name | Echelon Core |
| Slug | echelon-core |
| Project type | Mod |
| Categories | Adventure, Equipment, Game Mechanics (+ Mobs) |
| Environment | Client and server: required on both |
| License | MIT |
| Source | https://github.com/LeonHosch/EchelonCore |
| Version number | 0.6.0 |
| Release channel | Beta |
| Loader | Fabric |
| Game version | 26.2 |
| File | `echelon-core-0.6.0.jar` (not the `-sources` jar) |
| Dependencies | Crazy World Progression (required), Fabric API (required), GeckoLib (required), JourneyMap (optional) |
