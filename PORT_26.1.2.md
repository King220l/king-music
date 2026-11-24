# KING Music — Fabric 26.1.2

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `83a11fc` (26.1.1).
The current GitHub Wave API/player/history sources are unchanged. Together and
historical Visualizer sources are unchanged and excluded from the build.

Target: Java 25, Loader 0.18.4, Fabric API 0.155.3+26.1.2,
Loom 1.17.21, Gradle 9.6.0 and the official unobfuscated game.

## Adaptation

Retains the Java 25, unobfuscated Loom, GUI extraction and HUD registration
changes from 26.1. No further compatibility-source changes were required after
compilation against the actual 26.1.2 client and matching Fabric API.
The title-menu button remains removed; F8 opens the player.

## Verified on 2026-11-24

The build and all 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP). All 35
development game assertions passed, covering WAV/MP3 playback, pause/seek,
scaled mouse and keyboard input, Unicode editing and line limits, visible
Lyrics/editor text at 85% and 50%, next/previous, world HUDs and pause-menu
control separation. Player/Lyrics and editor screenshots were inspected.

Both JARs pass ZIP, metadata, class-version and official mixin target checks.
Their hashes were signed with the existing local Ed25519 key without exporting
it. Production-JAR and fresh online authenticity checks are pending; no release yet.

## Reproduce

Use JDK 25. Run `gradlew.bat build`, `python tools/verify_port.py`,
`python tools/create_smoke_fixtures.py`, then
`gradlew.bat -PsmokeTest -PsmokeWorld :runClient`.
Build the optional test mod with `gradlew.bat -PsmokeTest :smoke-tests:jar`.
An authorized signer can run `python tools/sign_port.py` after the game test.
After pushing the manifest, run
`python tools/run_production_smoke.py --world --online-trust`.

Test profiles are isolated and do not use a player account. The test mod is not
included in distributable JARs. Live Yandex login/Wave requests, multiplayer and
third-party menu/HUD interoperability remain untested.
