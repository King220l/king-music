import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "build"
BASE = Path(os.environ["APPDATA"]) / "ModrinthApp"
VERSION = "26.2-0.20.1"
PROFILE = BASE / "profiles" / "WSH_1_0_0"
JLAYER = "https://repo.maven.apache.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.jar"
JLAYER_SHA256 = "850508c837454a1b06017c32a36876fae516de1e89a829f725fee1e6dcc52000"
SIGNING_KEY = Path.home() / ".kingmusic" / "release-signing.pk8"


def classpath():
    meta = BASE / "meta"
    version_dir = meta / "versions" / VERSION
    version = json.loads((version_dir / f"{VERSION}.json").read_text(encoding="utf-8"))
    libraries = [version_dir / f"{VERSION}.jar"]
    for library in version["libraries"]:
        artifact = library.get("downloads", {}).get("artifact", {})
        path = artifact.get("path")
        if not path:
            group, name, number = library["name"].split(":")[:3]
            path = f"{group.replace('.', '/')}/{name}/{number}/{name}-{number}.jar"
        jar = meta / "libraries" / path
        if jar.is_file():
            libraries.append(jar)
    api = next((PROFILE / "mods").glob("fabric-api-*+26.2.jar"))
    with zipfile.ZipFile(api) as source:
        for entry in source.namelist():
            if entry.startswith("META-INF/jars/") and entry.endswith(".jar"):
                dest = OUT / Path(entry).name
                dest.write_bytes(source.read(entry))
                libraries.append(dest)
    return libraries


def build_addon(project, name, namespace, dependencies, shared=None):
    version = json.loads((project / "resources" / "fabric.mod.json").read_text(encoding="utf-8"))["version"]
    target = OUT / f"{name}-{version}-Fabric-26.2.jar"
    with tempfile.TemporaryDirectory(dir=OUT, prefix="addon-classes-") as temp:
        classes = Path(temp)
        sources = sorted((project / "src").rglob("*.java"))
        if shared is not None:
            sources.extend(sorted(shared.rglob("*.java")))
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "--release", "25", "-proc:none",
             "-classpath", os.pathsep.join(map(str, dependencies)),
             "-d", str(classes), *map(str, sources)],
            check=True,
        )
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            for directory in (classes, project / "resources"):
                for file in sorted(directory.rglob("*")):
                    if file.is_file():
                        archive.write(file, file.relative_to(directory).as_posix())
            archive.write(ROOT / "resources/assets/yamusic/icon.png", f"assets/{namespace}/icon.png")
            archive.write(ROOT / "LICENSE", "META-INF/LICENSE-KING-MUSIC.txt")
            archive.write(ROOT / "AI_USAGE_NOTICE.md", "META-INF/KING-MUSIC-AI-NOTICE.md")
    return target


def main():
    OUT.mkdir(exist_ok=True)
    jlayer = OUT / "jlayer-1.0.1.jar"
    if not jlayer.is_file():
        with urllib.request.urlopen(JLAYER, timeout=30) as response:
            jlayer.write_bytes(response.read())
    if hashlib.sha256(jlayer.read_bytes()).hexdigest() != JLAYER_SHA256:
        raise RuntimeError("Unexpected JLayer checksum")
    sources = sorted((ROOT / "src").rglob("*.java"))
    version = json.loads((ROOT / "resources" / "fabric.mod.json").read_text(encoding="utf-8"))["version"]
    target = OUT / f"King-Music-{version}-Fabric-26.2.jar"
    with tempfile.TemporaryDirectory(dir=OUT, prefix="classes-") as temp:
        classes = Path(temp)
        dependencies = [*classpath(), jlayer]
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "--release", "25", "-proc:none",
             "-classpath", os.pathsep.join(map(str, dependencies)),
             "-d", str(classes), *map(str, sources)],
            check=True,
        )
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            for directory in (classes, ROOT / "resources"):
                for file in sorted(directory.rglob("*")):
                    if file.is_file():
                        archive.write(file, file.relative_to(directory).as_posix())
            with zipfile.ZipFile(jlayer) as library:
                for name in library.namelist():
                    if name.startswith("javazoom/jl/decoder/") and not name.endswith("/"):
                        archive.writestr(name, library.read(name))
            archive.write(ROOT / "LICENSE", "META-INF/LICENSE-KING-MUSIC.txt")
            archive.write(ROOT / "AI_USAGE_NOTICE.md", "META-INF/KING-MUSIC-AI-NOTICE.md")
        room_protocol = ROOT / "together-network/src"
        addons = [
            build_addon(ROOT / "addon", "King-Music-Together", "king_music_together", [classes, *dependencies], room_protocol),
            build_addon(ROOT / "lyrics", "King-Music-Lyrics", "king_music_lyrics", [classes, *dependencies]),
            build_addon(ROOT / "visualizer", "King-Music-Visualizer", "king_music_visualizer", [classes, *dependencies]),
            build_addon(ROOT / "together-server", "King-Music-Together-Server", "king_music_together_server", dependencies, room_protocol),
        ]
    official = ROOT / "official"
    official.mkdir(exist_ok=True)
    # Preserve verified hashes of previously published files: changing them
    # would incorrectly flag existing users' installed add-ons as unofficial.
    # This release only publishes a new core JAR, not rebuilt add-ons.
    manifest_path = official / "manifest.txt"
    entries = {}
    if manifest_path.is_file():
        for line in manifest_path.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            digest, filename = line.split("  ", 1)
            if not (len(digest) == 64 and all(c in "0123456789abcdef" for c in digest)):
                raise ValueError("Invalid existing release hash")
            if not filename.startswith("King-Music-") or not filename.endswith(".jar"):
                raise ValueError("Invalid existing release filename")
            entries[filename] = digest
    if target.name in entries:
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        if entries[target.name] != digest:
            raise RuntimeError("Refusing to replace an existing published version with different bytes")
    else:
        entries[target.name] = hashlib.sha256(target.read_bytes()).hexdigest()
    # Keep the latest core first to make old update detectors see 0.5.16.
    latest = target.name
    ordered = [latest, *sorted(name for name in entries if name != latest)]
    manifest = "# King Music official release files\n" + "".join(
        f"{entries[name]}  {name}\n" for name in ordered
    )
    manifest_path.write_text(manifest, encoding="utf-8", newline="\n")
    if SIGNING_KEY.is_file():
        subprocess.run(["javac", str(ROOT / "tools" / "ReleaseSigner.java")], check=True)
        subprocess.run(["java", "-cp", str(ROOT / "tools"), "ReleaseSigner", "sign",
            str(official / "manifest.txt"), str(official / "manifest.sig")], check=True)
    print(target)
    for addon in addons:
        print(addon)


if __name__ == "__main__":
    main()
