[CmdletBinding()]
param(
  [ValidateRange(30, 180)]
  [int]$RestoreStartupTimeoutSeconds = 90,
  [string]$RestoreImage = 'postgres:17-alpine'
)

<#
Runs a non-empty, production-equivalent PostgreSQL and upload-volume backup/restore exercise.

The script only accepts the isolated production cold-start project. It registers one synthetic
customer through the real API, creates four independently ordered service projects linked to one
activity, verifies their customer-visible task and order records, uploads one synthetic text file,
backs up the database and upload volume, restores both into unexposed temporary targets, and checks
database-to-file consistency. It removes all temporary containers, volumes, files, credentials,
tokens, and the cold-start stack. It never touches or accepts a real production stack.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$coldStartPath = Join-Path $PSScriptRoot 'verify-production-cold-start.ps1'
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
$runId = [Guid]::NewGuid().ToString('N')
$statePath = Join-Path $temporaryRoot "winpress-production-backup-restore-$runId.json"
$stagingDirectory = Join-Path $temporaryRoot "winpress-production-backup-restore-$runId"
$databaseDumpPath = Join-Path $stagingDirectory 'winpress-production.dump'
$uploadArchivePath = Join-Path $stagingDirectory 'uploads.tar.gz'
$sourceDumpPath = "/tmp/winpress-production-backup-$runId.dump"
$restoreDatabaseContainer = "winpress-production-db-restore-$runId"
$restoreUploadVolume = "winpress-production-upload-restore-$runId"
$helperContainer = "winpress-production-backup-helper-$runId"
$expectedColdStartProject = 'winpress-production-cold-start'

$cleanupColdStart = $false
$restoreDatabaseStarted = $false
$restoreUploadVolumeCreated = $false
$sourceDumpCreated = $false
$sourceDatabaseContainer = 'winpress-production-postgres'
$sourceBackendContainer = 'winpress-production-backend'
$token = $null
$registrationPassword = $null
$registrationBody = $null
$restorePassword = $null
$syntheticContent = $null

function Get-RandomHex {
  param([int]$ByteCount = 24)
  $bytes = New-Object byte[] $ByteCount
  $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $generator.GetBytes($bytes)
  } finally {
    $generator.Dispose()
  }
  return [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function Assert-TemporaryPath {
  param([string]$Path)
  $fullPath = [System.IO.Path]::GetFullPath($Path)
  $prefix = $temporaryRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
  if (-not $fullPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Production-equivalent restore artifacts must remain inside the operating-system temporary directory.'
  }
  return $fullPath
}

function Invoke-DockerSafe {
  param(
    [string[]]$Arguments,
    [string]$FailureMessage
  )
  $output = @(& docker @Arguments 2>&1)
  if ($LASTEXITCODE -ne 0) {
    throw $FailureMessage
  }
  return $output
}

function Get-ContainerInspection {
  param([string]$ContainerName)
  $raw = @(& docker inspect $ContainerName 2>&1)
  if ($LASTEXITCODE -ne 0 -or $raw.Count -eq 0) {
    throw 'An expected isolated production-equivalent container is unavailable.'
  }
  return @(($raw -join "`n") | ConvertFrom-Json)[0]
}

function Assert-ColdStartContainer {
  param(
    [string]$ContainerName,
    [string]$ExpectedService
  )
  $inspection = Get-ContainerInspection -ContainerName $ContainerName
  if (
    [string]$inspection.Config.Labels.'com.docker.compose.project' -ne $expectedColdStartProject -or
    [string]$inspection.Config.Labels.'com.docker.compose.service' -ne $ExpectedService -or
    [string]$inspection.Config.Labels.'com.docker.compose.project.config_files' -notmatch '(?i)docker-compose[.]production[.]yml' -or
    -not [bool]$inspection.State.Running -or
    [string]$inspection.State.Health.Status -ne 'healthy'
  ) {
    throw 'The backup source is not the expected healthy isolated production cold-start service.'
  }
  return $inspection
}

function Invoke-JsonRequest {
  param(
    [ValidateSet('GET', 'POST')]
    [string]$Method,
    [string]$Uri,
    [hashtable]$Headers,
    [object]$Body,
    [string]$FailureMessage
  )
  $client = [System.Net.Http.HttpClient]::new()
  $request = $null
  $response = $null
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $request = [System.Net.Http.HttpRequestMessage]::new(
      [System.Net.Http.HttpMethod]::new($Method),
      $Uri
    )
    $requestHeaders = @{}
    foreach ($header in $Headers.GetEnumerator()) {
      $requestHeaders[[string]$header.Key] = [string]$header.Value
    }
    if (
      $Method -eq 'POST' -and
      $Uri.TrimEnd('/') -match '/requirements$' -and
      -not $requestHeaders.ContainsKey('Idempotency-Key')
    ) {
      $requestHeaders['Idempotency-Key'] = [Guid]::NewGuid().ToString()
    }
    foreach ($header in $requestHeaders.GetEnumerator()) {
      [void]$request.Headers.TryAddWithoutValidation(
        [string]$header.Key,
        [string]$header.Value
      )
    }
    if ($null -ne $Body) {
      $json = $Body | ConvertTo-Json -Depth 10 -Compress
      $request.Content = [System.Net.Http.StringContent]::new(
        $json,
        [System.Text.Encoding]::UTF8,
        'application/json'
      )
    }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    if ([int]$response.StatusCode -ne 200) {
      throw $FailureMessage
    }
    $content = [System.Text.Encoding]::UTF8.GetString($bytes)
    if ([string]::IsNullOrWhiteSpace($content)) {
      throw $FailureMessage
    }
    return $content | ConvertFrom-Json
  } catch {
    throw $FailureMessage
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $request) { $request.Dispose() }
    $client.Dispose()
  }
}

function Test-ContainsForbiddenCustomerKey {
  param([object]$Value)
  $forbiddenPattern =
    '(?i)^(storageKey|operationNote|mediaId|reporterId|external.*|supplier.*|cost.*|' +
    'token|apiKey|secret|upstream.*|internalNote|ownerName|operatorName|' +
    'assignedOperator.*|createdBy|voidedBy|voidReason)$'
  if ($null -eq $Value) {
    return $false
  }
  if (
    $Value -is [string] -or
    $Value.GetType().IsPrimitive -or
    $Value -is [System.ValueType]
  ) {
    return $false
  }
  if ($Value -is [System.Collections.IDictionary]) {
    foreach ($key in $Value.Keys) {
      if ([string]$key -match $forbiddenPattern) {
        return $true
      }
      if (Test-ContainsForbiddenCustomerKey -Value $Value[$key]) {
        return $true
      }
    }
    return $false
  }
  if ($Value -is [System.Collections.IEnumerable]) {
    foreach ($item in $Value) {
      if (Test-ContainsForbiddenCustomerKey -Value $item) {
        return $true
      }
    }
    return $false
  }
  foreach ($property in $Value.PSObject.Properties) {
    if ($property.Name -match $forbiddenPattern) {
      return $true
    }
    if (Test-ContainsForbiddenCustomerKey -Value $property.Value) {
      return $true
    }
  }
  return $false
}

