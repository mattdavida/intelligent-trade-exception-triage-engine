# Shared helpers for ITETE stack scripts. Dot-source from callers.

# Captured while this file is being loaded (scripts/lib).
$script:IteeLibDir = $PSScriptRoot

function Get-IteeRepoRoot {
    param([string]$StartDir = $script:IteeLibDir)
    $dir = (Resolve-Path -LiteralPath $StartDir).Path
    while ($dir) {
        if ((Test-Path -LiteralPath (Join-Path $dir 'settings.gradle.kts')) -and
            (Test-Path -LiteralPath (Join-Path $dir 'docker-compose.yml'))) {
            return $dir
        }
        $parent = Split-Path -Parent $dir
        if (-not $parent -or $parent -eq $dir) { break }
        $dir = $parent
    }
    throw "Could not locate ITETE repo root from $StartDir"
}

function Import-IteeDotEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        Set-Item -Path "Env:$name" -Value $value
    }
}

function Test-IteePortListening {
    param([int]$Port)
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Get-IteeListeningPids {
    param([Parameter(Mandatory)][int]$Port)
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { return @() }
    return @(
        $conns |
            ForEach-Object { [int]$_.OwningProcess } |
            Where-Object { $_ -gt 0 } |
            Sort-Object -Unique
    )
}

# Prefer killing the app process tree (python/java/node). Walk into a host
# powershell/pwsh only when that shell looks like an ITETE start-* window.
function Get-IteeProcessTreeRoot {
    param([Parameter(Mandatory)][int]$ProcId)
    $current = $ProcId
    $systemNames = @(
        'explorer', 'sihost', 'winlogon', 'csrss', 'services', 'svchost',
        'System', 'Idle', 'Registry', 'fontdrvhost', 'dwm', 'StartMenuExperienceHost',
        'Cursor', 'Code'
    )
    $shellNames = @('powershell', 'pwsh', 'cmd')
    for ($i = 0; $i -lt 24; $i++) {
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$current" -ErrorAction SilentlyContinue
        if (-not $proc) { return $current }
        $parentId = [int]$proc.ParentProcessId
        if ($parentId -le 4 -or $parentId -eq $current) { return $current }

        $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$parentId" -ErrorAction SilentlyContinue
        if (-not $parent) { return $current }

        $parentName = [System.IO.Path]::GetFileNameWithoutExtension($parent.Name)
        if ($systemNames -contains $parentName) {
            return $current
        }
        if ($shellNames -contains $parentName) {
            $cmd = [string]$parent.CommandLine
            if ($cmd -match 'start-(ai-engine|orchestrator|ui)\.ps1') {
                return $parentId
            }
            return $current
        }
        $current = $parentId
    }
    return $current
}

function Get-IteeChildProcessIds {
    param([Parameter(Mandatory)][int]$ParentId)
    if ($ParentId -le 4) { return @() }
    $kids = @(
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { [int]$_.ParentProcessId -eq $ParentId } |
            ForEach-Object { [int]$_.ProcessId }
    )
    $all = @()
    foreach ($kid in $kids) {
        $all += $kid
        $all += Get-IteeChildProcessIds -ParentId $kid
    }
    return @($all | Sort-Object -Unique)
}

function Stop-IteeProcessTree {
    param(
        [Parameter(Mandatory)][int]$ProcId,
        [string]$Label = 'process'
    )
    if ($ProcId -le 4) { return }

    # Kill known children first (handles dead parent PID still owning the Listen socket
    # while multiprocessing / uvicorn workers remain alive).
    $children = Get-IteeChildProcessIds -ParentId $ProcId
    foreach ($childId in $children) {
        Write-Host "Stopping $Label child PID $childId ..." -ForegroundColor Yellow
        & taskkill.exe /F /T /PID $childId 2>$null | Out-Null
        Stop-Process -Id $childId -Force -ErrorAction SilentlyContinue
    }

    $root = Get-IteeProcessTreeRoot -ProcId $ProcId
    $rootProc = Get-Process -Id $root -ErrorAction SilentlyContinue
    $name = if ($rootProc) { $rootProc.ProcessName } else { 'missing/zombie' }
    Write-Host "Stopping $Label tree root PID $root ($name) [listen PID $ProcId] ..." -ForegroundColor Yellow
    & taskkill.exe /F /T /PID $root 2>$null | Out-Null
    if ($root -ne $ProcId) {
        & taskkill.exe /F /T /PID $ProcId 2>$null | Out-Null
    }
    Stop-Process -Id $ProcId -Force -ErrorAction SilentlyContinue
    if ($root -ne $ProcId) {
        Stop-Process -Id $root -Force -ErrorAction SilentlyContinue
    }
}

function Stop-IteeProcessesByCommandMatch {
    param(
        [Parameter(Mandatory)][int]$Port,
        [string]$Label = "port $Port"
    )
    # Fallback when Listen OwningProcess is a zombie PID but workers still serve traffic.
    $patterns = switch ($Port) {
        8000 { @('uvicorn', 'ai-engine', 'main:app', 'multiprocessing.spawn') }
        8081 { @('orchestrator', 'bootRun', 'itee-orchestrator') }
        4200 { @('ng serve', '@angular/cli', 'ui\\node_modules') }
        default { @() }
    }
    if ($patterns.Count -eq 0) { return }

    $matches = @(
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                $cmd = [string]$_.CommandLine
                if (-not $cmd) { return $false }
                foreach ($pat in $patterns) {
                    if ($cmd -like ("*{0}*" -f $pat)) { return $true }
                }
                return $false
            }
    )
    foreach ($proc in $matches) {
        $targetPid = [int]$proc.ProcessId
        Write-Host ("Stopping {0} by command match PID {1}" -f $Label, $targetPid) -ForegroundColor Yellow
        & taskkill.exe /F /T /PID $targetPid 2>$null | Out-Null
        Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
    }
}

