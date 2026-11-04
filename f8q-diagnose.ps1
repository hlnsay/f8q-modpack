# Finds the F8Q instance, starts it the way the launcher does and saves why it failed to the Desktop.
$out = "$env:USERPROFILE\Desktop\f8q-report.txt"
$dirs = @()
Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -like 'F8Q*' -and $_.InstallLocation } | ForEach-Object { $dirs += $_.InstallLocation }
$dirs += "$env:LOCALAPPDATA\Programs\F8Q Launcher", "$env:LOCALAPPDATA\F8Q", "$env:LOCALAPPDATA\Programs\F8Q"
$engine = $dirs | ForEach-Object { Join-Path $_ 'engine\horizon-launcher.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
$env:HORIZON_LAUNCHER_NO_SELF_UPDATE = '1'
$r = New-Object Collections.ArrayList
[void]$r.Add("engine: $engine")
[void]$r.Add("gpu: $((Get-CimInstance Win32_VideoController).Name -join ', ')")
$list = @()
if ($engine) {
    $text = & $engine instance list --json 2>&1 | Out-String
    try { $list = @($text | ConvertFrom-Json) } catch { [void]$r.Add("list failed: $text") }
    foreach ($i in $list) {
        [void]$r.Add("instance id=$($i.id) name=$($i.name) kind=$($i.kind) selected=$($i.selected) java=$($i.java | ConvertTo-Json -Compress) home=$($i.home) home_exists=$(Test-Path $i.home)")
    }
    $acc = & $engine account list --json 2>&1 | Out-String
    [void]$r.Add("account signed in: $($acc.Trim().Length -gt 4)")
}
$f8q = $list | Where-Object { $_.java } | Where-Object { $_.name -like 'F8Q*' -or $_.id -like 'cozy*' } | Select-Object -First 1
if (-not $f8q) { [void]$r.Add('NO F8Q JAVA INSTANCE') }
else {
    $h = $f8q.home
    [void]$r.Add("chosen: $($f8q.id)")
    [void]$r.Add("updater: $(if(Test-Path "$h\f8q-updater.exe"){(Get-Item "$h\f8q-updater.exe").Length})")
    [void]$r.Add("mods: $((Get-ChildItem "$h\mods" -Filter *.jar -ErrorAction SilentlyContinue).Count)")
    [void]$r.Add('wrapper: ' + (& $engine instance launch $f8q.id 2>&1 | Out-String))
    $o = "$env:TEMP\f8q-play.out"; $e = "$env:TEMP\f8q-play.err"
    $p = Start-Process $engine -ArgumentList 'play', '--instance', $f8q.id -PassThru -WindowStyle Hidden -RedirectStandardOutput $o -RedirectStandardError $e
    $end = (Get-Date).AddSeconds(150)
    while ((Get-Date) -lt $end -and -not $p.HasExited) { Start-Sleep 2 }
    [void]$r.Add("play finished: $($p.HasExited) code: $($p.ExitCode)")
    [void]$r.Add('--- play output'); Get-Content $o -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { [void]$r.Add($_) }
    [void]$r.Add('--- play errors'); Get-Content $e -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { [void]$r.Add($_) }
    [void]$r.Add('--- log'); Get-Content "$h\logs\latest.log" -Tail 25 -Encoding UTF8 -ErrorAction SilentlyContinue | ForEach-Object { [void]$r.Add($_) }
    $c = Get-ChildItem "$h\crash-reports" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
    if ($c) { [void]$r.Add("--- crash $($c.Name)"); Get-Content $c.FullName -TotalCount 40 -Encoding UTF8 | ForEach-Object { [void]$r.Add($_) } }
}
$r -replace '(?i)(accessToken[=,: ]+)\S+', '$1***' | Set-Content $out -Encoding UTF8
"Saved: $out"
