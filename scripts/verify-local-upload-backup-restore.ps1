[CmdletBinding()]
param(
  [string]$BackendContainer = 'winpress-commercial-backend',
  [string]$HelperImage = 'postgres:17-alpine'
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$expectedComposeFile = (Resolve-Path (Join-Path $projectRoot 'docker-compose.local-demo.yml')).Path
$temporaryRoot = [System.IO.Path]::GetTempPath().TrimEnd('\')
$runId = [Guid]::NewGuid().ToString('N')
$stagingDirectory = Join-Path $temporaryRoot "winpress-upload-restore-$runId"
$archivePath = Join-Path $stagingDirectory 'uploads.tar.gz'
$restoreVolume = "winpress-upload-restore-$runId"
$helperContainer = "winpress-upload-helper-$runId"
$restoreVolumeCreated = $false

function Invoke-Docker {
  param([string[]]$Arguments)

  $output = & docker @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "Docker command failed: $($Arguments[0])"
  }
  return $output
}

function Get-FileLineCount {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Expected restore evidence is missing: $(Split-Path -Leaf $Path)"
  }
  return @((Get-Content -LiteralPath $Path -Encoding UTF8)).Count
}

function Assert-MatchingEvidence {
  param(
    [string]$SourcePath,
    [string]$RestorePath,
    [string]$EvidenceName
  )

  $sourceHash = (Get-FileHash -LiteralPath $SourcePath -Algorithm SHA256).Hash
  $restoreHash = (Get-FileHash -LiteralPath $RestorePath -Algorithm SHA256).Hash
  if ($sourceHash -ne $restoreHash) {
    throw "Restored upload evidence does not match the source snapshot: $EvidenceName"
  }
}

function Remove-TemporaryArtifacts {
  if ($helperContainer -like 'winpress-upload-helper-*') {
    $existingHelper = @(& docker ps -a --filter "name=^/${helperContainer}$" --format '{{.Names}}' 2>$null)
    if ($existingHelper -contains $helperContainer) {
      & docker rm -f $helperContainer *> $null
    }
  }

  if ($restoreVolumeCreated -and $restoreVolume -like 'winpress-upload-restore-*') {
    $existingVolume = @(& docker volume ls --filter "name=^${restoreVolume}$" --format '{{.Name}}' 2>$null)
    if ($existingVolume -contains $restoreVolume) {
      & docker volume rm -f $restoreVolume *> $null
    }
  }

  if (Test-Path -LiteralPath $stagingDirectory) {
    $resolvedStagingDirectory = (Resolve-Path -LiteralPath $stagingDirectory).Path
    if ($resolvedStagingDirectory.StartsWith(($temporaryRoot + '\'), [System.StringComparison]::OrdinalIgnoreCase)) {
      Remove-Item -LiteralPath $resolvedStagingDirectory -Recurse -Force
    }
  }
}

try {
  $inspectRaw = & docker inspect $BackendContainer 2>&1
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($inspectRaw -join ''))) {
    throw 'The local demonstration backend container is not available.'
  }

  $inspect = (($inspectRaw -join "`n") | ConvertFrom-Json)[0]
  if (-not $inspect.State.Running -or $inspect.State.Health.Status -ne 'healthy') {
    throw 'The local demonstration backend container must be running and healthy.'
  }

  $composeFileLabel = [string]$inspect.Config.Labels.'com.docker.compose.project.config_files'
  $workingDirectoryLabel = [string]$inspect.Config.Labels.'com.docker.compose.project.working_dir'
  if (
    -not $composeFileLabel.Equals($expectedComposeFile, [System.StringComparison]::OrdinalIgnoreCase) -or
    -not $workingDirectoryLabel.Equals($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)
  ) {
    throw 'The selected backend is not this project local-demonstration deployment. Production or unrelated containers are not accepted.'
  }

  $uploadMount = @($inspect.Mounts | Where-Object {
    $_.Type -eq 'volume' -and $_.Destination -eq '/app/storage/uploads'
  })
  if ($uploadMount.Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$uploadMount[0].Name)) {
    throw 'The local demonstration upload volume cannot be identified unambiguously.'
  }
  $sourceVolume = [string]$uploadMount[0].Name

  $volumeInspectRaw = & docker volume inspect $sourceVolume 2>&1
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($volumeInspectRaw -join ''))) {
    throw 'The local demonstration upload volume is not available.'
  }
  $volumeInspect = (($volumeInspectRaw -join "`n") | ConvertFrom-Json)[0]
  if (
    [string]$volumeInspect.Labels.'com.docker.compose.project' -ne 'winpress-commercial-v42' -or
    [string]$volumeInspect.Labels.'com.docker.compose.volume' -ne 'winpress_commercial_uploads'
  ) {
    throw 'The selected upload volume is not the current project local-demonstration volume.'
  }

  New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

  $backupCommand = @'
