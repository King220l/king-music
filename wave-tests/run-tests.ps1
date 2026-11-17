$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $PSScriptRoot "build"
New-Item -ItemType Directory -Force $out | Out-Null
javac -d $out "$PSScriptRoot/tools/stubs/studio/king220l/yamusic/MusicApi.java" "$root/src/studio/king220l/yamusic/WaveHistory.java" "$PSScriptRoot/src/test/java/studio/king220l/yamusic/WaveHistoryTest.java"
if ($LASTEXITCODE -ne 0) { throw "Java test compilation failed" }
java -cp $out studio.king220l.yamusic.WaveHistoryTest
if ($LASTEXITCODE -ne 0) { throw "Wave tests failed" }