function Invoke-ProjectFileUpload {
  param(
    [string]$ApiBase,
    [hashtable]$Headers,
    [long]$ProjectId,
    [string]$Text
  )
  $client = [System.Net.Http.HttpClient]::new()
  $form = $null
  $response = $null
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    foreach ($header in $Headers.GetEnumerator()) {
      [void]$client.DefaultRequestHeaders.TryAddWithoutValidation(
        [string]$header.Key,
        [string]$header.Value
      )
    }
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = [System.Net.Http.ByteArrayContent]::new(
      [System.Text.Encoding]::UTF8.GetBytes($Text)
    )
    $fileContent.Headers.ContentType =
      [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/plain')
    $form.Add($fileContent, 'file', 'production-backup-restore-evidence.txt')
    $form.Add(
      [System.Net.Http.StringContent]::new(
        [string]$ProjectId,
        [System.Text.Encoding]::UTF8
      ),
      'projectId'
    )
    $response = $client.PostAsync("$ApiBase/files", $form).GetAwaiter().GetResult()
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    if ([int]$response.StatusCode -ne 200) {
      throw 'The production-equivalent file upload failed.'
    }
    $content = [System.Text.Encoding]::UTF8.GetString($bytes)
    return $content | ConvertFrom-Json
  } catch {
    throw 'The production-equivalent file upload failed.'
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $form) { $form.Dispose() }
    $client.Dispose()
  }
}

function Invoke-ProjectFileDownload {
  param(
    [string]$ApiBase,
    [hashtable]$Headers,
    [string]$FileNo
  )
  $client = [System.Net.Http.HttpClient]::new()
  $response = $null
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    foreach ($header in $Headers.GetEnumerator()) {
      [void]$client.DefaultRequestHeaders.TryAddWithoutValidation(
        [string]$header.Key,
        [string]$header.Value
      )
    }
    $response = $client.GetAsync("$ApiBase/files/$FileNo").GetAwaiter().GetResult()
    if ([int]$response.StatusCode -ne 200) {
      throw 'The uploaded production-equivalent file could not be downloaded.'
    }
    return $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
  } catch {
    throw 'The uploaded production-equivalent file could not be downloaded.'
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    $client.Dispose()
  }
}

function Invoke-DatabaseJson {
  param(
    [string]$Container,
    [string]$DatabaseUser,
    [string]$DatabaseName,
    [string]$Sql
  )
  $result = @(
    & docker exec $Container `
      psql -X -q -v ON_ERROR_STOP=1 `
      -U $DatabaseUser `
      -d $DatabaseName `
      -Atc $Sql 2>&1
  )
  if ($LASTEXITCODE -ne 0) {
    throw 'A production-equivalent database assertion could not be evaluated.'
  }
  try {
    return (($result | ForEach-Object { [string]$_ }) -join '') | ConvertFrom-Json
  } catch {
    throw 'A production-equivalent database assertion returned an invalid result.'
  }
}

function Get-DatabaseIdentity {
  param([object]$Inspection)
  $environment = @{}
  foreach ($entry in $Inspection.Config.Env) {
    $separator = $entry.IndexOf('=')
    if ($separator -gt 0) {
      $environment[$entry.Substring(0, $separator)] = $entry.Substring($separator + 1)
    }
  }
  $databaseUser = [string]$environment['POSTGRES_USER']
  $databaseName = [string]$environment['POSTGRES_DB']
  if (
    [string]::IsNullOrWhiteSpace($databaseUser) -or
    [string]::IsNullOrWhiteSpace($databaseName)
  ) {
    throw 'The isolated production-equivalent database identity is incomplete.'
  }
  return [pscustomobject]@{
    User = $databaseUser
    Database = $databaseName
  }
}

function Assert-EqualFile {
  param(
    [string]$SourcePath,
    [string]$RestorePath,
    [string]$EvidenceName
  )
  if (
    -not (Test-Path -LiteralPath $SourcePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $RestorePath -PathType Leaf)
  ) {
    throw "Upload restore evidence is missing: $EvidenceName."
  }
  if (
    (Get-FileHash -LiteralPath $SourcePath -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath $RestorePath -Algorithm SHA256).Hash
  ) {
    throw "The restored upload volume does not match the source: $EvidenceName."
  }
}

