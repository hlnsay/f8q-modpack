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
$item = Get-Item $setup
$info = [ordered]@{
    build  = $build
    url    = "https://github.com/$repo/releases/download/files/F8Q-Launcher-Setup.exe"
    size   = $item.Length
    sha512 = (Get-FileHash $setup -Algorithm SHA512).Hash.ToLower()
}
& $gh release upload files $setup --repo $repo --clobber
if ($LASTEXITCODE -ne 0) { throw 'upload failed' }

$json = Join-Path $here 'launcher.json'
[IO.File]::WriteAllText($json, ($info | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
Set-Location $here
git add launcher.json
git commit -q -m "Publish launcher build $build"
git push -q
if ($LASTEXITCODE -ne 0) { throw 'push failed' }
Write-Output "Published launcher build $build."
