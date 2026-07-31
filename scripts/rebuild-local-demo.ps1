[CmdletBinding()]
param(
  [ValidateRange(30, 300)]
  [int]$WaitTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $projectRoot 'docker-compose.local-demo.yml'
$portCheck = Join-Path $PSScriptRoot 'check-ports.ps1'
$prepareBackend = Join-Path $PSScriptRoot 'prepare-local-docker-backend.ps1'

function Invoke-Checked {
  param(
    [string]$FilePath,
    [string[]]$Arguments
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code $($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
  }
}

if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
  throw "Local demo Compose file was not found: $composeFile"
}

# A currently running stack from this checkout is an allowed owner of its own ports. Any other
# process still causes the existing port-preflight to fail before Docker is asked to rebuild.
Invoke-Checked -FilePath 'powershell.exe' -Arguments @(
  '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $portCheck,
  '-Scope', 'all', '-AllowCurrentDeployment'
)

# Dockerfile.local deliberately copies this prepared artifact instead of compiling inside Docker.
# Always refresh it first so a direct Compose build cannot deploy a stale target-local JAR.
Invoke-Checked -FilePath 'powershell.exe' -Arguments @(
  '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $prepareBackend
)

Push-Location $projectRoot
try {
  Invoke-Checked -FilePath 'docker' -Arguments @(
    'compose', '-f', $composeFile, 'build', 'backend', 'frontend'
  )
  Invoke-Checked -FilePath 'docker' -Arguments @(
    'compose', '-f', $composeFile, 'up', '-d', '--wait',
    '--wait-timeout', $WaitTimeoutSeconds, 'backend', 'frontend'
  )
  Invoke-Checked -FilePath 'docker' -Arguments @('compose', '-f', $composeFile, 'ps')
}
finally {
  Pop-Location
}
