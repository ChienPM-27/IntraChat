param(
    [string]$JavaHome='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1',
    [switch]$SkipBuild
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java=Join-Path $JavaHome 'bin\java.exe'
$jar=Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
$source=Join-Path $PSScriptRoot 'P4HistoryProcessSmoke.java'
if(-not(Test-Path $java)){throw 'Missing JDK 21'}
Push-Location $repo
try {
    if(-not $SkipBuild){
        $env:JAVA_HOME=$JavaHome
        $env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
        & (Join-Path $repo 'mvnw.cmd') -o clean test package
        if($LASTEXITCODE -ne 0){throw 'P4 Maven build failed'}
    }
    if(-not(Test-Path $jar)){throw 'Missing JAR'}
    $tmp=Join-Path ([IO.Path]::GetTempPath()) ('intrachat-p4-'+[guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($tmp)
    $classes=Join-Path $tmp 'classes'
    [void][IO.Directory]::CreateDirectory($classes)
    & (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $jar -d $classes $source (Join-Path $PSScriptRoot 'P2GroupProcessSmoke.java')
    if($LASTEXITCODE -ne 0){throw 'P4 harness javac failed'}
    $runCp=$jar+';'+$classes
    $a=Join-Path $tmp 'profile-A'
    $b=Join-Path $tmp 'profile-B'
    function Invoke-Restore([string]$mode,[string]$dir,[string]$role){
        $previous=$ErrorActionPreference
        $ErrorActionPreference='Continue'
        try { $result=(& $java -cp $runCp P4HistoryProcessSmoke $mode 0 $dir $role 2>&1 | Out-String) }
        finally { $ErrorActionPreference=$previous }
        if($LASTEXITCODE -ne 0 -or $result -notmatch 'PASS_'){
            throw "$mode $role failed: $result"
        }
        Write-Host $result.Trim()
    }
    function Invoke-Conversation([string]$server,[string]$client){
        $finder=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
        $finder.Start();$port=([Net.IPEndPoint]$finder.LocalEndpoint).Port;$finder.Stop()
        $jobs=@()
        try {
            foreach($item in @(@{mode=$server;dir=$b},@{mode=$client;dir=$a})){
                $stdout=Join-Path $tmp ($item.mode+'.out')
                $stderr=Join-Path $tmp ($item.mode+'.err')
                $argsLine='-cp "'+$runCp+'" P4HistoryProcessSmoke '+$item.mode+' '+$port+' "'+$item.dir+'"'
                Write-Host "START mode=$($item.mode) dir=$($item.dir) command=$argsLine"
                $proc=Start-Process -FilePath $java -ArgumentList $argsLine -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
                Write-Host "PID $($proc.Id)"
                $jobs+=@{process=$proc;mode=$item.mode;out=$stdout;err=$stderr}
                if($item.mode -eq $server){Start-Sleep -Seconds 3}
            }
            foreach($job in $jobs){
                if(-not $job.process.WaitForExit(45000)){
                    foreach($scan in $jobs){Write-Host "TRACE $($scan.mode) out=$(Get-Content $scan.out -Raw) err=$(Get-Content $scan.err -Raw)"}
                    throw "P4 timeout $($job.mode)"
                }
                $job.process.Refresh()
                $output=Get-Content $job.out -Raw
                $errorText=Get-Content $job.err -Raw
                Write-Host "$($job.mode): $output"
                if(($null -ne $job.process.ExitCode -and $job.process.ExitCode -ne 0) -or
                    $output -notmatch 'PASS_' -or -not [string]::IsNullOrWhiteSpace($errorText)){
                    throw "P4 $($job.mode) failed: $errorText $tmp"
                }
            }
        }finally{
            foreach($job in $jobs){
                $proc=$job.process;$proc.Refresh()
                if(-not $proc.HasExited){Stop-Process -Id $proc.Id -Force}
                $proc.Dispose()
            }
        }
    }
    $initial=(& $java -cp $runCp P4HistoryProcessSmoke orchestrate 0 $tmp initial 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0 -or $initial -notmatch 'PASS_TWO_JVM_INITIAL'){throw "P4 initial process smoke: $initial"}
    Write-Host $initial
    foreach($role in @('A','B')){
        $dir=if($role -eq 'A'){$a}else{$b}
        Invoke-Restore 'restore' $dir $role
    }
    $pa=Get-Content (Join-Path $a 'profile.json') -Raw|ConvertFrom-Json
    $pb=Get-Content (Join-Path $b 'profile.json') -Raw|ConvertFrom-Json
    if($pa.deviceId -eq $pb.deviceId){throw 'DataDir profiles share UUID'}
    $aJson=(Get-Content (Join-Path $a 'history\messages.jsonl') -Raw)
    $bJson=(Get-Content (Join-Path $b 'history\messages.jsonl') -Raw)
    if($aJson -notmatch 'Xin chào B' -or $bJson -notmatch 'Xin chào B' -or $aJson -match 'dataBase64'){
        throw 'P4 not persisted to separate JSONL'
    }
    Write-Host '[PASS] two JVMs written to separate disks + two independent JVM restarts + ACK/READ + file completed'
    Invoke-Restore 'offline' $a 'A'
    $continued=(& $java -cp $runCp P4HistoryProcessSmoke orchestrate 0 $tmp continue 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0 -or $continued -notmatch 'PASS_TWO_JVM_CONTINUED'){throw "P4 continuation process smoke: $continued"}
    Write-Host $continued
    Invoke-Restore 'restore' $a 'A'
    Invoke-Restore 'restore' $b 'B'
    Write-Host '[PASS] original UUID/conversationId, duplicate replay, continue after restart'
    $damaged=Join-Path $tmp 'damaged-A'
    [void][IO.Directory]::CreateDirectory($damaged)
    Copy-Item -Path (Join-Path $a '*') -Destination $damaged -Recurse
    Add-Content -Path (Join-Path $damaged 'history\messages.jsonl') -Value '{BROKEN' -Encoding UTF8
    Invoke-Restore 'restore' $damaged 'A'
    Write-Host '[PASS] valid history survives appended malformed record'
    $received=Join-Path $b 'received-p4-sample.dat'
    if(-not(Test-Path $received)){throw 'Receiver file not present'}
    Move-Item $received (Join-Path $tmp 'moved-p4-sample.dat')
    Invoke-Restore 'missing' $b 'B'
    Write-Host '[PASS] missing attachment metadata survives without crash'
    # Three independent JVM Group runs and restarts; keep the first evidence directory.
    $groupDir=Join-Path $tmp 'group-1'
    foreach($run in 1..3){
        $current=Join-Path $tmp "group-$run"
        $groupOutput=(& $java -cp $runCp P4HistoryProcessSmoke orchestrate-group 0 $current 2>&1 | Out-String)
        if($LASTEXITCODE -ne 0 -or $groupOutput -notmatch 'PASS_THREE_JVM_GROUP'){
            throw "Group process run $run failed; evidence: $current; details: $groupOutput"
        }
        Write-Host "[PASS] P2 three-JVM group regression run $run"
    }
    foreach($role in @('A','B','C')){
        Invoke-Restore 'restore-group' (Join-Path $groupDir "profile-$role") $role
    }
    Write-Host '[PASS] three-JVM group persisted original metadata and three independent restart JVMs'
    Write-Host "Evidence directory: $tmp"
    Write-Host "Group evidence directory: $groupDir"
    Write-Host '[PASS] P4 backend multi-JVM persistence/restart smoke (GUI/LAN physical NOT RUN)'
}finally{Pop-Location}
