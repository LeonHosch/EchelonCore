# AI context for EchelonCore and Crazy World Progression

Use this file as the starting point when supplying these two repositories to an AI coding assistant. The summaries below describe the source checked on 2026-10-16. Read the relevant source before changing behavior; current code and YAML take precedence over prose.

## Read in this order

1. [EchelonCore features](docs/AI_FEATURES.md) — what the Echelon content mod currently does.
2. [EchelonCore structure](docs/AI_STRUCTURE.md) — entry points and file map.
3. [CWP features](../CrazyWorldProgression/docs/AI_FEATURES.md) — reusable framework capabilities.
4. [CWP structure](../CrazyWorldProgression/docs/AI_STRUCTURE.md) — framework packages and extension points.
5. Detailed references as needed: [Echelon README](README.md), [CWP README](../CrazyWorldProgression/README.md), [CWP architecture](../CrazyWorldProgression/docs/architecture.md), [MobForge](../CrazyWorldProgression/docs/mobforge.md), [MobForge API](../CrazyWorldProgression/docs/mobforge-api.md), and [portal groups](../CrazyWorldProgression/docs/portal-groups.md).

## Repository relationship

- This checkout is `EchelonCore`. Its `settings.gradle` includes sibling `../CrazyWorldProgression` as `:crazy-world-progression`.
- EchelonCore owns server-specific content and rules; CWP owns generic services, persistence, UI, and APIs. CWP must not import EchelonCore classes.
- When a change crosses the boundary, inspect and validate both repositories. Keep saved identifiers and YAML/model/editor round trips intact.
- Distinguish a configured feature from implemented gameplay. For example, Echelon's random-event YAML currently defines a blackout but no event definitions or effects.
- Build or structural checks alone do not establish in-game behavior. State what was actually verified.

## Suggested task handoff

> Read `AI_CONTEXT.md` and the linked feature/structure Markdown files first. Inspect the current code and YAML for the requested area, then implement the change in the owning mod. Preserve stable saved IDs and server authority. Report the exact changed paths and distinguish build results from runtime verification.
