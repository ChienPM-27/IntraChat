param([string]$JavaHome='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java=Join-Path $JavaHome 'bin\java.exe'
$jar=Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
$source=Join-Path $PSScriptRoot 'P2GroupProcessSmoke.java'
if(-not(Test-Path $jar) -or -not(Test-Path $java)){throw 'Build JAR / Java 21 first'}
$tmp=Join-Path ([IO.Path]::GetTempPath()) ('intrachat-p2-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($tmp)
$portFinder=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$portFinder.Start()
$port=([Net.IPEndPoint]$portFinder.LocalEndpoint).Port
$portFinder.Stop()
$jobs=@()
try{
    foreach($role in @('A','B','C')){
        $output=Join-Path $tmp "$role.out"
        $errorFile=Join-Path $tmp "$role.err"
        $directory=Join-Path $tmp "profile-$role"
        $argsLine='-cp "'+$jar+'" "'+$source+'" '+$role+' '+$port+' "'+$directory+'"'
        $job=Start-Process -FilePath $java -ArgumentList $argsLine -PassThru -RedirectStandardOutput $output -RedirectStandardError $errorFile
        $jobs+=@{role=$role;process=$job;out=$output;err=$errorFile}
        if($role -eq 'A'){Start-Sleep -Seconds 4}
        else{Start-Sleep -Milliseconds 700}
    }
    foreach($job in $jobs){
        $proc=$job.process
        if(-not $proc.WaitForExit(45000)){throw "Timed out: $($job.role)"}
        $proc.Refresh()
        $content=Get-Content $job.out -Raw
        Write-Host "$($job.role): exit=$($proc.ExitCode) stderr=$((Get-Item $job.err).Length) output=$content"
        if(($null -ne $proc.ExitCode -and $proc.ExitCode -ne 0) -or (Get-Item $job.err).Length -gt 0 -or
            $content -notmatch "PASS_"){throw "P2 three-JVM smoke FAILED: $($job.role), evidence $tmp"}
    }
    $trace=@{}
    $seen=0
    foreach($role in @('A','B','C')){
        foreach($line in (Get-Content (Join-Path $tmp "$role.out"))){
            if($line -notmatch '^TRACE\|') { continue }
            $fields=$line.Split('|')
            if($fields.Count -ne 7 -or $fields[1] -ne $role){throw "Malformed TRACE: $line"}
            $key=$fields[2]
            $canonical=($fields[3..6] -join '|')
            if($trace.ContainsKey($key) -and $trace[$key] -ne $canonical){throw "Message metadata mismatched across JVMs for $key"}
            $trace[$key]=$canonical
            $seen++
        }
    }
    if($seen -ne 9 -or $trace.Count -ne 3){throw "Missing cross-JVM message traces: $seen"}
    Write-Host '[PASS] A/B/C same groupId, original messageId, senderId and UTC timestamp for all 3 texts'
    $ids=@()
    foreach($role in @('A','B','C')){
        $profile=Get-Content (Join-Path $tmp "profile-$role\profile.json") -Raw | ConvertFrom-Json
        $ids+=@($profile.deviceId)
    }
    if(@($ids|Select-Object -Unique).Count -ne 3){throw 'Duplicate profile UUID'}
    Write-Host '[PASS] Three independent Java 21 processes; owner relay A/B/C, Unicode, sender, recipient ACK'
} finally{
    foreach($job in $jobs){
        $proc=$job.process
        $proc.Refresh()
        if(-not $proc.HasExited){Stop-Process -Id $proc.Id -Force}
        $proc.Dispose()
    }
    Write-Host "Evidence directory: $tmp"
}
