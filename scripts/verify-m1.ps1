[CmdletBinding()]
param([switch]$Offline, [switch]$SkipJava21Gate)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$previous = Get-Location
try {
  Set-Location $root
  $wrapper = Join-Path $root 'mvnw.cmd'
  if (-not (Test-Path $wrapper)) { throw 'Maven wrapper is missing.' }
  Write-Output '=== IntraChat M1 local automated verification ==='
  $version = (& cmd.exe /d /c 'java -version 2>&1') | Out-String
  Write-Output $version.Trim()
  if (-not $SkipJava21Gate -and $version -notmatch 'version "21([."]|$)') {
    throw 'This M1 validation requires actual Java 21 runtime. Set command-local JAVA_HOME and PATH to Temurin 21.'
  }
  $argsList = @('-q')
  if ($Offline) { $argsList += '-o' }
  $argsList += @('clean', 'package')
  & $wrapper @argsList
  if ($LASTEXITCODE -ne 0) { throw ('Maven clean package failed: ' + $LASTEXITCODE) }
  $jar = Join-Path $root 'target\intrachat-0.1.0-SNAPSHOT.jar'
  if (-not (Test-Path $jar)) { throw ('Shaded runnable JAR missing: ' + $jar) }
  & java.exe '-jar' $jar '--diagnostics'
  if ($LASTEXITCODE -ne 0) { throw ('Runnable JAR diagnostics failed: ' + $LASTEXITCODE) }
  $suiteFiles = @(Get-ChildItem (Join-Path $root 'target\surefire-reports') -Filter 'TEST-*.xml')
  $total = 0
  $failed = 0
  foreach ($file in $suiteFiles) {
    [xml]$report = Get-Content -Raw -Encoding UTF8 $file.FullName
    $total += [int]$report.testsuite.tests
    $failed += [int]$report.testsuite.failures + [int]$report.testsuite.errors
  }
  if ($total -lt 9 -or $failed -ne 0) { throw ('Unacceptable JUnit result: tests=' + $total + ', failures/errors=' + $failed) }
  Write-Output ("PASS: local M1 automated protocol/loopback tests=$total, failures/errors=$failed")
  Write-Output 'IMPORTANT: This script tests ONLY this Windows computer. It does NOT verify two distinct LAN PCs or offline LAN, production security or M1 acceptance gate.'
  exit 0
} catch {
  Write-Error $_.Exception.Message
  exit 1
} finally {
  Set-Location $previous
}
