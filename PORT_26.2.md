# KING Music — Fabric 26.2

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `5e7c0e3` (26.1.2).
The current GitHub Wave API/player/history sources are unchanged. Together and
historical Visualizer sources are unchanged and excluded. Existing main,
26.2/26.3 tags, releases and published JARs are not replaced.

Target: Java 25, Loader 0.19.3, Fabric API 0.161.0+26.2,
Loom 1.17.21, Gradle 9.6.0 and the official unobfuscated game.

## Adaptation

Minecraft now stores current screens, overlays and screen switching under its
Gui object. Core, Lyrics and the optional test mod use this API. The test-only
screenshot readback uses GameRenderer.mainRenderTarget; flat-world creation
resolves the registered FLAT preset. No title-menu button is added; F8 opens the
player. Direct official mixin targets and Java 25 classes are verified.

See the [official Fabric migration notes](https://fabricmc.net/2026/06/15/262.html).

## Verified on 2026-11-24

Build and 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP). All 35 development
assertions passed: WAV/MP3 playback, pause/seek, scaled mouse and keyboard events,
Unicode lyrics editing and limits, text visibility at 85% and 50%, next/previous,
world HUDs and pause-menu control separation. Player/Lyrics and editor screenshots
were visually inspected. Both JARs pass ZIP, metadata, bytecode and mixin checks.

Hashes for exactly two JARs are signed with the existing key without exporting it.
All 37 production assertions passed in ordinary Fabric, including fresh online
VERIFIED authenticity with no cached signatures. Production Lyrics screenshots
were visually inspected. An initial first-launch check caught the missing remote
manifest before release; after committing the signed manifest, the complete
production run passed. Uploaded release files are downloaded and compared
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
included in distributable JARs. Live Yandex login/Wave requests, multiplayer,
third-party menu/HUD interoperability and experimental Vulkan remain untested.
