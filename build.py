"""Build the Fabric 26.1.2 branch. Requires JAVA_HOME pointing to JDK 25."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent

def main():
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    subprocess.run([str(wrapper), 'build', '--console', 'plain'], cwd=ROOT, check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/verify_port.py')], cwd=ROOT, check=True)

if __name__ == '__main__':
    main()
