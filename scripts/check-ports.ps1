param(
  [ValidateSet('frontend', 'backend', 'all', 'registry')]
  [string]$Scope = 'all',
  [switch]$Json,
  [switch]$AllowCurrentDeployment
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$frontendPort = 5217
if ($env:WINPRESS_FRONTEND_PORT -and [int]::TryParse($env:WINPRESS_FRONTEND_PORT, [ref]$frontendPort)) { }
elseif ($env:FRONTEND_PORT -and [int]::TryParse($env:FRONTEND_PORT, [ref]$frontendPort)) { }

$backendPort = 8192
if ($env:VITE_BACKEND_PORT -and [int]::TryParse($env:VITE_BACKEND_PORT, [ref]$backendPort)) { }
elseif ($env:WINPRESS_BACKEND_PORT -and [int]::TryParse($env:WINPRESS_BACKEND_PORT, [ref]$backendPort)) { }
elseif ($env:BACKEND_PORT -and [int]::TryParse($env:BACKEND_PORT, [ref]$backendPort)) { }

$requiredServices = @(
  [pscustomobject]@{ Name = 'winpress-commercial-frontend'; Port = $frontendPort; Kind = 'web'; MustBeFree = $true },
  [pscustomobject]@{ Name = 'winpress-commercial-backend'; Port = $backendPort; Kind = 'api'; MustBeFree = $true },
  [pscustomobject]@{ Name = 'winpress-commercial-postgresql'; Port = 55434; Kind = 'database'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-commercial-redis'; Port = 6382; Kind = 'cache'; MustBeFree = $false }
)

$registryServices = @(
  [pscustomobject]@{ Name = 'winpress-commercial-legacy-frontend'; Port = 5174; Kind = 'web'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-full-web'; Port = 8091; Kind = 'web'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-commercial-legacy-backend'; Port = 8092; Kind = 'api'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-full-legacy-api'; Port = 8090; Kind = 'api'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'yishou-geo-trust-site'; Port = 3200; Kind = 'web'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'dongchi-brand-site'; Port = 4188; Kind = 'web'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-cn-prototype'; Port = 8116; Kind = 'web'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'aibis-preview'; Port = 5191; Kind = 'web'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-postgresql-legacy'; Port = 55432; Kind = 'database'; MustBeFree = $false },
  [pscustomobject]@{ Name = 'winpress-postgresql-commercial'; Port = 55434; Kind = 'database'; MustBeFree = $false }
)

$services = $requiredServices
if ($Scope -eq 'registry') {
  $services = $registryServices
} elseif ($Scope -eq 'backend') {
  $services = @($requiredServices | Where-Object { $_.Name -ne 'winpress-commercial-frontend' })
}

function Get-PortOwners {
  param([int]$Port)
  $pattern = "^\s*TCP\s+(\S+):$($Port)\s+\S+\s+LISTENING\s+(\d+)\s*$"
  $owners = @()
  for ($attempt = 1; $attempt -le 3; $attempt++) {
    $rows = netstat -ano | Select-String -Pattern $pattern
    foreach ($row in $rows) {
      $portPid = [int]$row.Matches[0].Groups[2].Value
      $owners += [pscustomobject]@{
        Pid = $portPid
        Process = (Get-Process -Id $portPid -ErrorAction SilentlyContinue).ProcessName
        Address = $row.Matches[0].Groups[1].Value
      }
    }
    if ($owners.Count -gt 0 -or $attempt -eq 3) { break }
    Start-Sleep -Milliseconds 150
  }
  if ($owners.Count -eq 0) { return @() }
  return $owners | Sort-Object Pid -Unique
}

function Test-CurrentProjectBinding {
  param(
    [string]$ContainerName,
    [int]$Port
  )

  $inspectRaw = & docker inspect $ContainerName 2>$null
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($inspectRaw -join ''))) {
    return $false
  }
  try {
    $inspect = (($inspectRaw -join "`n") | ConvertFrom-Json)[0]
    $workingDirectory = [string]$inspect.Config.Labels.'com.docker.compose.project.working_dir'
    if (
      -not $inspect.State.Running -or
      -not $workingDirectory.Equals($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)
    ) {
      return $false
    }
    foreach ($bindingProperty in $inspect.NetworkSettings.Ports.PSObject.Properties) {
      foreach ($binding in @($bindingProperty.Value)) {
        if ([int]$binding.HostPort -eq $Port) {
          return $true
        }
      }
    }
  } catch {
    return $false
  }
  return $false
}

$results = foreach ($service in $services) {
  $owners = @(Get-PortOwners -Port $service.Port)
  $status = if ($owners.Count -eq 0) {
    'free'
  } elseif (
    $service.MustBeFree -and
    $AllowCurrentDeployment -and
    (Test-CurrentProjectBinding -ContainerName $service.Name -Port $service.Port)
  ) {
    'current-deployment'
  } elseif ($service.MustBeFree) {
    'conflict'
  } else {
    'listening'
  }
  [pscustomobject]@{
    Service = $service.Name
    Port = $service.Port
    Kind = $service.Kind
    Status = $status
    Owners = $owners
  }
}

$conflicts = @($results | Where-Object { $_.Status -eq 'conflict' })

if ($Json) {
  [pscustomobject]@{
    CheckedAt = (Get-Date).ToString('o')
    Scope = $Scope
    Passed = ($conflicts.Count -eq 0)
    Results = $results
  } | ConvertTo-Json -Depth 8
} else {
  Write-Host 'WinPress deployment port preflight'
  Write-Host ("Checked at: {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
  Write-Host ''
  foreach ($result in $results) {
    $mark = switch ($result.Status) {
      'free' { '[OK]' }
      'current-deployment' { '[READY]' }
      'listening' { '[INFO]' }
      default { '[CONFLICT]' }
    }
    Write-Host ("{0} {1} TCP {2}: {3}" -f $mark, $result.Service, $result.Port, $result.Status)
    foreach ($owner in $result.Owners) {
      Write-Host ("  PID {0} | {1} | {2}" -f $owner.Pid, $owner.Process, $owner.Address)
    }
  }
  if ($conflicts.Count -gt 0) {
    Write-Host ''
    Write-Host 'Port preflight failed. Please stop conflicting processes before start or set a different port.' -ForegroundColor Red
    Write-Host ("Suggested next free frontend port: $($frontendPort + 10)") -ForegroundColor Yellow
  } else {
    Write-Host ''
    Write-Host 'Port preflight passed. Existing listeners, if any, belong to this project deployment.'
  }
}

if ($conflicts.Count -gt 0) { exit 2 }
exit 0
