[CmdletBinding()]
param([ValidatePattern('^[0-9]{8}-[0-9]{6}-[a-f0-9]{8}$')][string]$Session)
$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot 'data\_gui-bots'
if (!(Test-Path -LiteralPath $root -PathType Container)) { Write-Host 'No GUI bot sessions.'; return }
$directories = @(Get-ChildItem -LiteralPath $root -Directory)
if ($Session) { $directories = @($directories | Where-Object Name -eq $Session) }
foreach ($file in $directories | ForEach-Object { Join-Path $_.FullName 'bots.json' }) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { continue }
    foreach ($bot in @(Get-Content -LiteralPath $file -Raw | ConvertFrom-Json)) {
        $process = Get-Process -Id ([int]$bot.pid) -ErrorAction SilentlyContinue
        if (!$process) { continue }
        try {
            # Never stop a reused PID or a normal IntraChat instance.
            if ($process.ProcessName -notin @('java','javaw') -or
                $process.StartTime.ToUniversalTime() -ne [DateTimeOffset]::Parse($bot.startedUtc).UtcDateTime) { continue }
            $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)" -ErrorAction Stop
            if (!$details.CommandLine -or !$details.CommandLine.Contains('-Dintrachat.testBot=true') -or
                !$details.CommandLine.Contains("-Dintrachat.dataDir=$($bot.profile)")) { continue }
            Write-Host "Stopping $($bot.name) (PID $($process.Id))"
            $null = $process.CloseMainWindow()
            if (!$process.WaitForExit(5000)) {
                $process.Kill()
                $null = $process.WaitForExit(5000)
            }
        } finally { $process.Dispose() }
    }
}
Write-Host 'Bot profiles and logs are retained; normal users were not stopped.'
