[CmdletBinding()]
param(
    [ValidateRange(1,4)][int]$Count = 3,
    [string]$JavaHome,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$options = @{ BotCount = $Count }
if ($PSBoundParameters.ContainsKey('JavaHome')) { $options.JavaHome = $JavaHome }
if ($SkipBuild) { $options.SkipBuild = $true }
& (Join-Path $PSScriptRoot 'run.ps1') @options
