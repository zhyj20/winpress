[CmdletBinding()]
param(
  [string]$ComposeFile,
  [string]$ArtifactPath
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
  $ComposeFile = Join-Path $projectRoot 'docker-compose.local-demo.yml'
}

if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
  $ArtifactPath = Join-Path $projectRoot 'backend\target-local\winpress-commercial-1.0.0.jar'
}

$resolvedComposeFile = (Resolve-Path -LiteralPath $ComposeFile).Path
$resolvedArtifactPath = (Resolve-Path -LiteralPath $ArtifactPath).Path
if (-not (Test-Path -LiteralPath $resolvedArtifactPath -PathType Leaf)) {
  throw "Prepared local backend artifact was not found: $resolvedArtifactPath"
}

$hostHash = (Get-FileHash -LiteralPath $resolvedArtifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
$containerOutput = & docker compose -f $resolvedComposeFile exec -T backend sha256sum /app/app.jar
if ($LASTEXITCODE -ne 0) {
  throw "Could not read the running local backend artifact (exit code $LASTEXITCODE)."
}

$containerHash = [regex]::Match(($containerOutput -join "`n"), '^[0-9a-fA-F]{64}').Value.ToLowerInvariant()
if ([string]::IsNullOrWhiteSpace($containerHash)) {
  throw 'The running local backend did not return a valid SHA-256 checksum.'
}
if ($containerHash -ne $hostHash) {
  throw "Local backend artifact mismatch: host=$hostHash container=$containerHash"
}

[pscustomobject]@{
  Artifact = $resolvedArtifactPath
  Sha256 = $hostHash
  Runtime = 'backend:/app/app.jar'
  Match = $true
}
