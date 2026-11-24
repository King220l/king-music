"""Check the two remapped release candidates without installing or publishing them."""
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = {
    'yamusic': ROOT / 'build/libs/King-Music-0.5.16-Fabric-1.21.8.jar',
    'king_music_lyrics': ROOT / 'lyrics/build/libs/King-Music-Lyrics-0.1.5-Fabric-1.21.8.jar',
}

def classes(archive):
    count = 0
    for name in archive.namelist():
        if name.endswith('.class'):
            magic, minor, major = struct.unpack('>IHH', archive.read(name)[:8])
            assert magic == 0xCAFEBABE and major <= 65, f'Not Java 21 compatible: {name} ({major})'
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
            assert metadata['depends']['minecraft'] == '=1.21.8'
            assert metadata['depends']['java'] == '>=21'
            assert metadata['environment'] == 'client'
            assert metadata['icon'] in archive.namelist()
            count = classes(archive)
            assert count > 0
            if mod_id == 'yamusic':
                mixins = json.loads(archive.read('yamusic.mixins.json'))
                assert mixins['compatibilityLevel'] == 'JAVA_21'
                refmap = json.loads(archive.read(mixins['refmap']))
                assert len(refmap['mappings']) == 2
                assert any('method_1466(JIIII)V' in str(v) for v in refmap['mappings'].values())
                assert any('method_1601(JIII)V' in str(v) for v in refmap['mappings'].values())
                assert any('jlayer' in item['file'] for item in metadata['jars'])
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f'{digest}  {path.name}\n')
        print(f'PASS {path.name}: {count} classes, Java <=21, metadata and ZIP verified')
    return ''.join(lines)

if __name__ == '__main__':
    verify()
