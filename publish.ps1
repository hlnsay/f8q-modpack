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

# What changed against the published list goes to the top of the changelog.
function Title($path) {
    $name = ($path -split '/')[-1] -replace '\.(jar|zip)$', ''
    $name = $name -replace '[-_](v?\d|mc\d|neoforge).*$', '' -replace '[-_]', ' '
    if ($name.Length -gt 0) { $name.Substring(0, 1).ToUpper() + $name.Substring(1) } else { $name }
}
$listPath = Join-Path $here 'manifest.json'
$beforeManifest = Get-Content $listPath -Raw -Encoding UTF8 | ConvertFrom-Json
$afterManifest = Get-Content (Join-Path $out 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$before = @($beforeManifest.files | Where-Object { $_.path -like 'mods/*' })
$after = @($afterManifest.files | Where-Object { $_.path -like 'mods/*' })
$old = @{}; foreach ($m in $before) { $old[(Title $m.path)] = $m.sha512 }
$new = @{}; foreach ($m in $after) { $new[(Title $m.path)] = $m.sha512 }
$added = @($new.Keys | Where-Object { -not $old.ContainsKey($_) } | Sort-Object)
$removed = @($old.Keys | Where-Object { -not $new.ContainsKey($_) } | Sort-Object)
$updated = @($new.Keys | Where-Object { $old.ContainsKey($_) -and $old[$_] -ne $new[$_] } | Sort-Object)
$oldSettings = @{}; foreach ($f in $beforeManifest.config) { $oldSettings[$f.path] = $f.sha512 }
$settingsChanged = @($afterManifest.config | Where-Object { -not $oldSettings.ContainsKey($_.path) -or $oldSettings[$_.path] -ne $_.sha512 })
$settingsRemoved = @($beforeManifest.config | Where-Object { $_.path -notin @($afterManifest.config.path) })
if ($settingsChanged.Count + $settingsRemoved.Count -gt 0) { $updated += 'Pack settings' }
$oldPacks = @{}; foreach ($f in @($beforeManifest.files | Where-Object { $_.path -like 'resourcepacks/*' })) { $oldPacks[$f.path] = $f.sha512 }
$packsChanged = @($afterManifest.files | Where-Object { $_.path -like 'resourcepacks/*' -and (-not $oldPacks.ContainsKey($_.path) -or $oldPacks[$_.path] -ne $_.sha512) })
$packsRemoved = @($beforeManifest.files | Where-Object { $_.path -like 'resourcepacks/*' -and $_.path -notin @($afterManifest.files.path) })
if ($packsChanged.Count + $packsRemoved.Count -gt 0) { $updated += 'Resource packs' }
$logPath = Join-Path $here 'changelog.json'
$log = @(Get-Content $logPath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($added.Count + $removed.Count + $updated.Count -gt 0) {
    $entry = [pscustomobject]@{ date = (Get-Date -Format 'yyyy-MM-dd'); added = $added; updated = $updated; removed = $removed }
    $json = ConvertTo-Json -InputObject ([object[]](@($entry) + @($log))) -Depth 8
    [IO.File]::WriteAllText($logPath, $json, (New-Object Text.UTF8Encoding $false))
}
# Both panels use one publication snapshot, including settings-only updates.
$log = @(Get-Content $logPath -Raw -Encoding UTF8 | ConvertFrom-Json)
$afterManifest | Add-Member -NotePropertyName changelog -NotePropertyValue @($log) -Force
$afterManifest | Add-Member -NotePropertyName revision -NotePropertyValue (Get-Date -Format 'yyyyMMddTHHmmss') -Force
[IO.File]::WriteAllText($listPath, ($afterManifest | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding $false))
$config = Join-Path $here 'config'
if (Test-Path $config) { Remove-Item $config -Recurse -Force }
Copy-Item (Join-Path $out 'config') $config -Recurse
Set-Location $here
git add manifest.json changelog.json config .gitattributes
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) { Write-Output 'The list did not change.'; exit 0 }
git commit -q -m 'Update the mod list'
git push -q
if ($LASTEXITCODE -ne 0) { throw 'push failed' }
Write-Output 'Published.'