function Remove-IsolatedArtifacts {
  $cleanupProblems = New-Object System.Collections.Generic.List[string]

  if ($helperContainer -like 'winpress-production-backup-helper-*') {
    try {
      & docker rm -f $helperContainer 2>$null | Out-Null
    } catch {
      # A helper started with --rm can disappear between inspection and cleanup.
    }
  }

  if (
    $restoreDatabaseStarted -and
    $restoreDatabaseContainer -like 'winpress-production-db-restore-*'
  ) {
    try {
      & docker rm -f $restoreDatabaseContainer 2>$null | Out-Null
    } catch {
      $cleanupProblems.Add('restore database container')
    }
  }

  if (
    $restoreUploadVolumeCreated -and
    $restoreUploadVolume -like 'winpress-production-upload-restore-*'
  ) {
    try {
      & docker volume rm -f $restoreUploadVolume 2>$null | Out-Null
    } catch {
      $cleanupProblems.Add('restore upload volume')
    }
  }

  if (
    $sourceDumpCreated -and
    $sourceDumpPath -like '/tmp/winpress-production-backup-*.dump'
  ) {
    try {
      & docker exec $sourceDatabaseContainer rm -f $sourceDumpPath 2>$null | Out-Null
    } catch {
      $cleanupProblems.Add('source database dump')
    }
  }

  if (Test-Path -LiteralPath $stagingDirectory) {
    try {
      $safeStaging = Assert-TemporaryPath -Path $stagingDirectory
      Remove-Item -LiteralPath $safeStaging -Recurse -Force
    } catch {
      $cleanupProblems.Add('protected staging directory')
    }
  }

  if ($cleanupColdStart -or (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    try {
      & $coldStartPath -CleanupOnly -StateFile $statePath
    } catch {
      $cleanupProblems.Add('cold-start stack')
    }
  }

  $remainingContainers = @(& docker ps -a --format '{{.Names}}' 2>$null)
  if (
    $restoreDatabaseContainer -in $remainingContainers -or
    $helperContainer -in $remainingContainers -or
    @($remainingContainers | Where-Object { $_ -like 'winpress-production-*' }).Count -gt 0
  ) {
    $cleanupProblems.Add('one or more isolated containers')
  }
  $remainingVolumes = @(& docker volume ls --format '{{.Name}}' 2>$null)
  if (
    $restoreUploadVolume -in $remainingVolumes -or
    @(
      $remainingVolumes |
        Where-Object { $_ -like 'winpress-production-cold-start_*' }
    ).Count -gt 0
  ) {
    $cleanupProblems.Add('one or more isolated volumes')
  }
  if (
    (Test-Path -LiteralPath $statePath) -or
    (Test-Path -LiteralPath $stagingDirectory)
  ) {
    $cleanupProblems.Add('one or more protected temporary paths')
  }
  if ($cleanupProblems.Count -gt 0) {
    throw (
      'Production-equivalent backup/restore cleanup did not finish completely: ' +
      (($cleanupProblems | Select-Object -Unique) -join ', ') +
      '.'
    )
  }
}

$aggregateSql = @'
SELECT json_build_object(
  'publicTables', (
    SELECT count(*) FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  ),
  'organizations', (SELECT count(*) FROM organization),
  'users', (SELECT count(*) FROM app_user),
  'customerRoles', (
    SELECT count(*)
    FROM user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE ur.status = 'ACTIVE' AND r.role_code = 'CUSTOMER'
  ),
  'platformAdminRoles', (
    SELECT count(*)
    FROM user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE r.role_code = 'PLATFORM_ADMIN'
  ),
  'requirements', (SELECT count(*) FROM customer_requirement),
  'projects', (SELECT count(*) FROM project),
  'onsiteWritingRequirements', (
    SELECT count(*) FROM customer_requirement
    WHERE requested_service = 'ONSITE_WRITING'
  ),
  'mediaPrRequirements', (
    SELECT count(*) FROM customer_requirement
    WHERE requested_service = 'MEDIA_PR'
  ),
  'directPublishingRequirements', (
    SELECT count(*) FROM customer_requirement
    WHERE requested_service = 'DIRECT_PUBLISHING'
  ),
  'newsConferenceRequirements', (
    SELECT count(*) FROM customer_requirement
    WHERE requested_service = 'NEWS_CONFERENCE'
  ),
  'conferenceProjects', (SELECT count(*) FROM conference_project),
  'conferenceWorkItems', (SELECT count(*) FROM conference_work_item),
  'writingAssignments', (SELECT count(*) FROM writing_assignment),
  'serviceIntakeTasks', (SELECT count(*) FROM service_intake_task),
  'publishPlans', (SELECT count(*) FROM publish_plan),
  'publishTasks', (SELECT count(*) FROM publish_task),
  'fileAssets', (SELECT count(*) FROM file_asset),
  'activeLinkedFiles', (
    SELECT count(*) FROM file_asset
    WHERE project_id IS NOT NULL AND status = 'ACTIVE'
  ),
  'roles', (SELECT count(*) FROM sys_role WHERE status = 'ACTIVE'),
  'permissions', (SELECT count(*) FROM sys_permission WHERE status = 'ACTIVE'),
  'publicWritingPrices', (
    SELECT count(*) FROM service_price_book
    WHERE service_code = 'ONSITE_WRITING'
      AND billing_unit = 'PERSON_DAY'
      AND list_price = 980
      AND currency = 'CNY'
      AND status = 'ACTIVE'
  ),
  'channels', (SELECT count(*) FROM publish_channel),
  'quotes', (SELECT count(*) FROM channel_quote),
  'suppliers', (SELECT count(*) FROM supplier),
  'supplierOrders', (SELECT count(*) FROM supplier_order),
  'acceptanceEvidenceItems', (
    SELECT count(*) FROM platform_acceptance_evidence_item WHERE required
  ),
  'quoteAdjustmentBatches', (SELECT count(*) FROM quote_adjustment_batch),
  'quoteAdjustments', (SELECT count(*) FROM quote_adjustment),
  'serviceIntakeTitleIntegrity', EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'ck_service_intake_task_title_not_placeholder'
      AND conrelid = 'public.service_intake_task'::regclass
  ),
  'settlementTransactionEvidence', EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'ck_settlement_transaction_evidence'
      AND conrelid = 'public.settlement_transaction'::regclass
  ),
  'requirementIdempotency', (
    to_regclass('public.uq_customer_requirement_submission_key') IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_customer_requirement_submission_pair'
        AND conrelid = 'public.customer_requirement'::regclass
    )
  ),
  'taskAcceptanceIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_publish_task_status'
        AND conrelid = 'public.publish_task'::regclass
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_media_pr_invitation_status'
        AND conrelid = 'public.media_pr_invitation'::regclass
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_result_link_status'
        AND conrelid = 'public.result_link'::regclass
    )
    AND to_regclass('public.uq_result_link_task_url') IS NOT NULL
  ),
  'mediaInvitationProgressIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_publish_task_not_proceeding_channel'
        AND conrelid = 'public.publish_task'::regclass
        AND convalidated
    )
    AND NOT EXISTS (
      SELECT 1 FROM publish_task
      WHERE status = 'NOT_PROCEEDING' AND channel_type <> 'MEDIA_PR'
    )
    AND NOT EXISTS (
      SELECT 1
      FROM media_pr_invitation invitation
      JOIN publish_task task ON task.id = invitation.publish_task_id
      WHERE invitation.status IN ('DECLINED','NOT_PROCEEDING')
        AND task.status NOT IN ('NOT_PROCEEDING','COMPLETED','CLIENT_ACCEPTED')
    )
    AND NOT EXISTS (
      SELECT 1
      FROM service_intake_task intake
      WHERE intake.status NOT IN ('COMPLETED','CANCELLED')
        AND EXISTS (
          SELECT 1 FROM publish_plan plan
          WHERE plan.project_id = intake.project_id
            AND plan.status IN ('CONFIRMED','EXECUTING','COMPLETED')
      )
    )
  ),
  'publishTaskTerminalIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_publish_task_terminal_integrity'
        AND tgrelid = 'public.publish_task'::regclass
        AND NOT tgisinternal
    )
    AND NOT EXISTS (
      SELECT 1
      FROM publish_task task
      WHERE task.status = 'CLIENT_ACCEPTED'
        AND NOT EXISTS (
          SELECT 1 FROM result_link result
          WHERE result.publish_task_id = task.id
            AND result.status = 'VERIFIED'
        )
    )
  ),
  'publishPlanIdempotency', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_publish_plan_submission_pair'
        AND conrelid = 'public.publish_plan'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_index
      WHERE indexrelid = to_regclass('public.uq_publish_plan_submission_key')
        AND indisunique
        AND indisvalid
        AND indisready
    )
    AND NOT EXISTS (
      SELECT 1 FROM publish_plan
      WHERE (submission_key IS NULL) <> (submission_hash IS NULL)
    )
  ),
  'settlementTransactionIdempotency', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_settlement_transaction_submission_pair'
        AND conrelid = 'public.settlement_transaction'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_index
      WHERE indexrelid = to_regclass('public.uq_settlement_transaction_submission_key')
        AND indisunique
        AND indisvalid
        AND indisready
    )
    AND NOT EXISTS (
      SELECT 1 FROM settlement_transaction
      WHERE (submission_key IS NULL) <> (submission_hash IS NULL)
    )
  ),
  'batchQuoteAdjustmentIdempotency', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'uq_quote_adjustment_batch_submission'
        AND conrelid = 'public.quote_adjustment_batch'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_quote_adjustment_batch_hash'
        AND conrelid = 'public.quote_adjustment_batch'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'fk_quote_adjustment_batch'
        AND conrelid = 'public.quote_adjustment'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_index
      WHERE indexrelid = to_regclass('public.idx_quote_adjustment_batch')
        AND indisvalid
        AND indisready
    )
    AND NOT EXISTS (
      SELECT 1 FROM quote_adjustment_batch
      WHERE adjusted_count > channel_count
        OR status NOT IN ('PROCESSING','COMPLETED')
        OR submission_hash !~ '^[0-9a-f]{64}$'
    )
  ),
  'publishPlanServiceIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_publish_plan_item_service_integrity'
        AND tgrelid = 'public.publish_plan_item'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_publish_plan_project_service_integrity'
        AND tgrelid = 'public.publish_plan'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_project_plan_service_integrity'
        AND tgrelid = 'public.project'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_requirement_plan_service_integrity'
        AND tgrelid = 'public.customer_requirement'::regclass
        AND NOT tgisinternal
    )
  ),
  'releaseGovernanceIntegrity', (
    (SELECT count(*) FROM platform_acceptance_evidence_item WHERE required) = 28
    AND (SELECT count(*) FROM platform_acceptance_gate) = 5
    AND NOT EXISTS (
      SELECT 1 FROM platform_acceptance_gate WHERE status='PASSED'
    )
    AND NOT EXISTS (
      SELECT 1 FROM customer_requirement
      WHERE requested_service='WRITING_AND_PUBLISHING'
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_platform_acceptance_gate_readiness'
        AND tgrelid='public.platform_acceptance_gate'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_supplier_order_fulfillment_evidence'
        AND tgrelid='public.supplier_order'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_legacy_combination_service_boundary'
        AND tgrelid='public.customer_requirement'::regclass
        AND NOT tgisinternal
    )
  ),
  'channelQuoteIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_channel_quote_price_integrity'
        AND conrelid = 'public.channel_quote'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_channel_quote_validity'
        AND conrelid = 'public.channel_quote'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_channel_quote_status'
        AND conrelid = 'public.channel_quote'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_index
      WHERE indexrelid = to_regclass('public.uq_channel_quote_one_active_per_channel')
        AND indisunique AND indisvalid AND indisready
    )
    AND NOT EXISTS (
      SELECT 1 FROM channel_quote
      WHERE customer_price <= 0
        OR cost_price < 0
        OR (cost_price IS NOT NULL AND customer_price < cost_price)
        OR valid_until <= valid_from
    )
    AND NOT EXISTS (
      SELECT 1 FROM channel_quote
      WHERE status='ACTIVE'
      GROUP BY channel_id
      HAVING count(*) > 1
    )
  )
)::text;
'@

