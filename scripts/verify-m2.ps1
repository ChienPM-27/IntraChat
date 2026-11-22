[CmdletBinding()]
param([switch]$Offline,[switch]$SkipJava21Gate)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$previous=Get-Location
try {
  Set-Location $root
  if(-not (Test-Path '.\mvnw.cmd')){throw 'Maven Wrapper not found'}
  $version=(& cmd.exe /d /c 'java -version 2>&1') | Out-String
  Write-Output $version.Trim()
  if(-not $SkipJava21Gate -and $version -notmatch 'version "21([."]|$)'){
    throw 'Actual Java 21 runtime required for M2 local verification'
  }
  $argsList=@('-q')
  if($Offline){$argsList+='-o'}
  $argsList+=@('clean','package')
  & .\mvnw.cmd @argsList
  if($LASTEXITCODE -ne 0){throw ('Maven clean package failed: '+$LASTEXITCODE)}
  $jar=Join-Path $root 'target\intrachat-0.1.0-SNAPSHOT.jar'
  if(-not (Test-Path $jar)){throw 'Runnable shaded JAR missing'}
  & java.exe '-jar' $jar '--diagnostics'
  if($LASTEXITCODE -ne 0){throw ('JAR diagnostics exit: '+$LASTEXITCODE)}
  $entries=@(& jar.exe tf $jar)
  foreach($wanted in @('ui/IntraChat.class','ui/dialogs/LanConnectionDialog.class',
                       'ui/frames/MainChatFrame.class',
                       'vn/intrachat/security/TlsContextFactory.class')){
    if($entries -notcontains $wanted){throw ('Required shaded class missing: '+$wanted)}
  }
  foreach($obsolete in @('ui/LiveIntraChat.class','ui/frames/LiveConnectionFrame.class',
                       'vn/intrachat/IntraChatApplication.class','vn/intrachat/ui/MainWindow.class',
                       'vn/intrachat/ui/NetworkPanel.class','vn/intrachat/ui/InboundRequestDialog.class')){
    if($entries -contains $obsolete){throw ('Obsolete UI still packaged: '+$obsolete)}
  }
  $signatures=@($entries|Where-Object{$_ -match '^META-INF/.*\.(RSA|DSA|SF)$'})
  if($signatures.Count -gt 0){throw 'Signed JAR metadata must be removed during shade'}
  $suites=@(Get-ChildItem 'target\surefire-reports' -Filter 'TEST-*.xml')
  $total=0;$errors=0
  foreach($suite in $suites){
    [xml]$report=Get-Content -Raw -Encoding UTF8 $suite.FullName
    $total+=[int]$report.testsuite.tests
    $errors+=[int]$report.testsuite.errors+[int]$report.testsuite.failures
  }
  if($total -lt 73 -or $errors -ne 0){throw ('JUnit gate failed tests='+$total+' failures='+$errors)}
  Write-Output ("PASS: Java21 local M0/M1/FE/M2 regression. Tests=$total, failures/errors=$errors")
  Write-Output 'PASS: unified Messenger launcher; obsolete UI absent; TLS backend present; no stale signed JAR metadata'
  Write-Output 'NOT TESTED BY THIS SCRIPT: two real Windows LAN PCs; independent security review; GUI SAS/VIEW tests'
  Write-Output 'M2 remains IN PROGRESS; do not mark M1 or M2 Verified from this local verification.'
  exit 0
}catch{
  Write-Error $_.Exception.Message
  exit 1
}finally{Set-Location $previous}
