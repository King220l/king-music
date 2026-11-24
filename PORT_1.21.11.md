# KING Music — Fabric 1.21.11

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `a9daa01` (1.21.10).
GitHub Wave service/player/history sources are unchanged. Together and historical
Visualizer sources are unchanged and excluded.

Target: Java 21, Loader 0.18.1, Fabric API 0.141.6+1.21.11,
Loom 1.14.10, Gradle 9.2.1 and official Mojang mappings.

## Adaptation

ResourceLocation becomes Identifier; Util moves to net.minecraft.util. The world
test uses the new gamerules package. Native input callbacks and registered key
categories remain compatible. Loom's legacy mixin AP is explicitly enabled so
both production input refmaps and the optional frame-capture refmap are checked.
The title-menu mod button remains removed; F8 opens the player.

## Verified on 2026-11-24

Build and 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP). All 35 development
assertions passed: audio, seek/pause, lyrics editing and limits, scaled text,
world HUDs and pause-menu positioning. Six input assertions exercise actual
scaled mouse clicks, Space key activation and Unicode character events.
Player/Lyrics and 50% editor screenshots were visually inspected.

Both candidate JARs pass ZIP integrity, exact MC metadata, Java 21 class-version
and the new remapped input-mixin descriptor checks. Their hashes were signed
using the existing local key without exporting it. All 37 assertions passed in
ordinary Fabric with the actual JARs, including fresh online authenticity without
cached signatures. Production Lyrics screenshots were visually inspected.
Release uploads are downloaded and compared byte-for-byte before publication.

## Reproduce

With JDK 21: `gradlew.bat build`, `python tools/verify_port.py`,
`python tools/create_smoke_fixtures.py`, then
`gradlew.bat -PsmokeTest -PsmokeWorld :runClient`.
Build the optional test mod with `gradlew.bat -PsmokeTest :smoke-tests:remapJar`.
The authorized signer can run `python tools/sign_port.py` after a successful game
test. After pushing the manifest, run
`python tools/run_production_smoke.py --world --online-trust`.

Test profiles are isolated; no real player account is used. The test mod is not
included in distributable JARs. Live Yandex login/Wave requests, multiplayer and
third-party menu/HUD interoperability remain untested.
