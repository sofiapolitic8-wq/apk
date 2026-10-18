# DAVIDD

Original native Android app: touch controls, HUD and visual overlays for Slither, in a lightweight WebView shell. Kotlin, no dependencies, no ads, no analytics. Minimum Android 8.0.

## Build
Android Studio: open this folder and run `app`. Command line (Gradle 8.9, JDK 17, Android SDK 34):
`gradle assembleRelease` -> `app/build/outputs/apk/release/`.
(The Gradle wrapper JAR is not included; Android Studio or `gradle wrapper` generates it.)

`app/davidd-sideload.jks` is a **public** sideload key (password in `app/build.gradle`). Use your own private key for anything you publish.

## Test
`node tests/engine.test.js` runs the page engine against a fake DOM (14 tests).

## Using it
Play -> start a match as usual. Controls appear when a match starts. The ≡ button (or Back) opens quick settings. Overlays read the game's own on-page state; **Home > Overlay data** shows "Ready" if the page exposed it, "No data" if the site changed (controls, HUD and score still work).
DAVIDD is an aid for the player: it never steers, boosts or respawns by itself.
Made by DAVIDD.

## Team server
`server/server.js` is the team relay (Node 16+, no dependencies): `node server/server.js`. It needs an https URL for other players (see `server/README.txt`). The app does not talk to it yet.

## Ported from the PC mod
Done: brightness, saturation. Not yet: Eyes Back, Center Eyes, spine mode, team client/chat/SOS (see chat notes).
