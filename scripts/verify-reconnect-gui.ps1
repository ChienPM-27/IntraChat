param([Parameter(Mandatory=$true)][string]$JavaHome)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java=Join-Path $JavaHome 'bin\java.exe'
$jar=Join-Path $repo 'target\intrachat-0.1.0-SNAPSHOT.jar'
if(!(Test-Path $java) -or !(Test-Path $jar)){throw 'Build executable JAR with JDK 21 first'}
$dir=Join-Path ([IO.Path]::GetTempPath()) ('intrachat-reconnect-gui-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dir | Out-Null
$jobs=@()
function Wait-For([scriptblock]$condition,[string]$label){
    for($i=0;$i -lt 100;$i++){
        if(& $condition){return}
        Start-Sleep -Milliseconds 250
    }
    throw "Timed out: $label"
}
Push-Location $dir
try{
    & $java -cp $jar (Join-Path $PSScriptRoot 'ReconnectGuiSeed.java')
    if($LASTEXITCODE -ne 0){throw 'Fixture failed'}
    $ports=(Get-Content ports.txt -Raw).Trim().Split(',')
    $a=Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $dir -PassThru -RedirectStandardOutput (Join-Path $dir 'a.out') -RedirectStandardError (Join-Path $dir 'a.err')
    $jobs+=@($a)
    Wait-For { $a.Refresh(); !$a.HasExited -and $a.MainWindowTitle -like 'IntraChat*Live LAN TCP*' } 'Returning Alice main window while Bob offline'
    Wait-For { [bool](Get-NetTCPConnection -OwningProcess $a.Id -State Listen -LocalPort ([int]$ports[0]) -ErrorAction SilentlyContinue) } 'Alice restored listen port'
    Write-Host 'PASS_GUI_MAIN_WHILE_PEER_OFFLINE'
    $b=Start-Process -FilePath $java -ArgumentList @('-Dintrachat.user=Bob','-jar',('"'+$jar+'"')) -WorkingDirectory $dir -PassThru -RedirectStandardOutput (Join-Path $dir 'b.out') -RedirectStandardError (Join-Path $dir 'b.err')
    $jobs+=@($b)
    Wait-For { $b.Refresh(); !$b.HasExited -and $b.MainWindowTitle -like 'IntraChat*Live LAN TCP*' } 'Returning Bob main window'
    Wait-For {
        $aConnections=@(Get-NetTCPConnection -OwningProcess $a.Id -State Established -ErrorAction SilentlyContinue)
        $bConnections=@(Get-NetTCPConnection -OwningProcess $b.Id -State Established -ErrorAction SilentlyContinue)
        [bool]($aConnections | Where-Object { $entry=$_; $bConnections | Where-Object { $_.LocalPort -eq $entry.RemotePort -and $_.RemotePort -eq $entry.LocalPort } })
    } 'Automatic real TCP connection between GUI processes'
    Start-Sleep -Seconds 2
    $a.Refresh();$b.Refresh()
    if($a.MainWindowTitle -notlike 'IntraChat*Live LAN TCP*' -or $b.MainWindowTitle -notlike 'IntraChat*Live LAN TCP*'){throw 'Unexpected dialog after reconnect'}
    Write-Host 'PASS_GUI_TWO_JVM_AUTO_RECONNECT'
}finally{
    foreach($job in $jobs){
        $job.Refresh()
        if(!$job.HasExited){$null=$job.CloseMainWindow();if(!$job.WaitForExit(5000)){Stop-Process -Id $job.Id -Force}}
        $job.Dispose()
    }
    Pop-Location
    Write-Host "Evidence: $dir"
}
