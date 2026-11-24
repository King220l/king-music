# KING Music — Fabric 1.21.6

Sequential Core 0.5.16 and Lyrics 0.1.5 port from `d1a1d14` (1.21.5).
Visualizer and Together are excluded. Current GitHub Wave fixes are preserved.

Target: Java 21, Loader 0.16.14, Fabric API 0.128.2+1.21.6,
Loom 1.10.5, Gradle 8.12 and official Mojang mappings.

## Verified on 2026-11-23 and 2026-11-24

- Core and Lyrics build successfully. Both production JARs pass ZIP integrity,
  Java 21 class-version, exact Minecraft metadata and input-mixin refmap checks.
- All 25 logic checks pass: 18 upstream Wave checks and 7 LRC/PCM/HTTP checks.
- The isolated development client passed 29 assertions: Core/Lyrics initialization,
  absence of Visualizer, no title-menu button, F8, local WAV/MP3 playback and seek,
  Unicode lyrics editing and line limits, 50% UI scale, next/previous tracks,
  in-world HUDs and a pause mini-player clear of vanilla buttons.
- Three completed-frame pixel checks confirm visible Lyrics/editor text, including
  50% scale. Player and editor screenshots were also visually inspected.

Minecraft 1.21.6 introduces a 2D matrix stack and render pipelines. The port
adapts GUI transforms, textured rendering, overlay strata and deferred tooltips.
The Lyrics editor uses the new builder and vanilla scaled scissor behavior.
See the [Fabric migration notes](https://fabricmc.net/2025/06/15/1216.html).

The ordinary Fabric production client passed all 30 assertions, including
verification of actual JAR hashes against the signed Ed25519 manifest. Production
player/Lyrics screenshots were visually inspected as well. Signed SHA-256 hashes
for exactly two JARs are under `official/1.21.6`; the existing Ed25519 key was used
without exporting it.
No release or tag has been published. Only sources and signed manifests are
pushed to GitHub; verified JARs are delivered separately in chat.

## Reproduce

Set `JAVA_HOME` to JDK 21:

```powershell
.\gradlew.bat build --console plain
python tools/verify_port.py
python tools/create_smoke_fixtures.py
.\gradlew.bat -PsmokeTest -PsmokeWorld :runClient
.\gradlew.bat -PsmokeTest :smoke-tests:remapJar
python tools/sign_port.py
python tools/run_production_smoke.py --world
```

Signing requires the authorized local key and a successful game-test report.
The optional smoke mod is excluded from ordinary builds and distributable JARs.
Results and screenshots stay in the isolated `run` and
`run-production-core-lyrics` profiles.

Still unverified: live Yandex login/Wave requests, real multiplayer and third-party
menu/HUD mod interoperability. No player account or real music was copied into
the test profiles. Together and historical Visualizer sources are unchanged.
