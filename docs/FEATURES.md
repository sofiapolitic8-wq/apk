# Feature matrix

DAVIDD is an original app. Code, UI, names and layout are all its own.

| Feature | Purpose | Mobile fit | Cost | Priority | Approach in DAVIDD |
|---|---|---|---|---|---|
| Floating / fixed stick, touch pad | Steering without a mouse | Native | Very low | Must | One custom view, multi-touch, throttled to tier input rate |
| Boost button (hold / lock) | Space key | Native | Very low | Must | Button sends keydown/keyup once per change |
| Zoom buttons | Mouse wheel | Native | Very low | Must | Wheel event dispatched to the canvas |
| Configurable action buttons (4) | Keyboard shortcuts | Native | Very low | Must | Slot → action table stored per profile |
| Drag-to-place layout editor | Custom screen layouts | Native | Low | Must | Same full-screen surface as the game |
| Left-handed mirror, size, opacity, dead zone, sensitivity | Touch customisation | Native | None | Must | Plain config values |
| Score / best / time / runs HUD | Session info | Native | Low | Must | Native view, redraws only on change, 2–8 Hz |
| Heading guide | Visual aim aid | Good | Low | Should | Line on the overlay canvas |
| Danger ring | Range visualisation | Good | Low | Should | Circle + rings on nearby snakes |
| Target arrows | Target indicators | Good | Low | Should | K-nearest, no allocation; edge arrows off-screen |
| Biggest-snake marker | Largest snake indicator | Good | Low | Should | Single pass over snake list |
| Food radar | Collectible radar | Good | Medium | Should | 16 angular bins, drawn as arcs |
| Performance tiers | Low-end support | Native | n/a | Must | Performance / Balanced / Quality set Hz, DPR, HUD rate |
| Profiles | Saved configurations | Native | None | Must | Up to 12, debounced atomic JSON with backup |
| Ad / tracker host blocking | Lighter page | Native | Low | Could | Host suffix list, toggleable |
| Bots / auto-play, auto-respawn | Playing for you | — | — | **Not built** | Out of scope: DAVIDD assists the player, it does not play |
| Team chat, SOS, shared team dots, players list | Team play | Needs a server and accounts | — | **Not built** | Would need backend + permissions; against "no tracking / minimal permissions" |
| Skin/cosmetic and texture changes | Visual mods | Possible | Medium | Deferred | Not needed for control; revisit later |
| Mouse-click and keyboard remapping | Desktop only | Replaced by buttons | — | n/a | Redesigned as touch actions |
