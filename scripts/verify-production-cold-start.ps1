[CmdletBinding()]
param(
  [switch]$KeepRunning,
  [switch]$CleanupOnly,
  [int]$PostgresPort = 0,
  [int]$RedisPort = 0,
  [int]$FrontendPort = 0,
  [int]$BackendPort = 0,
  [string]$StateFile = (Join-Path ([System.IO.Path]::GetTempPath()) 'winpress-production-cold-start-state.json')
)

<#
Runs an isolated, production-equivalent cold-start acceptance check.

The check builds the real production images with isolated QA tags, starts a new empty database,
verifies the production data and integration boundaries, and removes the stack again. Use
-KeepRunning only when a short browser review is required, then invoke -CleanupOnly.

It never prints generated passwords or production credentials. It refuses to run when any real
winpress-production-* container already exists.
#>

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composePath = Join-Path $projectRoot 'docker-compose.production.yml'
$preflightPath = Join-Path $PSScriptRoot 'verify-production-readiness.ps1'
$boundaryPath = Join-Path $PSScriptRoot 'verify-production-compose-boundaries.ps1'
$qaProjectName = 'winpress-production-cold-start'
$containerNames = @(
  'winpress-production-postgres',
  'winpress-production-redis',
  'winpress-production-backend',
  'winpress-production-frontend'
)
$qaImageNames = @(
  'winpress-commercial-backend:production-cold-start',
  'winpress-commercial-frontend:production-cold-start'
)
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())

function Assert-TemporaryPath {
  param([string]$Path)
  $fullPath = [System.IO.Path]::GetFullPath($Path)
  $rootWithSeparator = $tempRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
  if (-not $fullPath.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Cold-start state and secret files must remain inside the operating-system temporary directory.'
  }
  return $fullPath
}