if (-not (Test-Path -LiteralPath $coldStartPath -PathType Leaf)) {
  throw 'The production cold-start verifier is missing.'
}
Assert-TemporaryPath -Path $statePath | Out-Null
Assert-TemporaryPath -Path $stagingDirectory | Out-Null
if (
  (Test-Path -LiteralPath $statePath) -or
  (Test-Path -LiteralPath $stagingDirectory)
) {
  throw 'An isolated production backup/restore temporary path is unexpectedly occupied.'
}

try {
  & $coldStartPath -KeepRunning -StateFile $statePath
  $cleanupColdStart = $true

  $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
  if (
    [string]$state.projectName -ne $expectedColdStartProject -or
    [int]$state.backendPort -le 0
  ) {
    throw 'The cold-start state is not valid for the production-equivalent restore exercise.'
  }

  $databaseInspection = Assert-ColdStartContainer `
    -ContainerName $sourceDatabaseContainer `
    -ExpectedService 'postgres'
  $backendInspection = Assert-ColdStartContainer `
    -ContainerName $sourceBackendContainer `
    -ExpectedService 'backend'
  $databaseIdentity = Get-DatabaseIdentity -Inspection $databaseInspection

  $uploadMounts = @(
    $backendInspection.Mounts |
      Where-Object {
        $_.Type -eq 'volume' -and
        $_.Destination -eq '/app/storage/uploads'
      }
  )
  if (
    $uploadMounts.Count -ne 1 -or
    [string]::IsNullOrWhiteSpace([string]$uploadMounts[0].Name)
  ) {
    throw 'The isolated production-equivalent upload volume cannot be identified.'
  }
  $sourceUploadVolume = [string]$uploadMounts[0].Name
  $volumeRaw = @(& docker volume inspect $sourceUploadVolume 2>&1)
  if ($LASTEXITCODE -ne 0 -or $volumeRaw.Count -eq 0) {
    throw 'The isolated production-equivalent upload volume is unavailable.'
  }
  $volumeInspection = @(($volumeRaw -join "`n") | ConvertFrom-Json)[0]
  if (
    [string]$volumeInspection.Labels.'com.docker.compose.project' -ne $expectedColdStartProject -or
    [string]$volumeInspection.Labels.'com.docker.compose.volume' -ne 'winpress_production_uploads'
  ) {
    throw 'The upload source does not belong to the isolated production cold-start project.'
  }

  $apiBase = "http://127.0.0.1:$([int]$state.backendPort)/api/v1"
  $registrationPassword = 'Qa!8' + (Get-RandomHex -ByteCount 20)
  $syntheticEmail = "restore-$($runId.Substring(0, 12))@qa.winpress.invalid"
  $registrationBody = @{
    username = $syntheticEmail
    organizationName = 'WinPress Restore QA'
    displayName = 'Restore QA Customer'
    mobile = '13700000000'
    email = $syntheticEmail
    password = $registrationPassword
  }
  $registration = Invoke-JsonRequest `
    -Method POST `
    -Uri "$apiBase/auth/register" `
    -Headers @{} `
    -Body $registrationBody `
    -FailureMessage 'The synthetic production-equivalent customer could not be registered.'
  if (
    -not [bool]$registration.success -or
    [string]$registration.data.user.role -ne 'CUSTOMER' -or
    [string]::IsNullOrWhiteSpace([string]$registration.data.token)
  ) {
    throw 'The synthetic production-equivalent customer registration is invalid.'
  }
  $token = [string]$registration.data.token
  $headers = @{ Authorization = "Bearer $token" }

  $conferenceRequirement = Invoke-JsonRequest `
    -Method POST `
    -Uri "$apiBase/requirements" `
    -Headers $headers `
    -Body @{
      title = 'Production closure QA conference'
      requestedService = 'NEWS_CONFERENCE'
      conferenceContactName = 'Closure QA Contact'
      conferenceContactMobile = '13700000000'
    } `
    -FailureMessage 'The production-equivalent conference project could not be created.'
  $conferenceProjectId = [long]$conferenceRequirement.data.projectId
  $serviceStart = [DateTimeOffset]::UtcNow.AddDays(7).ToString('o')
  $serviceDue = [DateTimeOffset]::UtcNow.AddDays(10).ToString('o')

  $writingRequirement = Invoke-JsonRequest `
    -Method POST `
    -Uri "$apiBase/requirements" `
    -Headers $headers `
    -Body @{
      title = 'Production closure QA onsite writing'
      requestedService = 'ONSITE_WRITING'
      relatedProjectId = $conferenceProjectId
      facts = 'Synthetic confirmed facts for isolated production-equivalent validation.'
      eventTime = $serviceStart
      eventLocation = 'Shenzhen'
      serviceDays = 1
      writerCount = 1
      onsiteContactName = 'Closure QA Contact'
      onsiteContactMobile = '13700000000'
      dueAt = $serviceDue
    } `
    -FailureMessage 'The production-equivalent onsite-writing project could not be created.'
  $writingProjectId = [long]$writingRequirement.data.projectId

  $mediaRequirement = Invoke-JsonRequest `
    -Method POST `
    -Uri "$apiBase/requirements" `
    -Headers $headers `
    -Body @{
      title = 'Production closure QA media invitation'
      requestedService = 'MEDIA_PR'
      relatedProjectId = $conferenceProjectId
      facts = 'Synthetic confirmed facts for isolated production-equivalent validation.'
      dueAt = $serviceDue
    } `
    -FailureMessage 'The production-equivalent media-invitation project could not be created.'
  $mediaProjectId = [long]$mediaRequirement.data.projectId

  $directRequirement = Invoke-JsonRequest `
    -Method POST `
    -Uri "$apiBase/requirements" `
    -Headers $headers `
    -Body @{
      title = 'Production closure QA direct publishing'
      requestedService = 'DIRECT_PUBLISHING'
      relatedProjectId = $conferenceProjectId
      facts = 'Synthetic confirmed facts for isolated production-equivalent validation.'
      dueAt = $serviceDue
    } `
    -FailureMessage 'The production-equivalent direct-publishing project could not be created.'
  $directProjectId = [long]$directRequirement.data.projectId

  $projectIds = @(
    $conferenceProjectId,
    $writingProjectId,
    $mediaProjectId,
    $directProjectId
  )
  if (
    @($projectIds | Where-Object { [long]$_ -le 0 }).Count -gt 0 -or
    @($projectIds | Select-Object -Unique).Count -ne 4
  ) {
    throw 'The four production-equivalent service projects are not independent.'
  }

  $expectedServiceMappings = @(
    [pscustomobject]@{
      ProjectId = $conferenceProjectId
      ServiceType = 'NEWS_CONFERENCE'
      ItemLabel = 'News conference'
    },
    [pscustomobject]@{
      ProjectId = $writingProjectId
      ServiceType = 'ONSITE_WRITING'
      ItemLabel = 'Onsite writing'
    },
    [pscustomobject]@{
      ProjectId = $mediaProjectId
      ServiceType = 'MEDIA_PR'
      ItemLabel = 'Media invitation'
    },
    [pscustomobject]@{
      ProjectId = $directProjectId
      ServiceType = 'DIRECT_PUBLISHING'
      ItemLabel = 'Direct publishing'
    }
  )

  $projectDetails = @{}
  foreach ($expected in $expectedServiceMappings) {
    $detail = Invoke-JsonRequest `
      -Method GET `
      -Uri "$apiBase/projects/$($expected.ProjectId)" `
      -Headers $headers `
      -Body $null `
      -FailureMessage 'A production-equivalent customer project is not readable.'
    if (
      [string]$detail.data.project.requestedService -ne $expected.ServiceType -or
      [string]::IsNullOrWhiteSpace([string]$detail.data.project.requirementNo) -or
      (Test-ContainsForbiddenCustomerKey -Value $detail.data)
    ) {
      throw 'A production-equivalent customer project violates its service or field boundary.'
    }
    $projectDetails[[string]$expected.ProjectId] = $detail
  }

  $activityServices = @(
    $projectDetails[[string]$conferenceProjectId].data.activityProjects |
      ForEach-Object { [string]$_.requestedService }
  )
  if (
    @(
      @('NEWS_CONFERENCE', 'ONSITE_WRITING', 'MEDIA_PR', 'DIRECT_PUBLISHING') |
        Where-Object { $activityServices -notcontains $_ }
    ).Count -ne 0
  ) {
    throw 'The production-equivalent activity does not retain all four independent services.'
  }

  $taskLedger = Invoke-JsonRequest `
    -Method GET `
    -Uri "$apiBase/task-records?page=1&pageSize=100" `
    -Headers $headers `
    -Body $null `
    -FailureMessage 'The production-equivalent task ledger is unavailable.'
  $taskRows = @($taskLedger.data.items)
  if (Test-ContainsForbiddenCustomerKey -Value $taskRows) {
    throw 'The production-equivalent customer task ledger exposes an internal field.'
  }
  foreach ($expected in $expectedServiceMappings) {
    $taskMatches = @(
      $taskRows |
        Where-Object {
          [long]$_.projectId -eq [long]$expected.ProjectId -and
          [string]$_.serviceType -eq $expected.ServiceType
        }
    )
    if ($taskMatches.Count -lt 1) {
      throw "The production-equivalent task ledger is missing $($expected.ItemLabel)."
    }
  }

  $orderLedger = Invoke-JsonRequest `
    -Method GET `
    -Uri "$apiBase/order-records?page=1&pageSize=100" `
    -Headers $headers `
    -Body $null `
    -FailureMessage 'The production-equivalent order ledger is unavailable.'
  $orderRows = @(
    $orderLedger.data.items |
      Where-Object { $projectIds -contains [long]$_.projectId }
  )
  if (
    $orderRows.Count -ne 4 -or
    @($orderRows | Select-Object -ExpandProperty recordNo -Unique).Count -ne 4 -or
    (Test-ContainsForbiddenCustomerKey -Value $orderRows)
  ) {
    throw 'The production-equivalent customer order ledger is not four independent safe records.'
  }
  foreach ($expected in $expectedServiceMappings) {
    $orderMatches = @(
      $orderRows |
        Where-Object {
          [long]$_.projectId -eq [long]$expected.ProjectId -and
          [string]$_.serviceType -eq $expected.ServiceType
        }
    )
    $expectedRequirementNo =
      [string]$projectDetails[[string]$expected.ProjectId].data.project.requirementNo
    if (
      $orderMatches.Count -ne 1 -or
      [string]$orderMatches[0].recordNo -ne $expectedRequirementNo
    ) {
      throw "The production-equivalent order ledger is invalid for $($expected.ItemLabel)."
    }
  }
  $writingOrder = @(
    $orderRows |
      Where-Object {
        [long]$_.projectId -eq $writingProjectId -and
        [string]$_.serviceType -eq 'ONSITE_WRITING'
      }
  )
  $unquotedOrders = @(
    $orderRows |
      Where-Object {
        [string]$_.serviceType -in @(
          'NEWS_CONFERENCE',
          'MEDIA_PR',
          'DIRECT_PUBLISHING'
        )
      }
  )
  if (
    $writingOrder.Count -ne 1 -or
    [decimal]$writingOrder[0].amount -ne [decimal]980 -or
    @($unquotedOrders | Where-Object { $null -ne $_.amount }).Count -ne 0
  ) {
    throw 'The production-equivalent customer order prices violate the accepted boundary.'
  }

  $channelDirectory = Invoke-JsonRequest `
    -Method GET `
    -Uri "$apiBase/channels?type=DIRECT_PUBLISHING&page=1&pageSize=20" `
    -Headers $headers `
    -Body $null `
    -FailureMessage 'The production-equivalent channel boundary could not be checked.'
  $mediaStatus = Invoke-JsonRequest `
    -Method GET `
    -Uri "$apiBase/media-discovery/status" `
    -Headers $headers `
    -Body $null `
    -FailureMessage 'The production-equivalent media boundary could not be checked.'
  if (
    [long]$channelDirectory.data.total -ne 0 -or
    [bool]$mediaStatus.data.available -or
    (Test-ContainsForbiddenCustomerKey -Value $mediaStatus.data)
  ) {
    throw 'The production-equivalent customer surface claims unverified external capability.'
  }

  $syntheticContent =
    "WinPress production-equivalent backup and restore evidence $runId."
  $upload = Invoke-ProjectFileUpload `
    -ApiBase $apiBase `
    -Headers $headers `
    -ProjectId $conferenceProjectId `
    -Text $syntheticContent
  $fileNo = [string]$upload.data.fileNo
  if ($fileNo -notmatch '^FIL-\d{8}-[A-F0-9]{10}$') {
    throw 'The production-equivalent file upload did not return a public file number.'
  }
  $downloaded = Invoke-ProjectFileDownload `
    -ApiBase $apiBase `
    -Headers $headers `
    -FileNo $fileNo
  $expectedBytes = [System.Text.Encoding]::UTF8.GetBytes($syntheticContent)
  if (
    $downloaded.Length -ne $expectedBytes.Length -or
    [Convert]::ToBase64String($downloaded) -ne [Convert]::ToBase64String($expectedBytes)
  ) {
    throw 'The uploaded production-equivalent file does not round-trip through the protected API.'
  }

  $logout = Invoke-JsonRequest `
    -Method POST `
    -Uri "$apiBase/auth/logout" `
    -Headers $headers `
    -Body $null `
    -FailureMessage 'The production-equivalent restore session could not be revoked.'
  if (-not [bool]$logout.success) {
    throw 'The production-equivalent restore session logout did not succeed.'
  }
  $token = $null
  $headers = @{}

  $sourceAggregate = Invoke-DatabaseJson `
    -Container $sourceDatabaseContainer `
    -DatabaseUser $databaseIdentity.User `
    -DatabaseName $databaseIdentity.Database `
    -Sql $aggregateSql
  if (
    [int]$sourceAggregate.publicTables -lt 41 -or
    [int]$sourceAggregate.organizations -ne 1 -or
    [int]$sourceAggregate.users -ne 1 -or
    [int]$sourceAggregate.customerRoles -ne 1 -or
    [int]$sourceAggregate.platformAdminRoles -ne 0 -or
    [int]$sourceAggregate.requirements -ne 4 -or
    [int]$sourceAggregate.projects -ne 4 -or
    [int]$sourceAggregate.onsiteWritingRequirements -ne 1 -or
    [int]$sourceAggregate.mediaPrRequirements -ne 1 -or
    [int]$sourceAggregate.directPublishingRequirements -ne 1 -or
    [int]$sourceAggregate.newsConferenceRequirements -ne 1 -or
    [int]$sourceAggregate.conferenceProjects -ne 1 -or
    [int]$sourceAggregate.conferenceWorkItems -ne 9 -or
    [int]$sourceAggregate.writingAssignments -ne 1 -or
    [int]$sourceAggregate.serviceIntakeTasks -ne 2 -or
    [int]$sourceAggregate.publishPlans -ne 0 -or
    [int]$sourceAggregate.publishTasks -ne 0 -or
    [int]$sourceAggregate.fileAssets -ne 1 -or
    [int]$sourceAggregate.activeLinkedFiles -ne 1 -or
    [int]$sourceAggregate.roles -ne 3 -or
    [int]$sourceAggregate.permissions -ne 10 -or
    [int]$sourceAggregate.publicWritingPrices -ne 1 -or
    [int]$sourceAggregate.channels -ne 0 -or
    [int]$sourceAggregate.quotes -ne 0 -or
    [int]$sourceAggregate.suppliers -ne 0 -or
    [int]$sourceAggregate.supplierOrders -ne 0 -or
    [int]$sourceAggregate.acceptanceEvidenceItems -ne 28 -or
    [int]$sourceAggregate.quoteAdjustmentBatches -ne 0 -or
    [int]$sourceAggregate.quoteAdjustments -ne 0 -or
    $sourceAggregate.serviceIntakeTitleIntegrity -ne $true -or
    $sourceAggregate.settlementTransactionEvidence -ne $true -or
    $sourceAggregate.requirementIdempotency -ne $true -or
    $sourceAggregate.taskAcceptanceIntegrity -ne $true -or
    $sourceAggregate.mediaInvitationProgressIntegrity -ne $true -or
    $sourceAggregate.publishTaskTerminalIntegrity -ne $true -or
    $sourceAggregate.publishPlanIdempotency -ne $true -or
    $sourceAggregate.settlementTransactionIdempotency -ne $true -or
    $sourceAggregate.batchQuoteAdjustmentIdempotency -ne $true -or
    $sourceAggregate.publishPlanServiceIntegrity -ne $true -or
    $sourceAggregate.releaseGovernanceIntegrity -ne $true
  ) {
    throw 'The non-empty production-equivalent source snapshot violates the accepted data boundary.'
  }

  $fileMetadata = Invoke-DatabaseJson `
    -Container $sourceDatabaseContainer `
    -DatabaseUser $databaseIdentity.User `
    -DatabaseName $databaseIdentity.Database `
    -Sql @"
