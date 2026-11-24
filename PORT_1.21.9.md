# KING Music — Fabric 1.21.9

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `23dd5a9` (1.21.8).
GitHub Wave service/player/history sources are unchanged. Together and historical
Visualizer sources are unchanged and excluded.

Target: Java 21, Loader 0.17.2, Fabric API 0.134.1+1.21.9,
Loom 1.11.8, Gradle 8.14.3 and official Mojang mappings.

## Adaptation

- Native KeyEvent, MouseButtonEvent and MouseButtonInfo replace old callbacks
  and internal input records. Keyboard/mouse mixins use the new real signatures.
- Scaled click, release and drag dispatch preserve screen coordinates.
- Key mappings use registered categories. Font styles use FontDescription.Resource.
- Window handles and masked token formatting use the new API names.
- The title-menu mod button remains removed; F8 opens the player.

See the [official Fabric migration notes](https://fabricmc.net/2025/09/23/1219.html).

## Verified on 2026-11-24

Build and 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP). All 35 development
assertions passed: audio, seek/pause, lyrics editing and limits, scaled text,
world HUDs and pause-menu positioning. Six new assertions exercise actual
scaled mouse clicks, Space key activation and Unicode character events.
Player/Lyrics and 50% editor screenshots were visually inspected.

Both candidate JARs pass ZIP integrity, exact MC metadata, Java 21 class-version
and the new remapped input-mixin descriptor checks. Their hashes were signed
using the existing local key without exporting it. Production and fresh-online
verification are pending; no release has been published yet.

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
