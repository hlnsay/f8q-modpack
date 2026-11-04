# Publishes the current F8Q instance: the mod list, the settings and the files Modrinth doesn't have.
# Players get what changed or was removed through the launcher's Update button or their next Play.
$ErrorActionPreference = 'Stop'
$repo = 'hlnsay/f8q-modpack'
$base = "https://github.com/$repo/releases/download/files"
$home_dir = Join-Path $env:APPDATA 'horizon\instances\cozy-autumn-neoforge'
$updater = Join-Path $home_dir 'f8q-updater.exe'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$here = $PSScriptRoot
$out = Join-Path $env:TEMP 'f8q-publish'

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
& $updater publish $home_dir $out $base | Write-Output
if ($LASTEXITCODE -ne 0) { throw 'publish failed' }
& $updater starter $home_dir (Join-Path $out 'upload\f8q-starter.zip') | Write-Output
if ($LASTEXITCODE -ne 0) { throw 'starter failed' }
Copy-Item $updater (Join-Path $out 'upload\f8q-updater.exe')

$files = @(Get-ChildItem (Join-Path $out 'upload') -File | ForEach-Object FullName)
& $gh release upload files @files --repo $repo --clobber
if ($LASTEXITCODE -ne 0) { throw 'upload failed' }

Copy-Item (Join-Path $out 'manifest.json') (Join-Path $here 'manifest.json') -Force
$config = Join-Path $here 'config'
if (Test-Path $config) { Remove-Item $config -Recurse -Force }
Copy-Item (Join-Path $out 'config') $config -Recurse
Set-Location $here
git add manifest.json config .gitattributes
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) { Write-Output 'The list did not change.'; exit 0 }
git commit -q -m 'Update the mod list'
git push -q
if ($LASTEXITCODE -ne 0) { throw 'push failed' }
Write-Output 'Published.'
