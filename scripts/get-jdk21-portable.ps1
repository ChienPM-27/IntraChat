[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$base = Join-Path $env:LOCALAPPDATA 'IntraChat\toolchains'
New-Item -ItemType Directory -Path $base -Force | Out-Null
$archive = Join-Path $base 'temurin-21.0.13-windows-x64.zip'
$stamp = Join-Path $base 'jdk21.status.txt'
$url = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
$expected = 'bbebea1469996b276683c9299c227b294f85c847039c82633218186a4ad4fbf2'
try {
  [IO.File]::WriteAllText($stamp, 'DOWNLOADING ' + (Get-Date).ToString('o'))
  if (-not (Test-Path $archive)) {
    & curl.exe -f -L -s -S --retry 2 --connect-timeout 25 --max-time 600 -o $archive $url
    if ($LASTEXITCODE -ne 0) { throw ('curl failed ' + $LASTEXITCODE) }
  }
  [IO.File]::WriteAllText($stamp, 'VERIFYING ' + (Get-Date).ToString('o'))
  $actual = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
  if ($actual -ne $expected) { throw "Temurin JDK checksum mismatch: $actual" }
  [IO.File]::WriteAllText($stamp, 'EXTRACTING ' + (Get-Date).ToString('o'))
  & tar.exe -xf $archive -C $base
  if ($LASTEXITCODE -ne 0) { throw ('extract failed ' + $LASTEXITCODE) }
  $jdk = @(Get-ChildItem $base -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1)
  if ($jdk.Count -ne 1) { throw 'JDK directory not found' }
  $j = Join-Path $jdk[0].FullName 'bin\java.exe'
  if (-not (Test-Path $j)) { throw 'JDK executable missing' }
  $v = & cmd.exe /d /c ('"'+$j+'" -version 2>&1') | Out-String
  [IO.File]::WriteAllText($stamp, 'SUCCESS JDK=' + $jdk[0].FullName + [Environment]::NewLine + $v)
} catch {
  [IO.File]::WriteAllText($stamp, 'FAILED ' + $_.Exception.ToString())
  exit 1
}
