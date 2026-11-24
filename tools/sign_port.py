"""Explicitly sign a verified 1.21.9 manifest with the existing local Ed25519 key.

Does not copy/export the key, install JARs, upload anything or modify old manifests.
"""
import os
from pathlib import Path
import re
import subprocess

from verify_port import ROOT, verify

if __name__ == '__main__':
    result = ROOT / 'run/smoke-result.txt'
    if not result.is_file() or not result.read_text(encoding='utf-8').startswith('PASS:'):
        raise SystemExit('Run the isolated in-game smoke test successfully before signing candidates.')
    if 'Visualizer' in result.read_text(encoding='utf-8'):
        raise SystemExit('Run the current Core + Lyrics test before signing this build.')
    public = (Path.home() / '.kingmusic/release-signing.pub').read_text(encoding='utf-8').strip()
    pinned = re.search(r'PUBLIC_KEY\s*=\s*"([^"]+)"',
                       (ROOT / 'src/studio/king220l/yamusic/BuildTrust.java').read_text(encoding='utf-8')).group(1)
    if public != pinned:
        raise SystemExit('Local signing identity does not match the pinned public key.')
    manifest = ROOT / 'official/1.21.9/manifest.txt'
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text('# KING Music Fabric 1.21.9 — SHA-256 / Ed25519\n' + verify(), encoding='utf-8', newline='\n')
    signature = manifest.with_suffix('.sig')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
    signer = str(ROOT / 'tools/ReleaseSigner.java')
    subprocess.run([java, signer, 'sign', str(manifest), str(signature)], check=True)
    subprocess.run([java, signer, 'verify', str(manifest), str(signature)], check=True)
