# Run from PowerShell 7; source folder is the directory holding this script.
$ErrorActionPreference = 'Stop'
$src = $PSScriptRoot
$dst = 'C:\Users\User\Desktop\king-music-release'
$repo = 'King220l/king-music'
$jarName = 'King-Music-0.5.16-Fabric-26.2.jar'
function CheckExit([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE)" } }
if (!(Test-Path (Join-Path $src 'build.py'))) { throw 'Source project not found' }
if (!(Test-Path (Join-Path $dst '.git'))) { throw "Git checkout not found: $dst" }
if (!(Test-Path (Join-Path $HOME '.kingmusic\release-signing.pk8'))) { throw 'Release signing key not found; cannot publish unsigned build' }
$origin = (git -C $dst remote get-url origin).Trim()
CheckExit 'Read remote'
if ($origin -notmatch '(^|[/:])King220l/king-music(\.git)?$') { throw "Unexpected Git remote: $origin" }
if ((git -C $dst branch --show-current).Trim() -ne 'main') { throw 'Checkout must be on main' }
if (git -C $dst status --porcelain) { throw 'Git checkout has uncommitted changes; resolve them first' }
Set-Location $src
& (Join-Path $src 'wave-tests\run-tests.ps1')
CheckExit 'Tests'
$env:KING_MUSIC_REPLACE_RELEASE = 'I_UNDERSTAND'
python (Join-Path $src 'build.py')
CheckExit 'Build'
$jar = Join-Path $src "build\$jarName"
$manifest = Join-Path $src 'official\manifest.txt'
$sig = Join-Path $src 'official\manifest.sig'
$hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if (!(Select-String -Path $manifest -Pattern "^$hash  $([regex]::Escape($jarName))$" -Quiet)) { throw 'JAR hash is not in signed manifest' }
java -cp (Join-Path $src 'tools') ReleaseSigner verify $manifest $sig
CheckExit 'Signature verification'
git -C $dst pull --ff-only origin main
CheckExit 'Git pull'
$files = @(
  'src/studio/king220l/yamusic/MusicApi.java',
  'src/studio/king220l/yamusic/MusicPlayer.java',
  'src/studio/king220l/yamusic/WaveHistory.java',
  'src/studio/king220l/yamusic/WaveDiagnostics.java',
  'official/manifest.txt', 'official/manifest.sig', 'build.py',
  'PUBLISH_0.5.16_WAVE.ps1',
  'WAVE_ROTOR_0.5.16_NOTES_RU.md'
)
foreach ($f in $files) {
    $from = Join-Path $src $f
    $to = Join-Path $dst $f
    if (!(Test-Path $from)) { throw "Missing source: $f" }
    Copy-Item -LiteralPath $from -Destination $to -Force
}
Set-Location $dst
git add -- $files
CheckExit 'Git add'
git diff --cached --check
CheckExit 'Whitespace checks'
git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {

  git diff --cached --stat
  git commit -m 'Hotfix 0.5.16 - Rotor feedback and queued track history'
  CheckExit 'Commit'
  git push origin main
  CheckExit 'Push'
} else { Write-Host 'No source changes to commit.' }
gh release upload v0.5.16 $jar --repo $repo --clobber
CheckExit 'Release upload'
$asset = (gh release view v0.5.16 --repo $repo --json assets | ConvertFrom-Json).assets | Where-Object name -eq $jarName
CheckExit 'Read release assets'
if ($asset.digest -ne "sha256:$hash") { throw "Remote digest mismatch. Local: $hash; remote: $($asset.digest)" }
Write-Host "SUCCESS: KING Music v0.5.16 updated. SHA-256: $hash" -ForegroundColor Green
