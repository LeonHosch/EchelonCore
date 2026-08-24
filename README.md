# Echelon Core

Echelon Core contains the gameplay rules for the Echelon server. It depends on Crazy World Progression for generic wallet and skill-tree handling.

Owned here:

- Kingdom Points (global), Echelon Points, Fakhrul Currency, and Powerful Souls.
- Currency names, commands, colors, and icons.
- The kingdom and personal skill-tree YAML definitions.
- King persistence, `/king`, and the king-only kingdom-tree purchase policy.
- Powerful Soul claim behavior.
- The two-layer veil and client renderer.
- Echelon's baseline health, movement, and mining modifiers.

## Adding another Echelon currency

Add one `CurrencyDefinition` registration in `EchelonCore.registerCurrencies`, add its 32×32 icon under `assets/echelon-core/currencies`, and reference its identifier in any tree's `costs` mapping. No framework enum, persistence codec, networking record, or screen code needs to change.

## Build

This checkout expects `CrazyWorldProgression` as a sibling directory. The Gradle multi-project configuration builds both automatically:

```powershell
.\gradlew.bat build
```