set -eu
cd /source
tar -czf /backup/uploads.tar.gz .
find . -type d -print | LC_ALL=C sort > /backup/source-directories.txt
if find . -type f -print -quit | grep -q .; then
  find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do sha256sum "$file"; done > /backup/source-manifest.txt
  find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do stat -c '%u|%g|%a|%s|%n' "$file"; done > /backup/source-modes.txt
else
  : > /backup/source-manifest.txt
  : > /backup/source-modes.txt
fi
find . -type f -exec stat -c '%s' '{}' ';' | awk '{ total += $1 } END { print total + 0 }' > /backup/source-bytes.txt
'@

  Invoke-Docker @(
    'run', '--rm',
    '--name', $helperContainer,
    '--entrypoint', 'sh',
    '--mount', "type=volume,source=$sourceVolume,target=/source,readonly",
    '--mount', "type=bind,source=$stagingDirectory,target=/backup",
    $HelperImage,
    '-ec', $backupCommand
  ) | Out-Null

  if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
    throw 'The upload backup archive was not created.'
  }
  $archive = Get-Item -LiteralPath $archivePath
  if ($archive.Length -lt 64) {
    throw 'The upload backup archive is unexpectedly small.'
  }
  $archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()

  Invoke-Docker @(
    'volume', 'create',
    '--label', 'winpress.qa.purpose=upload-backup-restore',
    $restoreVolume
  ) | Out-Null
  $restoreVolumeCreated = $true

  $restoreCommand = @'
set -eu
tar -xzf /backup/uploads.tar.gz -C /restore
cd /restore
find . -type d -print | LC_ALL=C sort > /backup/restore-directories.txt
if find . -type f -print -quit | grep -q .; then
  find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do sha256sum "$file"; done > /backup/restore-manifest.txt
  find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do stat -c '%u|%g|%a|%s|%n' "$file"; done > /backup/restore-modes.txt
else
  : > /backup/restore-manifest.txt
  : > /backup/restore-modes.txt
fi
find . -type f -exec stat -c '%s' '{}' ';' | awk '{ total += $1 } END { print total + 0 }' > /backup/restore-bytes.txt
'@

  Invoke-Docker @(
    'run', '--rm',
    '--name', $helperContainer,
    '--entrypoint', 'sh',
    '--mount', "type=volume,source=$restoreVolume,target=/restore",
    '--mount', "type=bind,source=$stagingDirectory,target=/backup",
    $HelperImage,
    '-ec', $restoreCommand
  ) | Out-Null

  Assert-MatchingEvidence `
    -SourcePath (Join-Path $stagingDirectory 'source-manifest.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-manifest.txt') `
    -EvidenceName 'file hashes'
  Assert-MatchingEvidence `
    -SourcePath (Join-Path $stagingDirectory 'source-directories.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-directories.txt') `
    -EvidenceName 'directory set'
  Assert-MatchingEvidence `
    -SourcePath (Join-Path $stagingDirectory 'source-modes.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-modes.txt') `
    -EvidenceName 'file ownership, modes and sizes'
  Assert-MatchingEvidence `
    -SourcePath (Join-Path $stagingDirectory 'source-bytes.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-bytes.txt') `
    -EvidenceName 'total bytes'

  $fileCount = Get-FileLineCount -Path (Join-Path $stagingDirectory 'source-manifest.txt')
  $directoryCount = Get-FileLineCount -Path (Join-Path $stagingDirectory 'source-directories.txt')
  $totalBytes = [long]((Get-Content -LiteralPath (Join-Path $stagingDirectory 'source-bytes.txt') -Raw -Encoding UTF8).Trim())

  [pscustomobject]@{
    Result = 'PASS'
    SourceBoundary = 'current project local demonstration upload volume only'
    RestoreIsolation = 'temporary Docker volume without application or host exposure'
    ArchiveFormat = 'tar.gz'
    ArchiveBytes = [long]$archive.Length
    ArchiveSha256 = $archiveHash
    Files = $fileCount
    Directories = $directoryCount
    RestoredFileBytes = $totalBytes
    FileHashesMatch = $true
    FileOwnershipModesAndSizesMatch = $true
    FileNamesOrContentsPrinted = $false
    ProductionAcceptance = 'not asserted'
  }
}
finally {
  Remove-TemporaryArtifacts
}
