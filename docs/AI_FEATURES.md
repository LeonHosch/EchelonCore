# EchelonCore features — AI context

Snapshot of the checked-in implementation on 2026-10-16. EchelonCore (`echelon-core`, version `1.0.1`) is the Echelon server's content mod for Minecraft 26.2/Fabric/Java 25. It requires Crazy World Progression (CWP). Read [AI_STRUCTURE.md](AI_STRUCTURE.md) for code locations and [../README.md](../README.md) for player and administrator commands. When these summaries differ from code or YAML, the implementation is authoritative.

## Implemented systems

| Feature | Current behavior | Main source |
| --- | --- | --- |
| Currencies | Registers global Kingdom Points (KP) and player Echelon Points (EP), Fakhrul Currency (FC), and Powerful Souls (PS). CWP supplies balances, persistence, generated commands, and skill costs. | `EchelonCore.java`; CWP `currency/` |
| Skill trees | Ships `kingdom-progression` (global), `a-new-beginning` (personal), and `player-paths` (personal). CWP parses YAML and manages unlocks. The kingdom tree has a king-only purchase policy. `player-paths` demonstrates permanent branch exclusion through `following: 1`. | `src/main/resources/skilltrees/`; `EchelonCore.java` |
| Baseline stats | Current YAML contains `addHealth(-8)`, `addMiningSpeed(-20)`, and `addMovementSpeed(-10)`. CWP applies and displays the baseline independently of purchased skills. | `src/main/resources/baseline-stats.yml` |
| King | Persists one appointed player UUID. Appointing or clearing the king changes purchase permission, without resetting global tree unlocks or currencies. | `king/`; `command/KingCommands.java` |
| Advancements and EP rewards | Filters loaded advancement definitions to the `echelon-core` namespace. Checks mining, woodworking, mysteries, monster hunting, farming, and fishing milestones from server state and vanilla statistics once per second. Completed configured milestones pay a one-time EP reward through a claim ledger; tiers are 1, 3, 5, or 15 EP. | `advancement/`; `mixin/ServerAdvancementManagerMixin.java`; `src/main/resources/data/echelon-core/advancement/` |
| Powerful Soul claims | `PowerfulSoulService.claim` uses CWP's claim ledger to award one PS for a unique claim. Callers supply the gameplay event ID and lifetime limit; this service does not select boss or event triggers. | `progression/PowerfulSoulService.java` |
| World Veil | Persistent inner/outer radii are keyed by dimension and exact Veil Level. Missing pair means no world Veil in that dimension at that level. The overworld uses its spawn as center; other dimensions use `(0, 0)`. Beyond the inner radius deals 2 magic damage every 5 seconds; beyond outer deals 5 each second. | `veil/VeilManager.java`, `VeilRadiusData.java`, `VeilRadiusNetworking.java`; client `veil/VeilRenderer.java` |
| Veil anomalies | Persistent sphere, cylinder, cube, and pyramid definitions in any dimension; level thresholds control collision, visibility, and block access. `keep_out` and `keep_in` choose the one-way boundary rule. Server movement and block checks use spatial indexes; clients receive snapshots and render the boundaries. | `veil/anomaly/`; client `veil/anomaly/`; `command/VeilCommands.java` |
| Admin pages | Adds Overview, Kingdom, Veil, Anomalies, and Advancements pages to CWP's `/cwp admin` panel. Veil page adds/removes dimension and level radius pairs. | `admin/EchelonAdminModule.java` |
| Random-event schedule | Registers Echelon's YAML with CWP. Current file has a Friday 12:00–Saturday 00:00 Europe/Berlin blackout and `events: []`. No Blood Moon, Rift, or other event effect is currently defined there. | `src/main/resources/random-events.yml` |

## Ownership and boundaries

- EchelonCore owns its four currency definitions, YAML content, king policy, advancements, Powerful Soul award integration, and Veil rules/rendering.
- CWP owns generic currency storage and commands; YAML loading/validation; skill unlocks and UI; baseline application; event scheduling; portal lifecycle; and the reusable admin UI.
- EchelonCore currently supplies no portal templates or placed portals. Its future portal gameplay should use CWP's `PortalRegistry` and services.
- Keep stable namespaced currency, tree, and node IDs when changing existing worlds. Stored balances, claims, and unlocks refer to them.

## Useful implementation paths

- Content: `src/main/resources/skilltrees/*.yml`, `baseline-stats.yml`, `random-events.yml`.
- Advancement definitions: `src/main/resources/data/echelon-core/advancement/<category>/*.json`.
- Server code: `src/main/java/ekuzo/echeloncore/`.
- Client code: `src/client/java/ekuzo/echeloncore/`.
- Framework source: sibling `../CrazyWorldProgression/` checkout.
