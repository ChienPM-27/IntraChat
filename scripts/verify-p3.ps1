param([string]$JavaHome='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java=Join-Path $JavaHome 'bin\java.exe'
$jar=Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
$source=Join-Path $PSScriptRoot 'P3FileProcessSmoke.java'
if(-not(Test-Path $jar) -or -not(Test-Path $java)){throw 'Build JAR / Java 21 first'}
$tmp=Join-Path ([IO.Path]::GetTempPath()) ('intrachat-p3-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($tmp)
$finder=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$finder.Start()
$port=([Net.IPEndPoint]$finder.LocalEndpoint).Port
$finder.Stop()
$jobs=@()
try{
    foreach($role in @('B','A')){
        $output=Join-Path $tmp "$role.out"
        $errorFile=Join-Path $tmp "$role.err"
        $directory=Join-Path $tmp "profile-$role"
        $line='-cp "'+$jar+'" "'+$source+'" '+$role+' '+$port+' "'+$directory+'"'
        $job=Start-Process -FilePath $java -ArgumentList $line -PassThru -RedirectStandardOutput $output -RedirectStandardError $errorFile
        $jobs+=@{role=$role;process=$job;out=$output;err=$errorFile}
        if($role -eq 'B'){Start-Sleep -Seconds 4}
    }
    foreach($job in $jobs){
        if(-not $job.process.WaitForExit(110000)){throw "P3 timeout $($job.role)"}
        $job.process.Refresh()
        $output=Get-Content $job.out -Raw
        Write-Host "$($job.role): code=$($job.process.ExitCode) output=$output"
        if(($null -ne $job.process.ExitCode -and $job.process.ExitCode -ne 0) -or (Get-Item $job.err).Length -ne 0 -or $output -notmatch 'PASS_DISCONNECT'){
            Write-Host (Get-Content $job.err -Raw)
            throw "P3 process failed $($job.role): $tmp"
        }
    }
    foreach($name in @('small.bin','many.bin','Tiếng Việt có dấu 🇻🇳.bin')){
        $src=Join-Path $tmp "profile-A\$name"
        $dst=Join-Path $tmp "profile-B\received-$name"
        if((Get-FileHash -Algorithm SHA256 $src).Hash -ne (Get-FileHash -Algorithm SHA256 $dst).Hash){
            throw "P3 hash mismatch: $name"
        }
        Write-Host "[PASS] SHA256 identical: $name"
    }
    $profiles=@('A','B')|ForEach-Object{(Get-Content (Join-Path $tmp "profile-$_\profile.json") -Raw|ConvertFrom-Json).deviceId}
    if($profiles[0] -eq $profiles[1]){throw 'Same local device ID'}
    Write-Host '[PASS] independent JVMs, TCP file transfer, CRC, SHA256, reject, cancel, disconnect, text'
} finally{
    foreach($job in $jobs){
        $proc=$job.process;$proc.Refresh()
        if(-not $proc.HasExited){Stop-Process -Id $proc.Id -Force}
        $proc.Dispose()
    }
    Write-Host "Evidence directory: $tmp"
}
