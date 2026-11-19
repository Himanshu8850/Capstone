<#
.SYNOPSIS
  Starts, stops or reports on the local BookyGeek stack without Docker: PostgreSQL, backend jar and Vite dev server.

.DESCRIPTION
  Everything binds to 127.0.0.1. Nothing waits indefinitely: every wait has a time limit.
  No secret is stored in the repository:
    - the database password comes from $env:SPRING_DATASOURCE_PASSWORD, or from the first line of
      <ToolsDir>\db-password.txt (outside the repository);
    - JWT_SECRET is generated at random on every start, so sessions end when the stack restarts.
  Environment variables are set for the child processes only and are removed again before the script ends.

.PARAMETER Action
  start (default), stop, restart or status.

.PARAMETER ToolsDir
  Folder with the throw-away PostgreSQL (pg\bin\pg_ctl.exe, pg\data) and the logs.
  Default: %USERPROFILE%\.bookygeek-tools

.PARAMETER DbPort
  PostgreSQL port on 127.0.0.1. Default 5434 (5433 is refused by Windows on some machines, 5432 is often taken).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\dev-local.ps1 start
#>
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'restart', 'status')]
    [string]$Action = 'start',
    [string]$ToolsDir = (Join-Path $env:USERPROFILE '.bookygeek-tools'),
    [int]$DbPort = 5434,
    [string]$DbName = 'bookygeek_test',
    [string]$DbUser = 'bookygeek_test'
)

$ErrorActionPreference = 'Stop'
$BackendPort = 8080
$FrontendPort = 5173
$RepoRoot = Split-Path -Parent $PSScriptRoot
$PgCtl = Join-Path $ToolsDir 'pg\bin\pg_ctl.exe'
$PgData = Join-Path $ToolsDir 'pg\data'
$Jar = Join-Path $RepoRoot 'backend\target\bookygeek-backend-0.1.0-SNAPSHOT.jar'

function Get-ListenerPid([int]$Port) {
    $line = netstat -ano -p tcp | Select-String -Pattern ("^\s*TCP\s+127\.0\.0\.1:{0}\s+\S+\s+LISTENING\s+(\d+)" -f $Port)
    if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    return $null
}

function Wait-ForPort([int]$Port, [int]$Seconds) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Get-ListenerPid $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Test-Http([string]$Url) {
    try { return (Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 8).StatusCode -eq 200 } catch { return $false }
}

function Get-DbPassword {
    if ($env:SPRING_DATASOURCE_PASSWORD) { return $env:SPRING_DATASOURCE_PASSWORD }
    $file = Join-Path $ToolsDir 'db-password.txt'
    if (Test-Path $file) { return (Get-Content $file -TotalCount 1).Trim() }
    throw "No database password. Set SPRING_DATASOURCE_PASSWORD, or put it on the first line of $file (outside the repository)."
}

function Start-Database {
    if (Get-ListenerPid $DbPort) { Write-Host "PostgreSQL  already listening on 127.0.0.1:$DbPort"; return }
    if (-not (Test-Path $PgCtl)) { throw "PostgreSQL is not running on 127.0.0.1:$DbPort and $PgCtl does not exist. Start your own database on that port, or set -ToolsDir." }
    $p = Start-Process $PgCtl -ArgumentList '-D', "`"$PgData`"", '-l', "`"$(Join-Path $ToolsDir 'pg.log')`"", '-o', "`"-p $DbPort`"", '-w', '-t', '20', 'start' -WindowStyle Hidden -PassThru
    [void]$p.WaitForExit(30000)
    if (-not (Wait-ForPort $DbPort 5)) { throw "PostgreSQL did not start on 127.0.0.1:$DbPort. See $(Join-Path $ToolsDir 'pg.log')." }
    Write-Host "PostgreSQL  started on 127.0.0.1:$DbPort"
}

