# Architecture

```
app/src/main/
  assets/engine.js            page-side engine (input events, score, overlays), tested with Node
  java/dev/davidd/app/
    core/perf/                Tier (budgets), FrameMonitor (FPS only when asked)
    core/storage/             Config models, Presets, ProfileStore (debounced atomic JSON), Stats
    features/controls/        Act, Layout, ControlsView (one view, no allocation in touch/draw)
    features/hud/             HudView (cached strings, redraw on change)
    game/                     GameActivity, GameBridge (3 calls), QuickSheet, Blocklist
    ui/                       Ui toolkit, Home / Settings / Editor, Immersive
```

Rules followed
- Only permission: INTERNET. No third-party libraries, no analytics, no ads, no services.
- Every optional feature is a flag in `Config`; the page engine draws nothing and runs no loop with all overlays off.
- Overlay loop is `requestAnimationFrame` capped to the tier's Hz and stops when hidden / not playing. A feature that throws repeatedly switches itself off and is reported on the Home screen.
- Config is clamped on load (`Config.fromJson` never throws); a corrupt file falls back to its backup, then to presets.
- Activity cleanup: handlers cleared, listeners removed, WebView detached and destroyed, renderer crash shows a retry screen.
- Back in a match opens the quick menu instead of exiting.
