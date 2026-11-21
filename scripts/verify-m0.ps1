[CmdletBinding()]
param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Invoke-Checked([string]$title, [string]$file, [string[]]$arguments) {
  Write-Output ("=== {0} ===" -f $title)
  & $file @arguments
  $code = $LASTEXITCODE
  if ($code -ne 0) { throw "$title FAILED with exit code $code" }
  Write-Output ("PASS: {0}" -f $title)
}

try {
  & (Join-Path $PSScriptRoot 'check-environment.ps1')
  $wrapper = Join-Path $root 'mvnw.cmd'
  if (-not (Test-Path $wrapper)) { throw 'Maven Wrapper not found.' }
  $mode = @()
  if ($Offline) { $mode += '-o' }
  Invoke-Checked 'Package' $wrapper ($mode + @('-q','package'))
  Invoke-Checked 'Unit tests' $wrapper ($mode + @('-q','test'))
  $jar = Join-Path $root 'target\intrachat-0.1.0-SNAPSHOT.jar'
  if (-not (Test-Path $jar)) { throw 'Built JAR is missing.' }
  Invoke-Checked 'Diagnostics' 'java.exe' @('-jar',$jar,'--diagnostics')
  $xmls = @(Get-ChildItem (Join-Path $root 'target\surefire-reports') -Filter 'TEST-*.xml' -ErrorAction Stop)
  $total = 0
  $failed = 0
  foreach ($file in $xmls) {
    [xml]$report = Get-Content -Raw -Encoding UTF8 $file.FullName
    $total += [int]$report.testsuite.tests
    $failed += ([int]$report.testsuite.failures + [int]$report.testsuite.errors)
  }
  if ($total -lt 1 -or $failed -gt 0) { throw "Invalid test count/results: total=$total failures/errors=$failed" }
  Write-Output "PASS: $total tests executed; 0 test failures/errors."
  Write-Output 'M0 automated verification PASS. GUI lifecycle, JDK 21 runtime, and clean-checkout verification require separate evidence.'
  exit 0
} catch {
  Write-Error $_.Exception.Message
  exit 1
}