SELECT row_to_json(record)::text
FROM (
  SELECT file_no AS "fileNo",
         storage_key AS "storageKey",
         checksum_sha256 AS "checksum",
         file_size AS "fileSize",
         project_id AS "projectId"
  FROM file_asset
  WHERE file_no = '$fileNo' AND status = 'ACTIVE'
) record;
"@
  if (
    [string]$fileMetadata.fileNo -ne $fileNo -or
    [long]$fileMetadata.projectId -ne $conferenceProjectId -or
    [string]$fileMetadata.storageKey -notmatch '^\d{4}-\d{2}-\d{2}/[0-9a-f]{32}[.]txt$' -or
    [string]$fileMetadata.checksum -notmatch '^[0-9a-f]{64}$' -or
    [long]$fileMetadata.fileSize -ne $expectedBytes.Length
  ) {
    throw 'The production-equivalent file metadata is incomplete or unsafe.'
  }

  New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

  $dumpCommand =
    'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" ' +
    '--format=custom --no-owner --no-privileges --serializable-deferrable ' +
    '--file="' + $sourceDumpPath + '"'
  $sourceDumpCreated = $true
  Invoke-DockerSafe `
    -Arguments @('exec', $sourceDatabaseContainer, 'sh', '-ec', $dumpCommand) `
    -FailureMessage 'The production-equivalent PostgreSQL backup could not be created.' |
    Out-Null
  Invoke-DockerSafe `
    -Arguments @('cp', "${sourceDatabaseContainer}:$sourceDumpPath", $databaseDumpPath) `
    -FailureMessage 'The production-equivalent PostgreSQL backup could not be copied to protected staging.' |
    Out-Null
  $databaseDump = Get-Item -LiteralPath $databaseDumpPath
  if ($databaseDump.Length -lt 1024) {
    throw 'The production-equivalent PostgreSQL backup is unexpectedly small.'
  }
  Invoke-DockerSafe `
    -Arguments @(
      'run', '--rm',
      '--network', 'none',
      '--entrypoint', 'pg_restore',
      '--mount', "type=bind,source=$stagingDirectory,target=/backup,readonly",
      $RestoreImage,
      '--list',
      '/backup/winpress-production.dump'
    ) `
    -FailureMessage 'The production-equivalent PostgreSQL archive catalogue is unreadable.' |
    Out-Null

  $uploadBackupCommand = @'
