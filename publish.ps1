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
$before = @((Get-Content $listPath -Raw | ConvertFrom-Json).files | Where-Object { $_.path -like 'mods/*' })
$after = @((Get-Content (Join-Path $out 'manifest.json') -Raw | ConvertFrom-Json).files | Where-Object { $_.path -like 'mods/*' })
$old = @{}; foreach ($m in $before) { $old[(Title $m.path)] = $m.sha512 }
$new = @{}; foreach ($m in $after) { $new[(Title $m.path)] = $m.sha512 }
$added = @($new.Keys | Where-Object { -not $old.ContainsKey($_) } | Sort-Object)
$removed = @($old.Keys | Where-Object { -not $new.ContainsKey($_) } | Sort-Object)
$updated = @($new.Keys | Where-Object { $old.ContainsKey($_) -and $old[$_] -ne $new[$_] } | Sort-Object)
if ($added.Count + $removed.Count + $updated.Count -gt 0) {
    $logPath = Join-Path $here 'changelog.json'
    $log = @(Get-Content $logPath -Raw | ConvertFrom-Json)
    $entry = [pscustomobject]@{ date = (Get-Date -Format 'yyyy-MM-dd'); added = $added; updated = $updated; removed = $removed }
    $json = ConvertTo-Json -InputObject @(@($entry) + $log) -Depth 4
    [IO.File]::WriteAllText($logPath, $json, (New-Object Text.UTF8Encoding $false))
}
Copy-Item (Join-Path $out 'manifest.json') (Join-Path $here 'manifest.json') -Force
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
