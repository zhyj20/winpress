[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendRoot = Join-Path $projectRoot 'backend'
$temporaryRoot = (Resolve-Path ([System.IO.Path]::GetTempPath())).Path.TrimEnd('\')
$buildRoot = Join-Path $temporaryRoot ("winpress-backend-local-build-" + [Guid]::NewGuid().ToString('N'))
$targetDirectory = Join-Path $backendRoot 'target-local'
$targetArtifact = Join-Path $targetDirectory 'winpress-commercial-1.0.0.jar'

if (-not (Test-Path -LiteralPath $backendRoot -PathType Container)) {
  throw "Backend source directory was not found: $backendRoot"
}

New-Item -ItemType Directory -Path $buildRoot | Out-Null

try {
  $sourceFiles = Get-ChildItem -LiteralPath $backendRoot -Recurse -File -Force | Where-Object {
    $relativePath = $_.FullName.Substring($backendRoot.Length).TrimStart('\', '/')
    $segments = $relativePath -split '[\\/]'
    -not (@($segments | Where-Object { $_ -in @('target', 'target-local') }).Count -gt 0)
  }

  foreach ($sourceFile in $sourceFiles) {
    $relativePath = $sourceFile.FullName.Substring($backendRoot.Length).TrimStart('\', '/')
    $destination = Join-Path $buildRoot $relativePath
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath $sourceFile.FullName -Destination $destination -Force
  }

  # Reuse a locally cached Maven distribution. Calling mvnw from a temporary tree can
  # trigger a fresh Maven download even when this machine already has a verified runtime.
  $mavenDistributionRoot = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
  $maven = Get-ChildItem -LiteralPath $mavenDistributionRoot -Recurse -Filter 'mvn.cmd' -File -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
  if ([string]::IsNullOrWhiteSpace($maven)) {
    throw 'No locally cached Maven runtime was found. Run backend\\mvnw.cmd once in a network-enabled environment before preparing the local Docker backend.'
  }

  # -f keeps all generated output under the isolated temporary source copy.
  $temporaryPom = Join-Path $buildRoot 'pom.xml'
  & $maven -q -f $temporaryPom package
  if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE."
  }

  $builtArtifact = Join-Path $buildRoot 'target\winpress-commercial-1.0.0.jar'
  if (-not (Test-Path -LiteralPath $builtArtifact -PathType Leaf)) {
    throw "Expected backend artifact was not produced: $builtArtifact"
  }

  New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null
  Copy-Item -LiteralPath $builtArtifact -Destination $targetArtifact -Force

  [pscustomobject]@{
    Artifact = $targetArtifact
    Sha256 = (Get-FileHash -LiteralPath $targetArtifact -Algorithm SHA256).Hash.ToLowerInvariant()
  }
}
finally {
  if (Test-Path -LiteralPath $buildRoot) {
    $resolvedBuildRoot = (Resolve-Path -LiteralPath $buildRoot).Path
    if ($resolvedBuildRoot.StartsWith(($temporaryRoot + '\'), [System.StringComparison]::OrdinalIgnoreCase)) {
      Remove-Item -LiteralPath $resolvedBuildRoot -Recurse -Force
    }
  }
}
