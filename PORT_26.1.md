# KING Music — Fabric 26.1

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `f6514e5` (1.21.11).
The current GitHub Wave API/player/history sources are unchanged. Together and
historical Visualizer sources are unchanged and excluded from the build.

Target: Java 25, Loader 0.18.4, Fabric API 0.145.1+26.1,
Loom 1.17.21, Gradle 9.6.0 and the official unobfuscated game.

## Adaptation

- Standard `net.fabricmc.fabric-loom` replaces the remapping plugin. Dependencies
  use standard Gradle variants; JARs need no remapping or input refmaps.
- GUI widgets and screens use GuiGraphicsExtractor and the extraction callbacks.
- KeyMappingHelper, HudElementRegistry, Screens.getWidgets and beforeExtract
  replace the removed or renamed Fabric APIs.
- The optional test client uses the new character event and world settings APIs.
- Java 25 bytecode and direct official mixin targets are verified. JLayer remains
  nested in Core. No title-menu button is added; F8 opens the player.

See the [official Fabric migration notes](https://fabricmc.net/2026/03/14/261.html).

## Verified on 2026-11-24

The build and all 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP). All 35
development game assertions passed, covering WAV/MP3 playback, pause/seek,
scaled mouse and keyboard input, Unicode editing and line limits, visible
Lyrics/editor text at 85% and 50%, next/previous, world HUDs and pause-menu
control separation. Player/Lyrics and editor screenshots were inspected.

Both JARs pass ZIP, metadata, class-version and official mixin target checks.
Their hashes were signed with the existing local Ed25519 key without exporting
it. All 37 production assertions passed in ordinary Fabric, including fresh
online VERIFIED authenticity without cached signatures. Production Lyrics
screenshots were visually inspected. Release assets are downloaded and compared
byte-for-byte before publication.

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
