# KING Music — Fabric 1.21.2

Sequential port following the verified 1.21.1 branch (`f271f9b`). Core 0.5.16,
Lyrics 0.1.5 and Visualizer 0.1.1 retain the current GitHub Wave fixes.
Together and the original 26.2/26.3 branches/releases remain unchanged.

Target: Java 21, Minecraft 1.21.2, Fabric Loader 0.16.14,
Fabric API 0.106.1+1.21.2, Loom 1.8.13, Gradle 8.10.2, Mojang mappings.

Status (2026-11-22): **build and verification passed**. The three remapped
production JARs are signed through `official/1.21.2/manifest.txt` and its Ed25519
signature. No release was published and no previous release files were replaced.

The 1.21.2 texture drawing calls now use `RenderType::guiTextured` with the
correct source/destination rectangle ordering. The title menu has no standalone
music button; F8 opens the player. The existing Wave implementation is unchanged.

Verification: 45 automated logic checks (18 upstream Wave, 27 lyrics/audio/HTTP)
passed. The game tests passed in development and in ordinary Fabric with the
actual remapped JARs. Production logged 28 assertions, including Ed25519 trust,
F8, WAV playback, pause/seek, LRC, Unicode editing and its line limit, 50% scale,
all five Visualizer modes receiving PCM, next/previous tracks, real in-world
track/lyrics HUD rendering and a pause mini-player clear of all vanilla buttons.
Game screenshots were visually inspected. The isolated flat world and test
profile do not use a player's saves or account.

The production test launcher applies Fabric's library versions over vanilla
versions (including ASM) before starting the ordinary Knot client.

Build and verify:

```powershell
.\gradlew.bat build --console plain
python tools/verify_port.py
python tools/create_smoke_fixtures.py
.\gradlew.bat -PsmokeTest -PsmokeWorld :runClient
# After the verified build, for an authorized signer with the existing local key:
.\gradlew.bat -PsmokeTest build
python tools/sign_port.py
python tools/run_production_smoke.py --world
```

The optional smoke mod is excluded from ordinary builds and distribution JARs.
Existing 1.21.1 signed manifests are retained unchanged as history; new signed
hashes will use the separate `official/1.21.2` directory after verification.

Still unverified: live Yandex login/Wave service requests, real multiplayer and
third-party menu/HUD mod interoperability. Signing does not export the key.
