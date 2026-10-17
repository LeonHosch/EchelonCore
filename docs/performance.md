# Performance design and profiling

EchelonCore owns the gameplay-specific costs below. The sibling CrazyWorldProgression repository has `docs/performance.md` for the portal, script, cleanup, and preview framework. The cadences here describe code behavior, not measured tick-time savings. A single-player world runs these server callbacks on its integrated server.

## Server work

| System | Current behavior | Reason and remaining cost |
| --- | --- | --- |
| [Veil boundary enforcement](../src/main/java/ekuzo/echeloncore/veil/anomaly/VeilAnomalyService.java) | Checks players each server tick against [64-block horizontal spatial-index cells](../src/main/java/ekuzo/echeloncore/veil/anomaly/VeilAnomalySpatialIndex.java) | Boundary enforcement needs timely collision response; the grid narrows candidates instead of testing every anomaly for every player. Dense overlapping anomalies still increase query cost. |
| [Veil anomaly rebuild](../src/main/java/ekuzo/echeloncore/veil/anomaly/VeilAnomalyService.java) | Checks every 20 ticks and returns when Veil Level and projection specifications have not changed | Avoid rebuilding indexes and resending resolved state in a stable world. Projection enumeration still occurs during the change check. |
| [Veil damage and radii](../src/main/java/ekuzo/echeloncore/veil/VeilManager.java) | Checks players every 20 ticks; radii sync follows a level change | Damage rules have second-level cadence; avoid per-tick distance and packet work. |
| [Advancements and rewards](../src/main/java/ekuzo/echeloncore/advancement/EchelonAdvancementManager.java) | Evaluates six categories for each connected player every 20 ticks, then [reconciles completed rewards](../src/main/java/ekuzo/echeloncore/advancement/EchelonAdvancementRewardService.java) | Persistent Minecraft statistics let existing players receive earned milestones. The repeated per-player checks may grow with player count; profile before replacing reconciliation with event hooks. |

The [client Veil renderer](../src/client/java/ekuzo/echeloncore/veil/anomaly/VeilAnomalyRenderer.java) caches geometry between frames and rebuilds it when geometry fields change. Visuals run on the client; server-side boundary and interaction rules remain authoritative.

## What to measure for 1.0

Measure worst server ticks during large raid/portal activity, many players crossing anomaly boundaries, Veil Level changes that rebuild projected anomalies, and advancement reconciliation with several established players. Record player count, anomaly count and overlap, projected-instance count, and client frame time separately. Compare integrated single player with a dedicated server. Use profiler samples to decide whether a path needs more caching or batching; no live benchmark is recorded here yet.

When modifying spatial indexes, test boundary and interaction behavior at cell edges and with overlapping anomalies. When modifying advancement cadence, verify late joins and previously earned statistics still receive rewards.