function Test-IteePortReallyServing {
    param([Parameter(Mandatory)][int]$Port)
    # Ghost Listen rows can linger after the process is gone; probe the port.
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $iar = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(500)
        if ($ok -and $client.Connected) {
            $client.Close()
            return $true
        }
        $client.Close()
    } catch { }
    return $false
}

function Stop-IteePortListener {
    param(
        [Parameter(Mandatory)][int]$Port,
        [string]$Label = "port $Port",
        [int]$Attempts = 4,
        [int]$SettleMs = 750
    )
    $listening = Test-IteePortListening -Port $Port
    $serving = Test-IteePortReallyServing -Port $Port
    if (-not $listening -and -not $serving) {
        Write-Host "Nothing listening on $Label (:$Port)." -ForegroundColor DarkGray
        return $true
    }

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $pids = Get-IteeListeningPids -Port $Port
        $pidList = ($pids -join ', ')
        if (-not $pidList) { $pidList = '(none)' }
        Write-Host ("Clearing {0} (:{1}) attempt {2}/{3} - PID(s): {4}" -f $Label, $Port, $attempt, $Attempts, $pidList) -ForegroundColor Yellow

        foreach ($procId in $pids) {
            Stop-IteeProcessTree -ProcId $procId -Label $Label
        }
        Stop-IteeProcessesByCommandMatch -Port $Port -Label $Label
        Start-Sleep -Milliseconds $SettleMs

        if (-not (Test-IteePortReallyServing -Port $Port)) {
            break
        }
    }

    # Prefer "can connect" over stale netstat Listen rows (zombie OwningProcess).
    if (Test-IteePortReallyServing -Port $Port) {
        $still = Get-IteeListeningPids -Port $Port
        $stillList = ($still -join ', ')
        Write-Host ("{0} still in use on :{1} (PID(s): {2})." -f $Label, $Port, $stillList) -ForegroundColor Red
        return $false
    }

    if (Test-IteePortListening -Port $Port) {
        Write-Host ("{0} free (:{1}) - stale Listen row may linger briefly in netstat." -f $Label, $Port) -ForegroundColor Green
    } else {
        Write-Host ("{0} free (:{1})." -f $Label, $Port) -ForegroundColor Green
    }
    return $true
}

function Wait-IteeHttpOk {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [int]$TimeoutSec = 90,
        [string]$Label = $Uri
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) {
                Write-Host "Ready: $Label" -ForegroundColor Green
                return $true
            }
        } catch {
            # still starting
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "Timed out waiting for $Label ($Uri)" -ForegroundColor Red
    return $false
}

function Invoke-IteeGradle {
    param(
        [Parameter(Mandatory)][string[]]$GradleArgs
    )
    $root = Get-IteeRepoRoot
    $wrapper = Join-Path $root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper)) {
        $wrapper = Join-Path $root 'gradlew'
    }
    if (-not (Test-Path -LiteralPath $wrapper)) {
        throw "Gradle wrapper not found at repo root (expected gradlew.bat / gradlew)."
    }
    Push-Location $root
    try {
        & $wrapper @GradleArgs
        if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Start-IteeServiceWindow {
    param(
        [Parameter(Mandatory)][string]$ScriptPath,
        [string]$Title = 'ITETE',
        [string[]]$ArgumentList = @()
    )
    if (-not (Test-Path -LiteralPath $ScriptPath)) {
        throw "Missing service script: $ScriptPath"
    }
    $shell = if (Get-Command pwsh -ErrorAction SilentlyContinue) { 'pwsh' } else { 'powershell' }
    $argList = @('-NoExit', '-File', $ScriptPath) + $ArgumentList
    Start-Process -FilePath $shell -ArgumentList $argList | Out-Null
    Write-Host "Opened window: $Title" -ForegroundColor Cyan
}
