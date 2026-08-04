# Wallpaper Art

Minimal Android app that generates procedural art and sets it as your wallpaper.

- 38 pattern styles — flow fields, dots, gradient meshes, voronoi, bauhaus grids, arches, isometric cubes, layered hills, and more. Hide any pattern from the current art, and restore hidden ones from a previewed list.
- Palettes are generated in OKLCH with colour-harmony strategies, so every combination comes out looking considered — no colour theory needed. New art matches the system light/dark theme.
- Refreshes re-roll pattern and colours; either can be locked. Auto-refresh on a schedule (30 min to daily) via inexact, non-waking alarms — no battery cost.
- The lock screen gets the full art; the home screen can optionally blur its bottom so dock icons stay legible (slider sets where the blur starts).
- Deterministic: an artwork is just `pattern + seed + colours`, so it re-renders identically at any size. Share via a small JSON code — copy it out, or paste one into the import box.

## Build

```
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compile SDK 35). Min SDK 26. No runtime dependencies beyond the Kotlin stdlib.

Unit tests render every pattern through Robolectric's native graphics into `app/build/renders/` for visual inspection, and check determinism, share-code round-trips, and odd canvas sizes:

```
./gradlew test
```
