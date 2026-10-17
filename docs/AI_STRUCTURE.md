# EchelonCore structure — AI context

Snapshot of the checked-in source on 2026-10-16. This repository is the content mod. Its Gradle `settings.gradle` includes sibling `../CrazyWorldProgression` as `:crazy-world-progression`; the CWP dependency is also declared in `fabric.mod.json`. See [AI_FEATURES.md](AI_FEATURES.md) for behavior.

## Entry points and startup

| Path | Responsibility |
| --- | --- |
| `src/main/resources/fabric.mod.json` | Mod ID, dependency, common/client entry points, mixin config. |
| `src/main/java/ekuzo/echeloncore/EchelonCore.java` | Registers currencies, CWP YAML sources, king purchase policy, admin module, Veil/advancement services, and commands. |
| `src/client/java/ekuzo/echeloncore/client/EchelonCoreClient.java` | Initializes world Veil, anomaly snapshot client, and anomaly rendering. |
| `build.gradle`, `settings.gradle`, `gradle.properties` | Fabric build and sibling CWP composite dependency. |

## Source map

| Location under `src/main/java/ekuzo/echeloncore/` | Role |
| --- | --- |
| `admin/` | CWP admin module pages and actions. |
| `advancement/` | Server milestone evaluation and one-time EP reward reconciliation. |
| `command/` | `/king` and `/veil` command trees. |
| `king/` | King service and world-persistent king data. |
| `progression/` | Powerful Soul claim integration with CWP. |
| `veil/` | Global Veil Level, per-dimension/level radius persistence, client synchronization, and distance damage. |
| `veil/anomaly/` | Definition, persistence, spatial indexes, movement/block enforcement, and network snapshots. |
| `mixin/` | Filters advancement definitions during server reload. |

Client-specific code lives under `src/client/java/ekuzo/echeloncore/veil/` and `veil/anomaly/`. The client renders and predicts visual/interactions; the server remains authoritative for purchases, claims, anomaly enforcement, and persistence.

## Resource map

| Path under `src/main/resources/` | Role |
| --- | --- |
| `skilltrees/kingdom-progression.yml` | Global king-gated tree. |
| `skilltrees/a-new-beginning.yml` | Personal progression tree. |
| `skilltrees/player-paths.yml` | Personal mutually exclusive paths. |
| `baseline-stats.yml` | Current starting stat expressions. |
| `random-events.yml` | Event definitions and RP blackout window. |
| `data/echelon-core/advancement/` | Six advancement categories; JSON definitions. |
| `assets/echelon-core/` | English/German strings and currency icons. |
| `echelon-core.mixins.json` | Server mixin configuration. |

## Where to make a change

- New currency: define and register it in `EchelonCore.java`; generic balances and commands belong to CWP.
- New tree or node: edit/add YAML under `skilltrees/`; preserve IDs for existing saved worlds. Extend CWP's parser only for a reusable schema or mechanic.
- New stat expression: register it before tree loading, usually in Echelon's `VeilManager` for Echelon-specific stats or CWP's `SkillStatRegistry` for generic stats.
- New event: add YAML to `random-events.yml` and register an Echelon lifecycle handler through CWP's `RandomEventRegistry` for actual effects.
- New advancement: add the JSON, server condition in `EchelonAdvancementManager`, and reward mapping in `EchelonAdvancementRewardService` if it should pay EP.
- New anomaly behavior: update server definition/service and client snapshot/renderer together; maintain server-side enforcement.
- New world Veil behavior: keep `VeilManager`, saved radius data, radius networking/client cache, and `VeilRenderer` consistent. Missing radius entries must remain veil-free.
- New admin control: add it in `EchelonAdminModule` through CWP's data-driven `AdminPanelRegistry`.

Build from this repository with `.\gradlew.bat build --offline --no-daemon` when dependencies are cached. Compile/package checks do not establish in-game behavior; use `:runClient` or `:runServer` for runtime work. Inspect `git status` in **both** repositories before editing because the composite build spans both checkouts.
