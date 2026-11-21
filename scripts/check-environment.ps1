[CmdletBinding()]
param([switch]$StrictJdk21)
$ErrorActionPreference = 'Stop'
Write-Output 'IntraChat / environment inspection'
Write-Output ("OS: {0}" -f [System.Environment]::OSVersion.VersionString)
if ([System.Environment]::OSVersion.Platform -ne 'Win32NT') { throw 'Windows is required for the initial release.' }
foreach ($name in @('java.exe','javac.exe')) {
  $command = Get-Command $name -ErrorAction SilentlyContinue
  if (-not $command) { throw "$name is missing. Install a JDK (Java 21 LTS recommended)." }
  Write-Output ("{0}: {1}" -f $name, $command.Source)
}
$versionText = & cmd.exe /d /c 'java -version 2>&1'
Write-Output ($versionText | Out-String)
$javacText = & cmd.exe /d /c 'javac -version 2>&1'
Write-Output ($javacText | Out-String)
if ($StrictJdk21 -and -not (($versionText | Out-String) -match 'version "21([."]|$)')) { throw 'JDK 21 is required for strict compatibility verification.' }
if (-not (($versionText | Out-String) -match 'version "21([."]|$)')) { Write-Warning 'JDK 21 is not active: compiling with --release 21 is not runtime verification on JDK 21.' }
$root = Split-Path -Parent $PSScriptRoot
if (Test-Path (Join-Path $root 'mvnw.cmd')) {
  Write-Output ('Maven wrapper: ' + (Join-Path $root 'mvnw.cmd'))
} else {
  Write-Warning 'Maven wrapper is not present yet.'
}
Write-Output 'Environment inspection completed (does not change PATH or firewall).'
