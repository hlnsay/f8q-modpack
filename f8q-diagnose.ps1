# Starts F8Q the way the launcher does and saves why it failed to the Desktop.
$h = "$env:APPDATA\horizon\instances\cozy-autumn-neoforge"
$out = "$env:USERPROFILE\Desktop\f8q-report.txt"
$dirs = @()
Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -like 'F8Q*' -and $_.InstallLocation } | ForEach-Object { $dirs += $_.InstallLocation }
$dirs += "$env:LOCALAPPDATA\Programs\F8Q Launcher", "$env:LOCALAPPDATA\F8Q", "$env:LOCALAPPDATA\Programs\F8Q"
$engine = $dirs | ForEach-Object { Join-Path $_ 'engine\horizon-launcher.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
$r = New-Object Collections.ArrayList
[void]$r.Add("engine: $engine")
[void]$r.Add("windows: $((Get-CimInstance Win32_OperatingSystem).Caption)")
[void]$r.Add("gpu: $((Get-CimInstance Win32_VideoController).Name -join ', ')")
[void]$r.Add("ram GB: $([math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB))")
[void]$r.Add("instance folder: $(Test-Path $h)")
[void]$r.Add("updater: $(if(Test-Path "$h\f8q-updater.exe"){(Get-Item "$h\f8q-updater.exe").Length})")
[void]$r.Add("mods: $((Get-ChildItem "$h\mods" -Filter *.jar -ErrorAction SilentlyContinue).Count)")
if ($engine) {
    $env:HORIZON_LAUNCHER_NO_SELF_UPDATE = '1'
    $o = "$env:TEMP\f8q-play.out"; $e = "$env:TEMP\f8q-play.err"
    $p = Start-Process $engine -ArgumentList 'play', '--instance', 'cozy-autumn-neoforge' -PassThru -WindowStyle Hidden -RedirectStandardOutput $o -RedirectStandardError $e
    $end = (Get-Date).AddSeconds(150)
    while ((Get-Date) -lt $end -and -not $p.HasExited) { Start-Sleep 2 }
    [void]$r.Add("play finished: $($p.HasExited) code: $($p.ExitCode)")
    [void]$r.Add('--- play output'); Get-Content $o -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { [void]$r.Add($_) }
    [void]$r.Add('--- play errors'); Get-Content $e -Tail 25 -ErrorAction SilentlyContinue | ForEach-Object { [void]$r.Add($_) }
}
[void]$r.Add('--- log'); Get-Content "$h\logs\latest.log" -Tail 25 -Encoding UTF8 -ErrorAction SilentlyContinue | ForEach-Object { [void]$r.Add($_) }
$c = Get-ChildItem "$h\crash-reports" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
if ($c) { [void]$r.Add("--- crash $($c.Name)"); Get-Content $c.FullName -TotalCount 40 -Encoding UTF8 | ForEach-Object { [void]$r.Add($_) } }
$r -replace '(?i)(accessToken[=,: ]+)\S+', '$1***' | Set-Content $out -Encoding UTF8
"Saved: $out"
