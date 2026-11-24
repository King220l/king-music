# KING Music — Fabric 26.3

Sequential Core 0.5.16 + Lyrics 0.1.5 port from `7196d7e` (26.2).
GitHub Wave API/player/history sources remain unchanged. Together and Visualizer
are excluded. Existing main, tags and published JARs are not replaced.

Target: Java 25, Loader 0.19.5, Fabric API 0.167.0+26.3,
Loom 1.17.21 and Gradle 9.6.0.

## Adaptation

Minecraft 26.3 uses SDL input instead of GLFW. Keyboard scancodes, mouse buttons,
modifier groups and key labels use the new input API. Saved GLFW shortcuts migrate
once, preserving Ctrl bindings, F8 and right/middle mouse behavior; left and right
modifier keys are normalized. No title-menu button is added. F8 opens the player.

The folder selector now uses the asynchronous SDL native dialog on the game
thread, with a retained native callback and results delivered to the game thread.
Browser links use SDL. The optional screenshot test targets GameRenderer.render().

## Verified on 2026-11-24

Build and 25 logic checks passed (18 Wave, 7 LRC/PCM/HTTP). All 43 development
assertions passed, including actual Windows folder selection, shortcut migration,
left/right Ctrl dispatch, WAV/MP3 playback, pause/seek, scaled input, Unicode lyrics
editing and limits, visible text at 85% and 50%, next/previous, world HUDs and
pause-menu control separation. Lyrics and editor screenshots were visually checked.
Both JARs passed ZIP, metadata, Java 25 bytecode and direct mixin target checks.
The hashes of exactly two JARs are signed with the existing key without exporting it.
All 43 production assertions passed with a fresh online VERIFIED signature and
no cached manifest. Production screenshots of Lyrics and the editor were visually
inspected. Two initial ordinary starts ended before the smoke suite without a
crash report; a diagnostic full run passed. A repeat ordinary run then passed all 43 assertions with exit code 0.
No unsuccessful startup was treated as a passed test.

## Reproduce

Use JDK 25. Run `gradlew.bat build`, `python tools/verify_port.py`,
`python tools/create_smoke_fixtures.py`, then
`gradlew.bat -PsmokeTest -PsmokeWorld -PsmokeFolder :runClient`.
The optional folder test requires selecting the generated test-music folder in
Windows. Omit -PsmokeFolder for an unattended run. Build the test mod using
`gradlew.bat -PsmokeTest :smoke-tests:jar`. An authorized signer can run
`python tools/sign_port.py` after the game test. After pushing the manifest, run
`python tools/run_production_smoke.py --world --online-trust`.

Test profiles use synthetic audio and no player account. The test mod is not
included in release JARs. Live Yandex login/Wave requests, multiplayer,
third-party menu/HUD compatibility and experimental Vulkan remain untested.
