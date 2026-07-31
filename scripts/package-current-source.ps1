[CmdletBinding()]
param(
  [string]$OutputDirectory = '',
  [string]$Stamp = (Get-Date -Format 'yyyyMMdd-HHmmss')
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
  $OutputDirectory = Join-Path $projectRoot 'release'
}
$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutputDirectory | Out-Null

$archiveName = "winpress-commercial-current-source-$Stamp.zip"
$archivePath = Join-Path $resolvedOutputDirectory $archiveName
$checksumPath = "$archivePath.sha256"
if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
  throw "Refusing to overwrite existing archive: $archivePath"
}

$tempRoot = (Resolve-Path ([System.IO.Path]::GetTempPath())).Path.TrimEnd('\')
$stagingDirectory = Join-Path $tempRoot ("winpress-source-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

$excludedDirectories = @(
  'node_modules', 'dist', 'target', 'target-local', '.git', 'release', 'storage', 'logs',
  'backups', '_codex-backups', '.codex', '.agents', 'tmp', '.vite', '.cache', 'coverage',
  'test-results', 'playwright-report', '.qa', 'qa', 'BOOT-INF', 'META-INF', 'tmpWeb',
  'qa-lo-profile-geo-openapi'
)
$excludedRelativePaths = @(
  'docs\TEST-ACCOUNTS.md',
  'database\winpress_full.sql'
)

try {
  $included = Get-ChildItem -LiteralPath $projectRoot -Recurse -File -Force | Where-Object {
    $relativePath = $_.FullName.Substring($projectRoot.Length).TrimStart('\', '/')
    $segments = $relativePath -split '[\\/]'
    $hasExcludedDirectory = @($segments | Where-Object {
      $excludedDirectories -contains $_ -or $_ -like '.codex-patch-backup*' -or $_ -like 'qa-render-*'
    }).Count -gt 0
    $isExcludedPath = $excludedRelativePaths -contains $relativePath
    $isEnvironmentTemplate = $_.Name -eq '.env.example'
    $isSensitiveEnvironment = $_.Name -like '.env*' -and -not $isEnvironmentTemplate
    $isSensitiveOrArtifact = $isSensitiveEnvironment -or $_.Name -eq '_perm_test.txt' -or $_.Name -like '*.tsbuildinfo' -or $_.Extension -in @('.err', '.hprof', '.log', '.out', '.pid', '.sha256', '.tmp', '.zip')
    -not $hasExcludedDirectory -and -not $isExcludedPath -and -not $isSensitiveOrArtifact
  }

  foreach ($file in $included) {
    $relativePath = $file.FullName.Substring($projectRoot.Length).TrimStart('\', '/')
    $destination = Join-Path $stagingDirectory $relativePath
    $destinationDirectory = Split-Path -Parent $destination
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
  }

  Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $stagingDirectory -Force | Select-Object -ExpandProperty FullName) -DestinationPath $archivePath -CompressionLevel Optimal
  $hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
  [System.IO.File]::WriteAllText($checksumPath, "$hash *$archiveName`n", [System.Text.UTF8Encoding]::new($false))

  [pscustomobject]@{
    Archive = $archivePath
    Sha256 = $hash
    ChecksumFile = $checksumPath
    SourceFiles = @($included).Count
  }
}
finally {
  if (Test-Path -LiteralPath $stagingDirectory) {
    $resolvedStagingDirectory = (Resolve-Path $stagingDirectory).Path
    if ($resolvedStagingDirectory.StartsWith(($tempRoot + '\'), [System.StringComparison]::OrdinalIgnoreCase)) {
      Remove-Item -LiteralPath $resolvedStagingDirectory -Recurse -Force
    }
  }
}