function Start-Backend {
    if (Get-ListenerPid $BackendPort) { Write-Host "Backend     already listening on 127.0.0.1:$BackendPort"; return }
    if (-not (Test-Path $Jar)) { throw "Backend jar not found: $Jar`nBuild it first: cd backend; mvn -DskipTests package" }
    $bytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $vars = @{
        SPRING_PROFILES_ACTIVE     = 'dev'
        SPRING_DATASOURCE_URL      = "jdbc:postgresql://127.0.0.1:$DbPort/$DbName"
        SPRING_DATASOURCE_USERNAME = $DbUser
        SPRING_DATASOURCE_PASSWORD = (Get-DbPassword)
        JWT_SECRET                 = [Convert]::ToBase64String($bytes)
        COOKIE_SECURE              = 'false'
    }
    $log = Join-Path $ToolsDir 'be.log'
    try {
        foreach ($k in $vars.Keys) { Set-Item -Path "Env:$k" -Value $vars[$k] }
        Start-Process cmd.exe -ArgumentList '/c', "java -jar `"$Jar`" > `"$log`" 2>&1" -WorkingDirectory (Join-Path $RepoRoot 'backend') -WindowStyle Hidden
    }
    finally {
        foreach ($k in $vars.Keys) { Remove-Item -Path "Env:$k" -ErrorAction SilentlyContinue }
    }
    if (-not (Wait-ForPort $BackendPort 60)) { throw "Backend did not start within 60 s. See $log." }
    Write-Host "Backend     started on 127.0.0.1:$BackendPort"
}

function Start-Frontend {
    if (Get-ListenerPid $FrontendPort) { Write-Host "Frontend    already listening on 127.0.0.1:$FrontendPort"; return }
    $dir = Join-Path $RepoRoot 'frontend'
    if (-not (Test-Path (Join-Path $dir 'node_modules'))) { throw "frontend\node_modules is missing. Run: cd frontend; npm ci" }
    $log = Join-Path $ToolsDir 'vite.log'
    Start-Process cmd.exe -ArgumentList '/c', "npm run dev > `"$log`" 2>&1" -WorkingDirectory $dir -WindowStyle Hidden
    if (-not (Wait-ForPort $FrontendPort 30)) { throw "Frontend did not start within 30 s. See $log." }
    Write-Host "Frontend    started on 127.0.0.1:$FrontendPort"
}

function Stop-Port([string]$Name, [int]$Port) {
    $id = Get-ListenerPid $Port
    if ($id) { Stop-Process -Id $id -Force; Write-Host "$Name stopped (port $Port)" } else { Write-Host "$Name not running (port $Port)" }
}

function Stop-Database {
    if ((Get-ListenerPid $DbPort) -and (Test-Path $PgCtl) -and (Test-Path (Join-Path $PgData 'postmaster.pid'))) {
        $p = Start-Process $PgCtl -ArgumentList '-D', "`"$PgData`"", 'stop', '-m', 'fast', '-t', '15' -WindowStyle Hidden -PassThru
        [void]$p.WaitForExit(20000)
        Write-Host "PostgreSQL stopped (port $DbPort)"
    }
    else { Write-Host "PostgreSQL not running from $ToolsDir (port $DbPort)" }
}

function Show-Status {
    $rows = @(
        @{ Name = 'PostgreSQL'; Port = $DbPort },
        @{ Name = 'Backend'; Port = $BackendPort },
        @{ Name = 'Frontend'; Port = $FrontendPort }
    )
    foreach ($r in $rows) {
        $id = Get-ListenerPid $r.Port
        $state = if ($id) { "running (pid $id)" } else { 'stopped' }
        Write-Host ("{0,-11} 127.0.0.1:{1,-5} {2}" -f $r.Name, $r.Port, $state)
    }
}

if ($Action -in 'stop', 'restart') {
    Stop-Port 'Frontend' $FrontendPort
    Stop-Port 'Backend ' $BackendPort
    Stop-Database
}

if ($Action -in 'start', 'restart') {
    if (-not (Test-Path $ToolsDir)) { New-Item -ItemType Directory -Path $ToolsDir | Out-Null }
    Start-Database
    Start-Backend
    Start-Frontend
    $api = Test-Http "http://127.0.0.1:$BackendPort/api/v1/categories"
    $ui = Test-Http "http://127.0.0.1:$FrontendPort/api/v1/books"
    Write-Host ''
    Write-Host ("Backend API check : {0}" -f $(if ($api) { 'OK (200)' } else { 'FAILED' }))
    Write-Host ("Via frontend proxy: {0}" -f $(if ($ui) { 'OK (200)' } else { 'FAILED' }))
    Write-Host ''
    Write-Host "Open http://127.0.0.1:$FrontendPort"
    Write-Host "Stop with: scripts\dev-local.cmd stop"
    if (-not ($api -and $ui)) { exit 1 }
}

if ($Action -eq 'status') { Show-Status }