set -eu
cd /source
tar -czf /backup/uploads.tar.gz .
find . -type d -print | LC_ALL=C sort > /backup/source-directories.txt
find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do
  sha256sum "$file"
done > /backup/source-manifest.txt
find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do
  stat -c '%u|%g|%a|%s|%n' "$file"
done > /backup/source-modes.txt
find . -type f -exec stat -c '%s' '{}' ';' |
  awk '{ total += $1 } END { print total + 0 }' > /backup/source-bytes.txt
'@
  Invoke-DockerSafe `
    -Arguments @(
      'run', '--rm',
      '--network', 'none',
      '--name', $helperContainer,
      '--entrypoint', 'sh',
      '--mount', "type=volume,source=$sourceUploadVolume,target=/source,readonly",
      '--mount', "type=bind,source=$stagingDirectory,target=/backup",
      $RestoreImage,
      '-ec', $uploadBackupCommand
    ) `
    -FailureMessage 'The production-equivalent upload backup could not be created.' |
    Out-Null
  $uploadArchive = Get-Item -LiteralPath $uploadArchivePath
  if ($uploadArchive.Length -lt 64) {
    throw 'The production-equivalent upload backup is unexpectedly small.'
  }

  $restorePassword = Get-RandomHex -ByteCount 32
  Invoke-DockerSafe `
    -Arguments @(
      'run', '--detach', '--rm',
      '--network', 'none',
      '--name', $restoreDatabaseContainer,
      '--label', 'winpress.qa.purpose=production-database-backup-restore',
      '--env', 'POSTGRES_DB=winpress_production_restore_qa',
      '--env', 'POSTGRES_USER=winpress_production_restore',
      '--env', "POSTGRES_PASSWORD=$restorePassword",
      '--tmpfs', '/var/lib/postgresql/data:rw,noexec,nosuid,size=1g',
      $RestoreImage
    ) `
    -FailureMessage 'The isolated production-equivalent restore database could not be started.' |
    Out-Null
  $restoreDatabaseStarted = $true

  $deadline = (Get-Date).AddSeconds($RestoreStartupTimeoutSeconds)
  $restoreReady = $false
  while ((Get-Date) -lt $deadline) {
    & docker exec $restoreDatabaseContainer `
      pg_isready `
      -U winpress_production_restore `
      -d winpress_production_restore_qa *> $null
    if ($LASTEXITCODE -eq 0) {
      $restoreReady = $true
      break
    }
    Start-Sleep -Seconds 1
  }
  if (-not $restoreReady) {
    throw 'The isolated production-equivalent restore database did not become ready.'
  }

  Invoke-DockerSafe `
    -Arguments @('cp', $databaseDumpPath, "${restoreDatabaseContainer}:/tmp/winpress-production.dump") `
    -FailureMessage 'The PostgreSQL archive could not be placed in the isolated restore database.' |
    Out-Null
  Invoke-DockerSafe `
    -Arguments @(
      'exec', $restoreDatabaseContainer,
      'pg_restore',
      '-U', 'winpress_production_restore',
      '-d', 'winpress_production_restore_qa',
      '--no-owner',
      '--no-privileges',
      '--exit-on-error',
      '/tmp/winpress-production.dump'
    ) `
    -FailureMessage 'The production-equivalent PostgreSQL archive could not be restored.' |
    Out-Null

  $restoreAggregate = Invoke-DatabaseJson `
    -Container $restoreDatabaseContainer `
    -DatabaseUser 'winpress_production_restore' `
    -DatabaseName 'winpress_production_restore_qa' `
    -Sql $aggregateSql
  foreach ($field in @(
    'publicTables',
    'organizations',
    'users',
    'customerRoles',
    'platformAdminRoles',
    'requirements',
    'projects',
    'onsiteWritingRequirements',
    'mediaPrRequirements',
    'directPublishingRequirements',
    'newsConferenceRequirements',
    'conferenceProjects',
    'conferenceWorkItems',
    'writingAssignments',
    'serviceIntakeTasks',
    'publishPlans',
    'publishTasks',
    'fileAssets',
    'activeLinkedFiles',
    'roles',
    'permissions',
    'publicWritingPrices',
    'channels',
    'quotes',
    'suppliers',
    'supplierOrders',
    'acceptanceEvidenceItems',
    'quoteAdjustmentBatches',
    'quoteAdjustments',
    'serviceIntakeTitleIntegrity',
    'settlementTransactionEvidence',
    'requirementIdempotency',
    'taskAcceptanceIntegrity',
    'mediaInvitationProgressIntegrity',
    'publishTaskTerminalIntegrity',
    'publishPlanIdempotency',
    'settlementTransactionIdempotency',
    'batchQuoteAdjustmentIdempotency',
    'publishPlanServiceIntegrity',
    'releaseGovernanceIntegrity'
  )) {
    if ([string]$sourceAggregate.$field -ne [string]$restoreAggregate.$field) {
      throw "The restored database aggregate does not match the source: $field."
    }
  }

  $restoredFileMetadata = Invoke-DatabaseJson `
    -Container $restoreDatabaseContainer `
    -DatabaseUser 'winpress_production_restore' `
    -DatabaseName 'winpress_production_restore_qa' `
    -Sql @"
SELECT row_to_json(record)::text
FROM (
  SELECT file_no AS "fileNo",
         storage_key AS "storageKey",
         checksum_sha256 AS "checksum",
         file_size AS "fileSize",
         project_id AS "projectId"
  FROM file_asset
  WHERE file_no = '$fileNo' AND status = 'ACTIVE'
) record;
"@
  foreach ($field in @('fileNo', 'storageKey', 'checksum', 'fileSize', 'projectId')) {
    if ([string]$fileMetadata.$field -ne [string]$restoredFileMetadata.$field) {
      throw "The restored file metadata does not match the source: $field."
    }
  }

  Invoke-DockerSafe `
    -Arguments @(
      'volume', 'create',
      '--label', 'winpress.qa.purpose=production-upload-backup-restore',
      $restoreUploadVolume
    ) `
    -FailureMessage 'The isolated upload restore volume could not be created.' |
    Out-Null
  $restoreUploadVolumeCreated = $true

  $uploadRestoreCommand = @'
set -eu
tar -xzf /backup/uploads.tar.gz -C /restore
cd /restore
find . -type d -print | LC_ALL=C sort > /backup/restore-directories.txt
find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do
  sha256sum "$file"
done > /backup/restore-manifest.txt
find . -type f -print | LC_ALL=C sort | while IFS= read -r file; do
  stat -c '%u|%g|%a|%s|%n' "$file"
done > /backup/restore-modes.txt
find . -type f -exec stat -c '%s' '{}' ';' |
  awk '{ total += $1 } END { print total + 0 }' > /backup/restore-bytes.txt
'@
  Invoke-DockerSafe `
    -Arguments @(
      'run', '--rm',
      '--network', 'none',
      '--name', $helperContainer,
      '--entrypoint', 'sh',
      '--mount', "type=volume,source=$restoreUploadVolume,target=/restore",
      '--mount', "type=bind,source=$stagingDirectory,target=/backup",
      $RestoreImage,
      '-ec', $uploadRestoreCommand
    ) `
    -FailureMessage 'The production-equivalent upload archive could not be restored.' |
    Out-Null

  Assert-EqualFile `
    -SourcePath (Join-Path $stagingDirectory 'source-directories.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-directories.txt') `
    -EvidenceName 'directory set'
  Assert-EqualFile `
    -SourcePath (Join-Path $stagingDirectory 'source-manifest.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-manifest.txt') `
    -EvidenceName 'file hashes'
  Assert-EqualFile `
    -SourcePath (Join-Path $stagingDirectory 'source-modes.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-modes.txt') `
    -EvidenceName 'file ownership, modes, and sizes'
  Assert-EqualFile `
    -SourcePath (Join-Path $stagingDirectory 'source-bytes.txt') `
    -RestorePath (Join-Path $stagingDirectory 'restore-bytes.txt') `
    -EvidenceName 'total file bytes'

  $sourceManifestLines = @(
    Get-Content -LiteralPath (Join-Path $stagingDirectory 'source-manifest.txt') -Encoding UTF8
  )
  if ($sourceManifestLines.Count -ne 1) {
    throw 'The production-equivalent upload snapshot must contain exactly one synthetic file.'
  }
  $restoredManifestLines = @(
    Get-Content -LiteralPath (Join-Path $stagingDirectory 'restore-manifest.txt') -Encoding UTF8
  )
  $restoredModeLines = @(
    Get-Content -LiteralPath (Join-Path $stagingDirectory 'restore-modes.txt') -Encoding UTF8
  )
  if ($restoredManifestLines.Count -ne 1 -or $restoredModeLines.Count -ne 1) {
    throw 'The restored upload evidence must contain exactly one synthetic file.'
  }
  $manifestMatch = [regex]::Match(
    [string]$restoredManifestLines[0],
    '^(?<hash>[0-9a-f]{64})  [.]\/(?<path>.+)$'
  )
  $modeParts = [string]$restoredModeLines[0] -split '\|', 5
  if (
    -not $manifestMatch.Success -or
    $modeParts.Count -ne 5 -or
    [string]$manifestMatch.Groups['path'].Value -ne [string]$restoredFileMetadata.storageKey -or
    [string]$manifestMatch.Groups['hash'].Value -ne [string]$restoredFileMetadata.checksum -or
    [long]$modeParts[3] -ne [long]$restoredFileMetadata.fileSize -or
    [string]$modeParts[4] -ne "./$($restoredFileMetadata.storageKey)"
  ) {
    throw 'The restored database file metadata does not match the restored upload blob.'
  }

  [pscustomobject]@{
    Result = 'PASS'
    SourceBoundary = 'isolated production cold-start project only'
    DatabaseBackup = 'PostgreSQL custom archive'
    DatabaseRestore = 'temporary container without network or host ports'
    UploadBackup = 'tar.gz with hash and file-mode manifest'
    UploadRestore = 'temporary Docker volume without application exposure'
    RestoredOrganizations = [int]$restoreAggregate.organizations
    RestoredRequirements = [int]$restoreAggregate.requirements
    RestoredProjects = [int]$restoreAggregate.projects
    RestoredWritingAssignments = [int]$restoreAggregate.writingAssignments
    RestoredServiceIntakeTasks = [int]$restoreAggregate.serviceIntakeTasks
    RestoredConferenceWorkItems = [int]$restoreAggregate.conferenceWorkItems
    RestoredFiles = [int]$restoreAggregate.fileAssets
    DatabaseAndUploadMatch = $true
    ExternalMediaOrSupplierData = 'not included'
    RealProductionAcceptance = 'not asserted'
  }
} finally {
  $token = $null
  $registrationPassword = $null
  $registrationBody = $null
  $restorePassword = $null
  $syntheticContent = $null
  $syntheticEmail = $null
  $headers = $null
  $downloaded = $null
  $expectedBytes = $null
  Remove-IsolatedArtifacts
}
