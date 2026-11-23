param([string]$JavaHome='C:\Users\XuanHoai\.jdks\jdk-21.0.12.1+1')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java=Join-Path $JavaHome 'bin\java.exe'
$jar=Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
if(-not(Test-Path $jar) -or -not(Test-Path $java)){throw 'Build executable JAR and install JDK 21 first'}
$tmp=Join-Path ([IO.Path]::GetTempPath()) ('intrachat-p5-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($tmp)
Push-Location $repo
try {
    & $java -cp $jar (Join-Path $PSScriptRoot 'P5MediaProcessSmoke.java') run $tmp
    if($LASTEXITCODE -ne 0){throw "P5 process smoke failed; inspect $tmp"}
    Write-Host '[PASS] P5 two independent JVMs and restart media history'
} finally {
    Pop-Location
    Write-Host "Evidence directory: $tmp"
}
