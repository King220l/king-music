# Core + Lyrics ports

Only Core 0.5.16 and Lyrics 0.1.5 are distributed. Choose exactly one Minecraft version.
F8 opens the player; no title-menu button is added. Install matching Fabric API.

| Minecraft | Java | Logic checks | Production assertions | Release |
|---|---|---|---|---|
| 1.21.1 | 21 | 45 | 21 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.1) |
| 1.21.2 | 21 | 45 | 28 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.2) |
| 1.21.3 | 21 | 25 | 27 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.3) |
| 1.21.4 | 21 | 25 | 30 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.4) |
| 1.21.5 | 21 | 25 | 30 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.5) |
| 1.21.6 | 21 | 25 | 31 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.6) |
| 1.21.7 | 21 | 25 | 31 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.7) |
| 1.21.8 | 21 | 25 | 31 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.8) |
| 1.21.9 | 21 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.9) |
| 1.21.10 | 21 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.10) |
| 1.21.11 | 21 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-1.21.11) |
| 26.1 | 25 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-26.1) |
| 26.1.1 | 25 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-26.1.1) |
| 26.1.2 | 25 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-26.1.2) |
| 26.2 | 25 | 25 | 37 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-26.2) |
| 26.3 | 25 | 25 | 43 | [Download](https://github.com/King220l/king-music/releases/tag/v0.5.16-fabric-26.3) |

Each version has a dedicated port/fabric-<version> branch and PORT_<version>.md report.
1.21.1 production checks covered the menu/player but not a world. Later versions
include world HUD and pause-menu checks. The first two historical test runs included
Visualizer, but release assets contain only Core and Lyrics.

26.3 development checks additionally covered the Windows native folder selector
and legacy shortcut migration. Production passed 43 assertions with fresh online trust.

Live Yandex authorization/Wave requests, multiplayer and third-party UI compatibility
were not verified. Existing main releases were not replaced.
