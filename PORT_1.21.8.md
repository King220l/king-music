# KING Music — Fabric 1.21.8

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `aaffee5` (1.21.7).
GitHub Wave improvements are preserved. Together and historical Visualizer
sources are unchanged and excluded from the build.

Target: Java 21, Fabric Loader 0.16.14, Fabric API 0.136.1+1.21.8,
Loom 1.10.5, Gradle 8.12 and official Mojang mappings.

## Verified on 2026-11-24

- Build succeeded. All 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP).
- Development game testing passed all 29 assertions, including WAV/MP3 playback,
  pause/seek, Unicode LRC editing and limits, 50% UI scale, next/previous,
  world HUDs and pause-menu control separation. Three completed-frame pixel
  assertions check visible Lyrics/editor text. Screenshots were visually inspected.
- No title-menu mod button; F8 opens and closes the player.
- ZIP integrity, exact Minecraft metadata, Java 21 classes and remapped input
  mixins were checked before signing the two JAR hashes with the existing key.

Signed manifests are published on the version-specific branch. Production-JAR
and fresh online-authenticity verification are pending; no release is published.

## Reproduce

Use JDK 21. Run `gradlew.bat build`, `python tools/verify_port.py`,
`python tools/create_smoke_fixtures.py`, then
`gradlew.bat -PsmokeTest -PsmokeWorld :runClient`.
Build the optional test JAR with `gradlew.bat -PsmokeTest :smoke-tests:remapJar`.
An authorized signer can run `python tools/sign_port.py` after the game test.
Once the manifest is pushed, run
`python tools/run_production_smoke.py --world --online-trust`.

Test profiles are isolated; no player account is used. The smoke mod is excluded
from ordinary builds and distributable JARs. Live Yandex login/Wave requests,
multiplayer and third-party menu/HUD interoperability remain untested.
