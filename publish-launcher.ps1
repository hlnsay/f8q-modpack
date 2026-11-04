# Publishes a new launcher: players get it by themselves the next time they open theirs.
# Raise BUILD in horizon\launcher\src\server_home.rs and build it first.
$ErrorActionPreference = 'Stop'
$repo = 'hlnsay/f8q-modpack'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$here = $PSScriptRoot
$root = Split-Path $here -Parent
$setup = Join-Path $root 'F8Q-Launcher-Setup.exe'
$built = 'D:\f8q-launcher-target\release\horizon-launcher.exe'
$source = Join-Path $root 'horizon\launcher\src\server_home.rs'

$build = [int][regex]::Match((Get-Content $source -Raw), 'const BUILD: u32 = (\d+);').Groups[1].Value
if ($build -lt 1) { throw 'no BUILD in server_home.rs' }
Copy-Item $built $setup -Force
$publicVersion = ([IO.File]::ReadAllText((Join-Path $root 'horizon\launcher\F8Q_VERSION'))).Trim()
$versionedSetup = Join-Path $root "F8Q-Launcher-Setup-v$publicVersion.exe"
Copy-Item -LiteralPath $setup -Destination $versionedSetup -Force
$item = Get-Item $setup
$info = [ordered]@{
    build  = $build
    version = ([IO.File]::ReadAllText((Join-Path $root 'horizon\launcher\F8Q_VERSION'))).Trim()
    changelog = (Get-Content (Join-Path $root 'horizon\launcher\F8Q_CHANGELOG.json') -Raw -Encoding UTF8 | ConvertFrom-Json).changelog
    url    = "https://github.com/$repo/releases/download/files/F8Q-Launcher-Setup-v$publicVersion.exe"
    size   = $item.Length
    sha512 = (Get-FileHash $setup -Algorithm SHA512).Hash.ToLower()
}
& $gh release upload files $setup $versionedSetup --repo $repo --clobber
if ($LASTEXITCODE -ne 0) { throw 'upload failed' }

$json = Join-Path $here 'launcher.json'
[IO.File]::WriteAllText($json, ($info | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding $false))
Set-Location $here
git add launcher.json
git commit -q -m "Publish launcher build $build"
git push -q
if ($LASTEXITCODE -ne 0) { throw 'push failed' }
Write-Output "Published launcher build $build."
