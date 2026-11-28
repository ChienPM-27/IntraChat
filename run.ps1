[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$DataDir,
    [switch]$SkipBuild,
    [switch]$Diagnostics
)

$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot

function Test-Jdk21([string]$JdkPath) {
    if ([string]::IsNullOrWhiteSpace($JdkPath)) { return $false }
    $java = Join-Path $JdkPath 'bin\java.exe'
    $javac = Join-Path $JdkPath 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
        -not (Test-Path -LiteralPath $javac -PathType Leaf)) { return $false }

    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $version = (& $java -version 2>&1 | Out-String)
        $javaExit = $LASTEXITCODE
        $compiler = (& $javac -version 2>&1 | Out-String)
        $javacExit = $LASTEXITCODE
        return ($javaExit -eq 0 -and $javacExit -eq 0 -and
            $version -match 'version "21(?:[."]|$)' -and
            $compiler -match 'javac 21(?:[.\s]|$)')
    } finally {
        $ErrorActionPreference = $oldPreference
    }
}

if ($PSBoundParameters.ContainsKey('JavaHome')) {
    if (-not (Test-Jdk21 $JavaHome)) { throw "Not a JDK 21 installation: $JavaHome" }
} else {
    $candidates = @($env:JAVA_HOME)
    if ($env:LOCALAPPDATA) {
        $portable = Join-Path $env:LOCALAPPDATA 'IntraChat\toolchains'
        $candidates += @(Get-ChildItem -LiteralPath $portable -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
    }
    $javacCommand = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javacCommand) {
        $candidates += Split-Path -Parent (Split-Path -Parent $javacCommand.Source)
    }
    $JavaHome = $candidates | Where-Object { Test-Jdk21 $_ } | Select-Object -First 1
    if (-not $JavaHome) {
        throw 'JDK 21 not found. Install JDK 21, set JAVA_HOME, or pass -JavaHome <path>.'
    }
}

$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$java = Join-Path $JavaHome 'bin\java.exe'
$wrapper = Join-Path $repo 'mvnw.cmd'
$jar = Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
$javaArgs = @()
if ($DataDir) {
    $dataPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($DataDir)
    $javaArgs += "-Dintrachat.dataDir=$dataPath"
}
$javaArgs += @('-jar', $jar)
if ($Diagnostics) { $javaArgs += '--diagnostics' }

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path
Push-Location -LiteralPath $repo
try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$(Join-Path $JavaHome 'bin');$oldPath"
    Write-Host "IntraChat / JDK 21: $JavaHome"
    if (-not $SkipBuild) {
        Write-Host 'Building and testing IntraChat...'
        & $wrapper clean package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)." }
    }
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "Executable JAR missing: $jar. Run without -SkipBuild first."
    }
    Write-Host 'Starting IntraChat...'
    & $java @javaArgs
    if ($LASTEXITCODE -ne 0) { throw "IntraChat exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}
