"""Check the two remapped release candidates without installing or publishing them."""
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = {
    'yamusic': ROOT / 'build/libs/King-Music-0.5.16-Fabric-26.3.jar',
    'king_music_lyrics': ROOT / 'lyrics/build/libs/King-Music-Lyrics-0.1.5-Fabric-26.3.jar',
}

def classes(archive):
    count = 0
    for name in archive.namelist():
        if name.endswith('.class'):
            magic, minor, major = struct.unpack('>IHH', archive.read(name)[:8])
            assert magic == 0xCAFEBABE and major <= 69, f'Not Java 25 compatible: {name} ({major})'
            assert 'Together' not in name and '/rooms/' not in name, f'Unexpected Together class: {name}'
            count += 1
        if name.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                count += classes(nested)
    return count

def verify():
    lines = []
    for mod_id, path in ARTIFACTS.items():
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None, f'Corrupt archive: {path}'
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert metadata['id'] == mod_id
            assert metadata['depends']['minecraft'] == '=26.3'
            assert metadata['depends']['java'] == '>=25'
            assert metadata['environment'] == 'client'
            assert metadata['icon'] in archive.namelist()
            count = classes(archive)
            assert count > 0
            if mod_id == 'yamusic':
                mixins = json.loads(archive.read('yamusic.mixins.json'))
                assert mixins['compatibilityLevel'] == 'JAVA_25'
                assert 'refmap' not in mixins
                for name, method, target in [
                    ('KeyboardMixin', b'keyPress', b'net/minecraft/client/KeyboardHandler'),
                    ('MouseMixin', b'onButton', b'net/minecraft/client/MouseHandler'),
                ]:
                    bytecode = archive.read('studio/king220l/yamusic/mixin/' + name + '.class')
                    assert method in bytecode and target in bytecode
                    assert b'net/minecraft/class_' not in bytecode, 'Unexpected intermediary mixin reference'
                assert any('jlayer' in item['file'] for item in metadata['jars'])
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f'{digest}  {path.name}\n')
        print(f'PASS {path.name}: {count} classes, Java <=25, metadata and ZIP verified')
    return ''.join(lines)

if __name__ == '__main__':
    verify()
