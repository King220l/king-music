"""Create synthetic test audio; never reads account tokens or existing music files."""
import array
import json
import math
from pathlib import Path
import wave

ROOT = Path(__file__).resolve().parents[1]
fixtures = ROOT / 'build/test-music'
fixtures.mkdir(parents=True, exist_ok=True)
for name, frequency in [('KING Test - Stereo Tone', 440), ('KING Test - Second Track', 660)]:
    path = fixtures / (name + '.wav')
    if not path.exists():
        pcm = array.array('h')
        for frame in range(44100 * 60):
            amplitude = .08 * (.5 + .5 * math.sin(frame / 44100 * 2))
            pcm.extend(int(32767 * amplitude * math.sin(2 * math.pi * f * frame / 44100))
                       for f in (frequency, frequency * 1.5))
        with wave.open(str(path), 'wb') as output:
            output.setnchannels(2)
            output.setsampwidth(2)
            output.setframerate(44100)
            output.writeframes(pcm.tobytes())
    path.with_suffix('.lrc').write_text(
        '[00:00.00]KING Music 1.21.4 test\n[00:05.00]Lyrics synchronization\n'
        '[00:10.00]Перемотка и русский текст\n[00:20.00]Stereo PCM playback test\n'
        '[00:40.00]End of test track\n', encoding='utf-8')
# MPEG-1 Layer III silent frames: exercise the actual MP3 decoder without an encoder dependency.
mp3 = fixtures / 'KING Test - Z MP3 Silence.mp3'
mp3.write_bytes((bytes.fromhex('fffb9000') + bytes(413)) * 2297)
mp3.with_suffix('.lrc').write_text('[00:00.00]Synthetic MP3 decoder test\n[00:10.00]MP3 seeking\n', encoding='utf-8')
config = ROOT / 'run/config/yamusic'
config.mkdir(parents=True, exist_ok=True)
settings = config / 'settings.json'
if not settings.exists():
    settings.write_text(json.dumps(dict(language='ru_ru', localFolder=str(fixtures), volume=5,
                                        repeat='QUEUE', hud='ALWAYS', uiScale=85)), encoding='utf-8')
options = ROOT / 'run/options.txt'
if not options.exists():
    options.write_text('guiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\npauseOnLostFocus:false\nsoundCategory_master:0.0\n', encoding='utf-8')
print('Synthetic WAV/LRC fixtures:', fixtures)
print('Run: gradlew.bat -PsmokeTest :runClient; result: run/smoke-result.txt')
