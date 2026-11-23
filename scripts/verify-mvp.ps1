param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipProcessSmoke
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'Set JAVA_HOME to JDK 21, or pass -JavaHome.'
}
$java = Join-Path $JavaHome 'bin\java.exe'
$jarTool = Join-Path $JavaHome 'bin\jar.exe'
if (-not (Test-Path $java) -or -not (Test-Path $jarTool)) {
    throw "JDK binaries missing from $JavaHome"
}
$previousPreference=$ErrorActionPreference
$ErrorActionPreference='Continue'
try { $version = (& $java -version 2>&1 | Out-String) }
finally { $ErrorActionPreference=$previousPreference }
if ($version -notmatch 'version "21\.') {
    throw "JDK 21 required; found $version"
}
$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH
Push-Location $repo
try {
    & (Join-Path $repo 'mvnw.cmd') -o clean test package
    if ($LASTEXITCODE -ne 0) { throw 'Maven clean test package FAILED' }
    $jar = Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path $jar)) { throw 'Executable JAR not found' }
    $classes = @(& $jarTool tf $jar)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect JAR' }
    foreach ($required in @(
        'ui/IntraChat.class',
        'vn/intrachat/application/LocalProfile.class',
        'vn/intrachat/application/LanCoordinator.class',
        'vn/intrachat/application/LiveChatService.class',
        'vn/intrachat/protocol/WireCodec.class',
        'vn/intrachat/protocol/WireMessage.class',
        'vn/intrachat/transport/LanSession.class'
    )) {
        if ($classes -notcontains $required) { throw "Missing class in JAR: $required" }
    }
    foreach ($forbidden in @('vn/intrachat/security/', 'TlsChatSession.class',
        'SecureChatCoordinator.class', 'com/sun/jna/', 'org/bouncycastle/')) {
        if (@($classes | Where-Object { $_.Contains($forbidden) }).Count -gt 0) {
            throw "Legacy class/dependency still packaged: $forbidden"
        }
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $entry=$zip.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $entry) { throw 'Missing JAR manifest' }
        $reader=[System.IO.StreamReader]::new($entry.Open())
        try { $manifest=$reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($manifest -notmatch 'Main-Class: ui.IntraChat') {
            throw 'JAR does not launch ui.IntraChat'
        }
    } finally { $zip.Dispose() }
    Write-Host '[PASS] JDK 21 Maven tests, package, manifest and no legacy class/dependency'

    if (-not $SkipProcessSmoke) {
        $smoke = Join-Path $PSScriptRoot 'P1ProcessSmoke.java'
        if (-not (Test-Path $smoke)) { throw 'Missing 2-JVM TCP smoke harness' }
        $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('intrachat-p1-verify-'+[guid]::NewGuid().ToString('N'))
        [void][System.IO.Directory]::CreateDirectory($tmp)
        $a = Join-Path $tmp 'profile-A'
        $b = Join-Path $tmp 'profile-B'
        $portFinder=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0)
        $portFinder.Start()
        $port=([System.Net.IPEndPoint]$portFinder.LocalEndpoint).Port
        $portFinder.Stop()
        # Start-Process receives one command-line string. Quote absolute paths safely.
        $quotedJar='"'+$jar+'"'
        $quotedSource='"'+$smoke+'"'
        $quotedA='"'+$a+'"'
        $quotedB='"'+$b+'"'
        $listener=$null
        $connector=$null
        try {
            $listener=Start-Process -FilePath $java -ArgumentList @(
                '--class-path',$quotedJar,$quotedSource,'listen',"$port",$quotedA
            ) -PassThru -RedirectStandardOutput (Join-Path $tmp 'listener.out') -RedirectStandardError (Join-Path $tmp 'listener.err')
            Start-Sleep -Seconds 4
            $connector=Start-Process -FilePath $java -ArgumentList @(
                '--class-path',$quotedJar,$quotedSource,'connect',"$port",$quotedB
            ) -PassThru -RedirectStandardOutput (Join-Path $tmp 'connector.out') -RedirectStandardError (Join-Path $tmp 'connector.err')
            if (-not $connector.WaitForExit(30000)) { throw 'Connector process timed out' }
            if (-not $listener.WaitForExit(30000)) { throw 'Listener process timed out' }
            $connector.Refresh();$listener.Refresh()
            $listenerOutput=Get-Content (Join-Path $tmp 'listener.out') -Raw
            $connectorOutput=Get-Content (Join-Path $tmp 'connector.out') -Raw
            if (-not $listener.HasExited -or -not $connector.HasExited -or
                ($null -ne $listener.ExitCode -and $listener.ExitCode -ne 0) -or
                ($null -ne $connector.ExitCode -and $connector.ExitCode -ne 0) -or
                $listenerOutput -notmatch 'PASS_LISTENER' -or $connectorOutput -notmatch 'PASS_CONNECTOR' -or
                (Get-Item (Join-Path $tmp 'listener.err')).Length -ne 0 -or
                (Get-Item (Join-Path $tmp 'connector.err')).Length -ne 0) {
                throw "2-JVM smoke FAILED. Evidence: $tmp"
            }
            $profileA=Get-Content (Join-Path $a 'profile.json') -Raw | ConvertFrom-Json
            $profileB=Get-Content (Join-Path $b 'profile.json') -Raw | ConvertFrom-Json
            if ($profileA.deviceId -eq $profileB.deviceId) { throw 'Two dataDirs returned same UUID' }
            Write-Host '[PASS] Two independent JVMs, socket, Unicode, reply, ACK, READ, isolated UUIDs'
        } finally {
            foreach ($process in @($listener,$connector)) {
                if ($null -ne $process) {
                    $process.Refresh()
                    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
                    $process.Dispose()
                }
            }
        }
    }
    Write-Host '[PASS] P1 verification complete (GUI and physical LAN require manual acceptance)'
} finally {
    Pop-Location
}