function Get-RandomHex {
  param([int]$ByteCount = 32)
  $bytes = New-Object byte[] $ByteCount
  $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $generator.GetBytes($bytes)
  } finally {
    $generator.Dispose()
  }
  return [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function Get-FreeTcpPort {
  $listener = New-Object System.Net.Sockets.TcpListener(
    [System.Net.IPAddress]::Loopback,
    0
  )
  try {
    $listener.Start()
    return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
  } finally {
    $listener.Stop()
  }
}

function Resolve-UniquePorts {
  param([int[]]$RequestedPorts)
  $resolved = New-Object System.Collections.Generic.List[int]
  foreach ($requested in $RequestedPorts) {
    if ($requested -lt 0 -or $requested -gt 65535) {
      throw 'Cold-start ports must be zero for automatic selection or valid TCP ports.'
    }
    $candidate = $requested
    do {
      if ($candidate -eq 0) {
        $candidate = Get-FreeTcpPort
      }
    } while ($resolved.Contains($candidate))
    $resolved.Add($candidate)
  }
  return $resolved.ToArray()
}

function Get-ExistingTargetContainers {
  $allNames = @(& docker ps -a --format '{{.Names}}')
  if ($LASTEXITCODE -ne 0) {
    throw 'Docker is not available for the production cold-start check.'
  }
  return @($allNames | Where-Object { $_ -in $containerNames })
}

function Assert-ContainerOwnership {
  foreach ($name in Get-ExistingTargetContainers) {
    $raw = & docker inspect $name
    if ($LASTEXITCODE -ne 0) {
      throw "Unable to inspect the cold-start container: $name."
    }
    $inspection = @($raw | ConvertFrom-Json)[0]
    $owner = [string]$inspection.Config.Labels.'com.docker.compose.project'
    if ($owner -ne $qaProjectName) {
      throw "Refusing to change $name because it does not belong to the isolated cold-start project."
    }
  }
}

function Remove-TemporaryFile {
  param([string]$Path)
  if ([string]::IsNullOrWhiteSpace($Path)) { return }
  $safePath = Assert-TemporaryPath -Path $Path
  if (Test-Path -LiteralPath $safePath -PathType Leaf) {
    Remove-Item -LiteralPath $safePath -Force
  }
}

function Remove-ColdStartStack {
  param(
    [string]$EnvironmentFile,
    [string]$OverrideFile,
    [string]$StatePath
  )
  Assert-ContainerOwnership

  if (
    -not [string]::IsNullOrWhiteSpace($EnvironmentFile) -and
    -not [string]::IsNullOrWhiteSpace($OverrideFile) -and
    (Test-Path -LiteralPath $EnvironmentFile -PathType Leaf) -and
    (Test-Path -LiteralPath $OverrideFile -PathType Leaf)
  ) {
    # Keep cleanup compatible with a state file produced before a newly required Compose
    # variable existed. The process-only value is used solely to let Compose parse the
    # isolated stack and is restored immediately afterwards.
    $previousStorageMaxFileBytes = $env:WINPRESS_STORAGE_MAX_FILE_BYTES
    try {
      if ([string]::IsNullOrWhiteSpace($env:WINPRESS_STORAGE_MAX_FILE_BYTES)) {
        $env:WINPRESS_STORAGE_MAX_FILE_BYTES = '20971520'
      }
      & docker compose `
        --env-file $EnvironmentFile `
        -p $qaProjectName `
        -f $composePath `
        -f $OverrideFile `
        down -v --remove-orphans
      if ($LASTEXITCODE -ne 0) {
        throw 'The isolated production cold-start stack could not be removed cleanly.'
      }
    } finally {
      if ($null -eq $previousStorageMaxFileBytes) {
        Remove-Item Env:WINPRESS_STORAGE_MAX_FILE_BYTES -ErrorAction SilentlyContinue
      } else {
        $env:WINPRESS_STORAGE_MAX_FILE_BYTES = $previousStorageMaxFileBytes
      }
    }
  } elseif ((Get-ExistingTargetContainers).Count -gt 0) {
    throw 'Cold-start containers exist but the protected temporary Compose inputs are missing.'
  }

  foreach ($imageName in $qaImageNames) {
    $imageId = & docker image ls --quiet $imageName
    if ($LASTEXITCODE -ne 0) {
      throw "Unable to inspect isolated image $imageName."
    }
    if (-not [string]::IsNullOrWhiteSpace(($imageId -join ''))) {
      & docker image rm $imageName | Out-Null
      if ($LASTEXITCODE -ne 0) {
        throw "Unable to remove isolated image $imageName."
      }
    }
  }

  $remainingVolumes = @(
    & docker volume ls --quiet --filter "label=com.docker.compose.project=$qaProjectName"
  )
  if ($LASTEXITCODE -ne 0) {
    throw 'Unable to verify isolated volume cleanup.'
  }
  if ($remainingVolumes.Count -gt 0) {
    throw 'One or more isolated production cold-start volumes remain after cleanup.'
  }
  if ((Get-ExistingTargetContainers).Count -gt 0) {
    throw 'One or more isolated production cold-start containers remain after cleanup.'
  }

  Remove-TemporaryFile -Path $EnvironmentFile
  Remove-TemporaryFile -Path $OverrideFile
  Remove-TemporaryFile -Path $StatePath
  Write-Output 'Production cold-start cleanup passed: isolated containers, volumes, images, and temporary credentials were removed.'
}

function Wait-HealthyContainer {
  param(
    [string]$Name,
    [int]$TimeoutSeconds = 360
  )
  $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
  $lastStatus = ''
  while ([DateTime]::UtcNow -lt $deadline) {
    $status = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $Name 2>$null)
    if ($LASTEXITCODE -eq 0) {
      $status = ([string]$status).Trim()
      if ($status -ne $lastStatus) {
        Write-Output "$Name state: $status"
        $lastStatus = $status
      }
      if ($status -eq 'healthy') { return }
      if ($status -in @('exited', 'dead', 'unhealthy')) {
        throw "$Name entered terminal state: $status."
      }
    }
    Start-Sleep -Seconds 2
  }
  throw "$Name did not become healthy within $TimeoutSeconds seconds."
}

function Get-HttpStatus {
  param([string]$Uri)
  try {
    return [int](Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 15).StatusCode
  } catch {
    if ($null -ne $_.Exception.Response) {
      return [int]$_.Exception.Response.StatusCode
    }
    throw
  }
}

function Invoke-CorsPreflight {
  param(
    [string]$Uri,
    [string]$Origin
  )
  $headers = @{
    Origin = $Origin
    'Access-Control-Request-Method' = 'GET'
    'Access-Control-Request-Headers' = 'authorization'
  }
  try {
    $response = Invoke-WebRequest `
      -UseBasicParsing `
      -Method Options `
      -Uri $Uri `
      -Headers $headers `
      -TimeoutSec 15
    return [pscustomobject]@{
      StatusCode = [int]$response.StatusCode
      AllowedOrigin = [string]$response.Headers['Access-Control-Allow-Origin']
    }
  } catch {
    if ($null -eq $_.Exception.Response) {
      throw
    }
    return [pscustomobject]@{
      StatusCode = [int]$_.Exception.Response.StatusCode
      AllowedOrigin = [string]$_.Exception.Response.Headers['Access-Control-Allow-Origin']
    }
  }
}

function Invoke-DatabaseJson {
  param(
    [string]$Database,
    [string]$User,
    [string]$Sql
  )
  $result = & docker exec winpress-production-postgres `
    psql -U $User -d $Database -Atc $Sql
  if ($LASTEXITCODE -ne 0) {
    throw 'A production cold-start database assertion could not be evaluated.'
  }
  return (($result -join '') | ConvertFrom-Json)
}

function Assert-LoopbackBinding {
  param(
    [string]$ContainerName,
    [string]$ContainerPort,
    [int]$ExpectedHostPort
  )
  $raw = & docker inspect $ContainerName
  if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect port bindings for $ContainerName."
  }
  $inspection = @($raw | ConvertFrom-Json)[0]
  $portProperty = $inspection.NetworkSettings.Ports.PSObject.Properties[$ContainerPort]
  if ($null -eq $portProperty -or $null -eq $portProperty.Value) {
    throw "$ContainerName does not expose the expected container port $ContainerPort."
  }
  $bindings = @($portProperty.Value)
  if (
    $bindings.Count -ne 1 -or
    [string]$bindings[0].HostIp -ne '127.0.0.1' -or
    [int]$bindings[0].HostPort -ne $ExpectedHostPort
  ) {
    throw "$ContainerName is not bound exclusively to the expected loopback port."
  }
}

$resolvedStateFile = Assert-TemporaryPath -Path $StateFile

if ($CleanupOnly) {
  if (-not (Test-Path -LiteralPath $resolvedStateFile -PathType Leaf)) {
    throw 'No isolated production cold-start state file is available for cleanup.'
  }
  $savedState = Get-Content -LiteralPath $resolvedStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
  if ([string]$savedState.projectName -ne $qaProjectName) {
    throw 'The cold-start state file does not belong to the expected isolated project.'
  }
  Remove-ColdStartStack `
    -EnvironmentFile ([string]$savedState.environmentFile) `
    -OverrideFile ([string]$savedState.overrideFile) `
    -StatePath $resolvedStateFile
  return
}

if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
  throw 'The production Compose file is missing.'
}
if (-not (Test-Path -LiteralPath $preflightPath -PathType Leaf)) {
  throw 'The production readiness preflight is missing.'
}
if (-not (Test-Path -LiteralPath $boundaryPath -PathType Leaf)) {
  throw 'The production boundary verifier is missing.'
}
if (Test-Path -LiteralPath $resolvedStateFile -PathType Leaf) {
  throw 'A previous isolated cold-start state file exists. Run this script with -CleanupOnly first.'
}

$existingContainers = @(Get-ExistingTargetContainers)
if ($existingContainers.Count -gt 0) {
  throw "Production-named containers already exist: $($existingContainers -join ', '). The isolated check will not touch them."
}

$ports = Resolve-UniquePorts -RequestedPorts @(
  $PostgresPort,
  $RedisPort,
  $FrontendPort,
  $BackendPort
)
$PostgresPort = $ports[0]
$RedisPort = $ports[1]
$FrontendPort = $ports[2]
$BackendPort = $ports[3]

$runId = [Guid]::NewGuid().ToString('N')
$environmentFile = Assert-TemporaryPath -Path (
  Join-Path $tempRoot "winpress-production-cold-start-$runId.env"
)
$overrideFile = Assert-TemporaryPath -Path (
  Join-Path $tempRoot "winpress-production-cold-start-$runId.override.yml"
)
$postgresPassword = Get-RandomHex
$redisPassword = Get-RandomHex
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

$environmentLines = @(
  'POSTGRES_DB=winpress_cold_start',
  'POSTGRES_USER=winpress_cold_start',
  "POSTGRES_PASSWORD=$postgresPassword",
  "POSTGRES_PORT=$PostgresPort",
  "REDIS_PASSWORD=$redisPassword",
  "REDIS_PORT=$RedisPort",
  "FRONTEND_PORT=$FrontendPort",
  "BACKEND_PORT=$BackendPort",
  'WINPRESS_STORAGE_MAX_FILE_BYTES=20971520',
  'WINPRESS_CORS_ORIGINS=https://qa.winpress.invalid',
  'WINPRESS_API_DOCS_ENABLED=false',
  'WINPRESS_NIUMEDIA_BASE_URL=',
  'WINPRESS_NIUMEDIA_TOKEN=',
  'WINPRESS_FEDERATION_ENABLED=false',
  'WINPRESS_FEDERATION_MIGRATE_ON_START=false',
  'WINPRESS_FEDERATION_SHARED_SECRET=',
  'WINPRESS_FEDERATION_PLATFORM_ISSUER=niumedia-platform',
  'WINPRESS_FEDERATION_WINPRESS_ISSUER=winpress-commercial',
  'WINPRESS_FEDERATION_SOURCE_INSTANCE_ID=cold-start',
  'WINPRESS_FEDERATION_GEO_CALLBACK_URL=',
  'WINPRESS_FEDERATION_CALLBACK_TIMEOUT_SECONDS=15',
  'WINPRESS_FEDERATION_MAX_REQUESTS_PER_MINUTE=120'
)
[System.IO.File]::WriteAllLines($environmentFile, $environmentLines, $utf8WithoutBom)

$overrideLines = @(
  'services:',
  '  backend:',
  '    image: winpress-commercial-backend:production-cold-start',
  '  frontend:',
  '    image: winpress-commercial-frontend:production-cold-start'
)
[System.IO.File]::WriteAllLines($overrideFile, $overrideLines, $utf8WithoutBom)

$state = [ordered]@{
  projectName = $qaProjectName
  environmentFile = $environmentFile
  overrideFile = $overrideFile
  frontendPort = $FrontendPort
  backendPort = $BackendPort
  postgresPort = $PostgresPort
  redisPort = $RedisPort
  createdAt = [DateTimeOffset]::Now.ToString('o')
}
[System.IO.File]::WriteAllText(
  $resolvedStateFile,
  ($state | ConvertTo-Json),
  $utf8WithoutBom
)

$verificationPassed = $false
try {
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $boundaryPath
  if ($LASTEXITCODE -ne 0) {
    throw 'Production Compose boundary verification failed.'
  }
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $preflightPath -EnvFile $environmentFile
  if ($LASTEXITCODE -ne 0) {
    throw 'Production readiness preflight failed.'
  }

  Write-Output 'Building isolated production images...'
  & docker compose `
    --env-file $environmentFile `
    -p $qaProjectName `
    -f $composePath `
    -f $overrideFile `
    build --quiet
  if ($LASTEXITCODE -ne 0) {
    throw 'The production images could not be built.'
  }

  Write-Output 'Starting isolated production stack...'
  & docker compose `
    --env-file $environmentFile `
    -p $qaProjectName `
    -f $composePath `
    -f $overrideFile `
    up -d --no-build
  if ($LASTEXITCODE -ne 0) {
    throw 'The isolated production stack could not be started.'
  }

  foreach ($containerName in $containerNames) {
    Wait-HealthyContainer -Name $containerName
  }
  Assert-ContainerOwnership

  $healthUri = "http://127.0.0.1:$BackendPort/api/v1/health"
  $frontendUri = "http://127.0.0.1:$FrontendPort/"
  $health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 15
  if (
    -not $health.success -or
    [string]$health.data.status -ne 'UP' -or
    [string]$health.data.database -ne 'UP' -or
    [string]$health.data.schemaStatus -ne 'UP'
  ) {
    throw 'The isolated backend generic readiness health check does not report the accepted database and schema state.'
  }

  if ((Get-HttpStatus -Uri $frontendUri) -ne 200) {
    throw 'The isolated production frontend is not reachable.'
  }
  foreach ($internalDocumentationPath in @(
    'swagger-ui/index.html',
    'swagger-ui.html',
    'v3/api-docs'
  )) {
    if ((Get-HttpStatus -Uri "http://127.0.0.1:$BackendPort/$internalDocumentationPath") -ne 404) {
      throw "The production backend unexpectedly exposes internal API documentation: $internalDocumentationPath."
    }
  }

  $acceptedCors = Invoke-CorsPreflight `
    -Uri $healthUri `
    -Origin 'https://qa.winpress.invalid'
  if (
    $acceptedCors.StatusCode -notin @(200, 204) -or
    $acceptedCors.AllowedOrigin -ne 'https://qa.winpress.invalid'
  ) {
    throw 'The explicitly configured production HTTPS origin was not accepted.'
  }
  foreach ($rejectedOrigin in @('http://localhost:5217', 'http://winpress.cn')) {
    $rejectedCors = Invoke-CorsPreflight -Uri $healthUri -Origin $rejectedOrigin
    if (
      $rejectedCors.StatusCode -in @(200, 204) -or
      -not [string]::IsNullOrWhiteSpace($rejectedCors.AllowedOrigin)
    ) {
      throw "The production backend accepted an origin outside its explicit HTTPS allow-list: $rejectedOrigin."
    }
  }

  if ((Get-HttpStatus -Uri "${frontendUri}files/FIL-NOT-FOUND") -ne 404) {
    throw 'The production frontend unexpectedly exposes uploaded files directly.'
  }
  if ((Get-HttpStatus -Uri "${frontendUri}test-accounts.html") -ne 404) {
    throw 'The production frontend unexpectedly exposes test-account material.'
  }
  if ((Get-HttpStatus -Uri "${frontendUri}api/v1/media-discovery/status") -ne 401) {
    throw 'The media-discovery status endpoint is not protected by authentication.'
  }
  if ((Get-HttpStatus -Uri "${frontendUri}api/v1/integrations/geo/status") -ne 404) {
    throw 'The disabled GEO integration unexpectedly exposes a public status endpoint.'
  }

  $counts = Invoke-DatabaseJson `
    -Database 'winpress_cold_start' `
    -User 'winpress_cold_start' `
    -Sql @'
SELECT json_build_object(
  'app_user', (SELECT count(*) FROM app_user),
  'organization', (SELECT count(*) FROM organization),
  'customer_requirement', (SELECT count(*) FROM customer_requirement),
  'project', (SELECT count(*) FROM project),
  'publish_channel', (SELECT count(*) FROM publish_channel),
  'channel_quote', (SELECT count(*) FROM channel_quote),
  'supplier', (SELECT count(*) FROM supplier),
  'supplier_order', (SELECT count(*) FROM supplier_order),
  'business_inquiry', (SELECT count(*) FROM business_inquiry),
  'acceptance_evidence_item', (
    SELECT count(*) FROM platform_acceptance_evidence_item WHERE required
  ),
  'schema_migration_ledger', (
    SELECT count(*) FROM schema_migration_ledger
  ),
  'schema_migration_baseline', (
    SELECT count(*) FROM schema_migration_ledger
    WHERE migration_version=36
      AND script_name='36-schema-migration-ledger.sql'
      AND release_contract='winpress-v4.2.25-20260731'
      AND apply_mode='BASELINE'
      AND verification_reference='SCHEMA35_STRUCTURAL_BASELINE_20260731'
  ),
  'schema_migration_media_result_integrity', (
    SELECT count(*) FROM schema_migration_ledger
    WHERE migration_version=37
      AND script_name='37-media-pr-result-integrity.sql'
      AND release_contract='winpress-v4.2.26-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='MEDIA_PR_RESULT_CHAIN_INTEGRITY_20260731'
  ),
  'schema_migration_writing_assignment_schedule_integrity', (
    SELECT count(*) FROM schema_migration_ledger
    WHERE migration_version=38
      AND script_name='38-writing-assignment-slot-schedule-integrity.sql'
      AND release_contract='winpress-v4.2.27-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='WRITING_ASSIGNMENT_SLOT_AND_SCHEDULE_INTEGRITY_20260731'
  ),
  'schema_migration_writing_assignment_radius_integrity', (
    SELECT count(*) FROM schema_migration_ledger
    WHERE migration_version=39
      AND script_name='39-writing-assignment-radius-integrity.sql'
      AND release_contract='winpress-v4.2.28-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731'
  ),
  'schema_migration_conference_work_item_integrity', (
    SELECT count(*) FROM schema_migration_ledger
    WHERE migration_version=40
      AND script_name='40-conference-work-item-state-integrity.sql'
      AND release_contract='winpress-v4.2.29-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='CONFERENCE_WORK_ITEM_STATE_INTEGRITY_20260731'
  ),
  'schema_migration_conference_media_candidate_integrity', (
    SELECT count(*) FROM schema_migration_ledger
    WHERE migration_version=41
      AND script_name='41-conference-media-candidate-state-integrity.sql'
      AND release_contract='winpress-v4.2.30-20260731'
      AND apply_mode='FORWARD'
      AND verification_reference='CONFERENCE_MEDIA_CANDIDATE_STATE_INTEGRITY_20260731'
  ),
  'passed_acceptance_gate', (
    SELECT count(*) FROM platform_acceptance_gate WHERE status='PASSED'
  ),
  'sys_role', (SELECT count(*) FROM sys_role),
  'sys_permission', (SELECT count(*) FROM sys_permission)
)::text;
'@
  foreach ($emptyTable in @(
    'app_user',
    'organization',
    'customer_requirement',
    'project',
    'publish_channel',
    'channel_quote',
    'supplier',
    'supplier_order',
    'business_inquiry'
  )) {
    if ([int]$counts.$emptyTable -ne 0) {
      throw "Production cold-start unexpectedly populated $emptyTable."
    }
  }
  if ([int]$counts.sys_role -ne 3 -or [int]$counts.sys_permission -ne 10) {
    throw 'Production role and permission bootstrap metadata is incomplete.'
  }
  if (
    [int]$counts.acceptance_evidence_item -ne 28 -or
    [int]$counts.schema_migration_ledger -ne 6 -or
    [int]$counts.schema_migration_baseline -ne 1 -or
    [int]$counts.schema_migration_media_result_integrity -ne 1 -or
    [int]$counts.schema_migration_writing_assignment_schedule_integrity -ne 1 -or
    [int]$counts.schema_migration_writing_assignment_radius_integrity -ne 1 -or
    [int]$counts.schema_migration_conference_work_item_integrity -ne 1 -or
    [int]$counts.schema_migration_conference_media_candidate_integrity -ne 1 -or
    [int]$counts.passed_acceptance_gate -ne 0
  ) {
    throw 'Production release-governance or schema-migration ledger was not initialized in the expected state.'
  }

  $price = Invoke-DatabaseJson `
    -Database 'winpress_cold_start' `
    -User 'winpress_cold_start' `
    -Sql @'
SELECT json_build_object(
  'count', count(*),
  'service_code', min(service_code),
  'billing_unit', min(billing_unit),
  'list_price', min(list_price),
  'currency', min(currency)
)::text
FROM service_price_book;
'@
  if (
    [int]$price.count -ne 1 -or
    [string]$price.service_code -ne 'ONSITE_WRITING' -or
    [string]$price.billing_unit -ne 'PERSON_DAY' -or
    [decimal]$price.list_price -ne [decimal]980 -or
    [string]$price.currency -ne 'CNY'
  ) {
    throw 'The production public price bootstrap does not match the accepted 980 CNY person-day rule.'
  }

  $integrationCheck = @'
test "$WINPRESS_FEDERATION_ENABLED" = "false" &&
test "$WINPRESS_FEDERATION_MIGRATE_ON_START" = "false" &&
test "$WINPRESS_API_DOCS_ENABLED" = "false" &&
test -z "$WINPRESS_FEDERATION_SHARED_SECRET" &&
test -z "$WINPRESS_NIUMEDIA_BASE_URL" &&
test -z "$WINPRESS_NIUMEDIA_TOKEN"
'@
  & docker exec winpress-production-backend sh -c $integrationCheck
  if ($LASTEXITCODE -ne 0) {
    throw 'An external integration was unexpectedly enabled in the isolated production stack.'
  }

  foreach ($forbiddenMarker in @('本机演示环境', '选择测试身份')) {
    & docker exec winpress-production-frontend `
      grep -R -F -q $forbiddenMarker /usr/share/nginx/html
    if ($LASTEXITCODE -eq 0) {
      throw "The production frontend still contains a local demonstration marker: $forbiddenMarker."
    }
    if ($LASTEXITCODE -notin @(0, 1)) {
      throw 'The production frontend bundle could not be inspected for demonstration markers.'
    }
  }

  Assert-LoopbackBinding `
    -ContainerName 'winpress-production-postgres' `
    -ContainerPort '5432/tcp' `
    -ExpectedHostPort $PostgresPort
  Assert-LoopbackBinding `
    -ContainerName 'winpress-production-redis' `
    -ContainerPort '6379/tcp' `
    -ExpectedHostPort $RedisPort
  Assert-LoopbackBinding `
    -ContainerName 'winpress-production-backend' `
    -ContainerPort '8092/tcp' `
    -ExpectedHostPort $BackendPort
  Assert-LoopbackBinding `
    -ContainerName 'winpress-production-frontend' `
    -ContainerPort '80/tcp' `
    -ExpectedHostPort $FrontendPort

  $postgresInspection = @((& docker inspect winpress-production-postgres) | ConvertFrom-Json)[0]
  $forbiddenMounts = @(
    $postgresInspection.Mounts |
      Where-Object {
        $_.Type -eq 'bind' -and
        $_.Source -match '(?i)(seed|demo|media_channels|media_quotes|03-media-import|04-normalize)'
      }
  )
  if ($forbiddenMounts.Count -gt 0) {
    throw 'The production database unexpectedly mounted a demo or unverified catalogue input.'
  }

  Write-Output (
    'Production cold-start verification passed: schema 41, contract v4.2.30, ' +
    'empty business data, private API documentation, explicit HTTPS CORS, protected integrations, ' +
    '980 CNY public writing price, and loopback-only ports.'
  )
  Write-Output "Cold-start frontend: $frontendUri"
  Write-Output "Cold-start backend health: $healthUri"
  $verificationPassed = $true
} finally {
  if (-not $KeepRunning -or -not $verificationPassed) {
    Remove-ColdStartStack `
      -EnvironmentFile $environmentFile `
      -OverrideFile $overrideFile `
      -StatePath $resolvedStateFile
  }
}

if ($KeepRunning -and $verificationPassed) {
  Write-Output 'The isolated stack is being kept temporarily for browser review.'
  Write-Output (
    "Cleanup command: powershell.exe -NoProfile -ExecutionPolicy Bypass " +
    "-File scripts\verify-production-cold-start.ps1 -CleanupOnly"
  )
}
