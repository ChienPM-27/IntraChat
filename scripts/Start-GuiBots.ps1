[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Java,
    [Parameter(Mandatory=$true)][string]$Jar,
    [ValidateRange(1,4)][int]$Count = 3
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$root = Join-Path $repo 'data\_gui-bots'
$session = Join-Path $root ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $session -Force | Out-Null
# Launch from a session-local snapshot, never lock the normal build artifact.
$snapshot = Join-Path $session 'intrachat.jar'
Copy-Item -LiteralPath $Jar -Destination $snapshot
$Jar = $snapshot
$ports = @()
$reservations = @()
$started = @()
$manifest = Join-Path $session 'bots.json'
# Windows PowerShell 5.1 has no ConvertTo-Json -AsArray.
function Write-BotManifest {
    ConvertTo-Json -InputObject @($started) -Depth 4 | Set-Content -LiteralPath $manifest -Encoding UTF8
}
try {
    # Reserve distinct free loopback ports until all profiles are prepared.
    for ($i=0; $i -lt $Count; $i++) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
        $listener.Start()
        $reservations += $listener
        $ports += $listener.LocalEndpoint.Port
    }
    & $Java -cp $Jar (Join-Path $PSScriptRoot 'GuiBotSeed.java') $session @ports
    if ($LASTEXITCODE -ne 0) { throw "Bot profile setup failed (exit $LASTEXITCODE)." }
    foreach ($listener in $reservations) { $listener.Stop() }
    $reservations = @()
    for ($i=0; $i -lt $Count; $i++) {
        $name = 'Bot-{0:00}' -f ($i+1)
        $profile = Join-Path $session $name
        $args = @(('"-Dintrachat.dataDir='+$profile+'"'),("-Dintrachat.user=$name"),'-Dintrachat.testBot=true','-jar',('"'+$Jar+'"'))
        $process = Start-Process -FilePath $Java -ArgumentList $args -WorkingDirectory $repo -PassThru -RedirectStandardOutput (Join-Path $session "$name.out.log") -RedirectStandardError (Join-Path $session "$name.err.log")
        $started += [pscustomobject]@{ name=$name; pid=$process.Id; startedUtc=$process.StartTime.ToUniversalTime().ToString('o'); port=$ports[$i]; profile=$profile }
        Write-BotManifest
        $process.Dispose()
    }
    Write-Host "GUI bot session: $session"
    $started | Select-Object name,pid,@{Name='IPv4';Expression={'127.0.0.1'}},port | Format-Table -AutoSize
    Write-Host 'Connect manually in LAN settings and approve the real handshake. No automatic LAN scanning.'
} finally {
    foreach ($listener in $reservations) { $listener.Stop() }
    if ($started.Count -gt 0) { Write-BotManifest }
}
