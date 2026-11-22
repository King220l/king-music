# KING Music — Fabric 1.21.1

This branch ports **Core 0.5.16, Lyrics 0.1.5, Visualizer 0.1.1** from GitHub
`King220l/king-music`, base commit `53e8de7`. It includes all Wave fixes through
`72eb8ca` (recommendations, Rotor feedback, queued track history and diagnostics).
The later R2/R3 commits register signed files; they do not contain a separate 26.3
source tree. No older locally modified source files were used for this port.

Together, Together Server and their shared protocol are unchanged and excluded
from Gradle settings. `main`, existing tags and published JARs are untouched.

## Build

Install **JDK 21**, set `JAVA_HOME` to that JDK, then run:

```powershell
.\gradlew.bat build --console plain
python tools/verify_port.py
# Equivalent build + artifact verification:
python build.py
# Isolated development client with all three modules:
.\gradlew.bat :runClient
```

On Linux/macOS use `bash gradlew build` / `bash gradlew :runClient`.
Gradle 8.9, Loom 1.7.4, Mojang mappings for Minecraft 1.21.1,
Fabric Loader 0.16.14 and Fabric API 0.116.7+1.21.1 are pinned.
The build does not use any launcher profile, sign files or publish releases.

Outputs:

- `build/libs/King-Music-0.5.16-Fabric-1.21.1.jar`
- `lyrics/build/libs/King-Music-Lyrics-0.1.5-Fabric-1.21.1.jar`
- `visualizer/build/libs/King-Music-Visualizer-0.1.1-Fabric-1.21.1.jar`

## Changes

- Java 21 bytecode and mixin compatibility; Fabric Loom remaps Mojang names to
  the intermediary namespace required by production Fabric 1.21.1.
- Older screen/widget input callbacks, keybinding categories and HUD callbacks.
- No standalone music button in the 1.21.1 title menu, as requested; F8 still opens
  the player. The pause mini-player only uses space free of vanilla buttons.
- Immediate GUI rendering, pose transforms, scaled scissor rectangles, textures,
  font resource names, tooltips and the older multi-line lyrics editor.
- A Java 21 bounded HTTP body subscriber replaces the Java 25-only limiting API;
  oversized responses still cancel before the extra bytes reach the buffer.
- Core uses a separate signed manifest endpoint for this version in this branch.
  Existing manifests in `main` are not replaced.

## Verification

Local Java 21 build: **passed** (2026-11-22).
`check` runs 18 upstream Wave checks and 27 port checks for LRC parsing, save/reload,
PCM resampling, stereo meters, FFT and HTTP body size limits. All passed.
`tools/verify_port.py` also checks ZIP integrity, exact Minecraft/Java constraints,
icons, both remapped mixin targets, nested JLayer and class-file versions across
all three production JARs. Together classes must not appear.

In-game development tests: **passed**. Production tests of the actual remapped
JARs: **passed**, 21 assertions (2026-11-22). The latter ran using ordinary Fabric,
the obfuscated vanilla client and the nested JLayer from the Core JAR. They also
verified the signed SHA-256 manifest through the mod's offline trust cache.

Tested: F8, absence of a title-menu button, local WAV scanning/playback, pause,
seek with pause preserved, LRC loading, Unicode editor and its line limit,
editor rendering at 50% scale, all five Visualizer modes receiving decoded PCM,
next/previous tracks. Screenshots were visually inspected; a double-scaled
background artifact was fixed before the production pass.

Reproduce the optional game test in its isolated `run` directory:

```powershell
python tools/create_smoke_fixtures.py
.\gradlew.bat -PsmokeTest :runClient
# Check run/smoke-result.txt; screenshots are in run/screenshots.
```

`smoke-tests` is only included when `-PsmokeTest` is specified and is never nested
into the three distributable mods. After testing the current build, an authorized
signer can run `python tools/sign_port.py`; the existing key stays under the user's
home directory. `python tools/run_production_smoke.py` tests the remapped artifacts
in `run-production`, using the signed manifest as a verified offline cache. It
requires the test mod built with `gradlew.bat -PsmokeTest build`.

Still unverified: live Yandex login/Wave requests, a real multiplayer session,
in-world HUD operation and interoperability with third-party menu/HUD mods.
No release has been published; existing release files have not been replaced.
