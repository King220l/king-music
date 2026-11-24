# KING Music — Fabric 1.21.7

Sequential Core 0.5.16 and Lyrics 0.1.5 port from `f1835d2` (1.21.6).
Visualizer and Together are excluded. Current GitHub Wave fixes are preserved.

Target: Java 21, Loader 0.16.14, Fabric API 0.129.0+1.21.7,
Loom 1.10.5, Gradle 8.12 and official Mojang mappings.

## Verified on 2026-11-24

- Core and Lyrics build successfully. Both production JARs pass ZIP integrity,
  Java 21 class-version, exact Minecraft metadata and input-mixin refmap checks.
- All 25 logic checks pass: 18 upstream Wave checks and 7 LRC/PCM/HTTP checks.
- The isolated development client passed 29 assertions: Core/Lyrics initialization,
  absence of Visualizer, no title-menu button, F8, local WAV/MP3 playback and seek,
  Unicode lyrics editing and line limits, 50% UI scale, next/previous tracks,
  in-world HUDs and a pause mini-player clear of vanilla buttons.
- Three completed-frame pixel checks confirm visible Lyrics/editor text, including
  50% scale. Player and editor screenshots were also visually inspected.

This version retains the 1.21.6 GUI matrix/render-pipeline adaptation and
uses Fabric API 0.129.0+1.21.7. No compatibility-source changes were needed after
compiling against the actual 1.21.7 mappings. Signed manifests are marked binary
in .gitattributes so Windows checkout cannot change their signed line endings.

The ordinary Fabric production client passed all 30 assertions, including
signature and actual-JAR hash verification. Its Lyrics screenshot was visually
inspected. Signed hashes for the two verified JARs are in `official/1.21.7`.
No release or tag has been published yet.

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

## Online authenticity — 2026-11-24

A fresh production client with cached signature files removed passed all 31
assertions. It explicitly confirmed VERIFIED online authenticity from the
version-specific GitHub manifest. The release upload is verified byte-for-byte
before publication. Only Core and Lyrics are release artifacts.
