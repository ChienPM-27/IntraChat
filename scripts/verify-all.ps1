param(
  [string]$JavaHome = $env:JAVA_HOME,
  [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME or pass -JavaHome (JDK 21)' }
$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $java)) { throw "Java not found: $java" }
$oldPref = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try { $ver = (& $java -version 2>&1 | Out-String) }
finally { $ErrorActionPreference = $oldPref }
if ($ver -notmatch 'version "21\.') { throw "JDK 21 required: $ver" }
$logDir = Join-Path ([IO.Path]::GetTempPath()) ('intrachat-p6-verify-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $logDir | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
function Invoke-Check([string]$Name, [string]$Script, [string[]]$More) {
  $log = Join-Path $logDir ($Name+'.log')
  $args = @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot $Script),'-JavaHome',$JavaHome) + $More
  $oldPref = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & powershell.exe @args *> $log; $exit = $LASTEXITCODE }
  finally { $ErrorActionPreference = $oldPref }
  $status = if ($exit -eq 0) {'PASS'} else {'FAIL'}
  $results.Add([pscustomobject]@{Name=$Name;Status=$status;ExitCode=$exit;Log=$log})
  Write-Host "[$status] $Name (log: $log)"
  if ($exit -ne 0) { Get-Content $log -Tail 18; throw "$Name failed" }
}
try {
  if (-not $SkipBuild) { Invoke-Check 'P1-build-JUnit-TCP' 'verify-mvp.ps1' @() }
  elseif (-not (Test-Path (Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'))) {
    throw 'SkipBuild requires existing executable JAR'
  }
  Invoke-Check 'P2-group' 'verify-p2.ps1' @()
  Invoke-Check 'P3-file' 'verify-p3.ps1' @()
  Invoke-Check 'P4-history' 'verify-p4.ps1' @('-SkipBuild')
  Invoke-Check 'P5-media' 'verify-p5.ps1' @()
  Push-Location $repo
  try {
    $oldPref = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & git diff --check 2>&1 | Out-File (Join-Path $logDir 'git-diff-check.log'); $gitExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $oldPref }
    if ($gitExit -ne 0) { throw 'git diff --check failed' }
  } finally { Pop-Location }
  $results.Add([pscustomobject]@{Name='git-diff-check';Status='PASS';ExitCode=0;Log=(Join-Path $logDir 'git-diff-check.log')})
  Write-Host '[PASS] git diff --check'
  Write-Host 'GUI, physical LAN and device playback are NOT RUN by this headless script.'
} finally {
  $results | Export-Csv (Join-Path $logDir 'results.csv') -NoTypeInformation -Encoding UTF8
  Write-Host "Verification logs: $logDir"
}
