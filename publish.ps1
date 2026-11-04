# Publishes the current F8Q instance: the mod list, the files Modrinth doesn't have and the settings.
# Players get the mods at their next start; the settings only reach new installs.
$ErrorActionPreference = 'Stop'
$repo = 'hlnsay/f8q-modpack'
$base = "https://github.com/$repo/releases/download/files"
$home_dir = Join-Path $env:APPDATA 'horizon\instances\cozy-autumn-neoforge'
$updater = Join-Path $home_dir 'f8q-updater.exe'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$here = $PSScriptRoot
$out = Join-Path $env:TEMP 'f8q-publish'

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
& $updater publish $home_dir $out $base
if ($LASTEXITCODE -ne 0) { throw 'publish failed' }
& $updater starter $home_dir (Join-Path $out 'upload\f8q-starter.zip')
if ($LASTEXITCODE -ne 0) { throw 'starter failed' }
Copy-Item $updater (Join-Path $out 'upload\f8q-updater.exe')

$files = @(Get-ChildItem (Join-Path $out 'upload') -File | ForEach-Object FullName)
& $gh release upload files @files --repo $repo --clobber
if ($LASTEXITCODE -ne 0) { throw 'upload failed' }

Copy-Item (Join-Path $out 'manifest.json') (Join-Path $here 'manifest.json') -Force
Set-Location $here
git add manifest.json
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) { Write-Output 'The list did not change.'; exit 0 }
git commit -q -m 'Update the mod list'
git push -q
if ($LASTEXITCODE -ne 0) { throw 'push failed' }
Write-Output 'Published.'
