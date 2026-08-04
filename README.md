# Art Widget

Minimal Android home-screen widget that shows procedurally generated art.

- 23 pattern styles — flow fields, dots, gradient meshes, voronoi, bauhaus grids, contours, scallops, and more. Pick which ones are in rotation.
- Palettes are generated in OKLCH with colour-harmony strategies, so every combination comes out looking considered — no colour theory needed. New art matches the system light/dark theme.
- Shuffle the pattern and the colours independently; save the ones you like.
- Deterministic: an artwork is just `pattern + seed + colours`, so it re-renders identically at any size. The widget mirrors the app's current art and re-renders when resized.
- Tap the widget for a new design. Optionally auto-refresh on a schedule (30 min to daily) via the app's Auto button — inexact, non-waking alarms, so no battery cost.
- Optionally apply the art as your wallpaper (home + lock) via the Wallpaper button; taps and auto-refresh keep it in sync, and it works with or without the widget.
- Share via a small JSON code — copy it out, or paste one into the import box.

## Build

```
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compile SDK 35). Min SDK 26. No runtime dependencies beyond the Kotlin stdlib.

Unit tests render every pattern through Robolectric's native graphics into `app/build/renders/` for visual inspection, and check determinism, share-code round-trips, and odd canvas sizes:

```
./gradlew test
```
