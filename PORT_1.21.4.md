# KING Music — Fabric 1.21.4

Sequential port from `port/fabric-1.21.3` (`c0575a1`), using the current GitHub
Core 0.5.16 Wave fixes. Only **Core 0.5.16 and Lyrics 0.1.5** are included.
Visualizer was excluded at the user's request on 2026-11-23. Its historical source
folder remains outside Gradle settings; Together is also excluded and unchanged.

Target: Java 21, Minecraft 1.21.4, Fabric Loader 0.16.14,
Fabric API 0.119.4+1.21.4, Loom 1.8.13, Gradle 8.10.2 and Mojang mappings.

## Verified on 2026-11-23

- Core and Lyrics build successfully; both production JARs pass ZIP, metadata,
  class-version and remapped input-mixin checks.
- 25 automated checks passed: 18 upstream Wave checks and 7 LRC/PCM/HTTP checks.
- A fresh test client without Visualizer passed development testing and 27
  production assertions, including Ed25519 trust, F8, WAV/MP3 playback and seek,
  pause, Unicode LRC editing and line limits, 50% UI scale, next/previous tracks,
  in-world track/lyrics HUDs and a pause mini-player clear of vanilla buttons.
- The synthetic MP3 exercises the nested JLayer decoder without downloading audio
  or requiring an encoder. Screenshots of the ordinary player/Lyrics were inspected.
- The title menu has no added music button; F8 opens the player.

Signed SHA-256 hashes for exactly two JARs are in `official/1.21.4`. The existing
local Ed25519 key was used without exporting it. No release has been published;
previous tags, branches and published JARs remain untouched.

## Reproduce

Set `JAVA_HOME` to JDK 21:

```powershell
.\gradlew.bat build --console plain
python tools/verify_port.py
python tools/create_smoke_fixtures.py
.\gradlew.bat -PsmokeTest -PsmokeWorld :runClient
.\gradlew.bat -PsmokeTest :smoke-tests:remapJar
# Authorized signer, after checking the current test result:
python tools/sign_port.py
python tools/run_production_smoke.py --world
```

Results: `run/smoke-result.txt` and
`run-production-core-lyrics/smoke-result.txt`. Screenshots are under each test
profile's `screenshots` directory. The temporary smoke mod is not included in
ordinary builds or either distributable JAR.

Still unverified: live Yandex login/Wave requests, real multiplayer and third-party
menu/HUD mod interoperability. The earlier fullscreen Visualizer screenshots came
from a dedicated test screen; normal-player Visualizer layout was not established.
