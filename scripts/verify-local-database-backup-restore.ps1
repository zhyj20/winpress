[CmdletBinding()]
param(
  [string]$SourceContainer = 'winpress-commercial-postgres',
  [ValidateRange(20, 120)]
  [int]$StartupTimeoutSeconds = 60,
  [string]$RestoreImage = 'postgres:17-alpine'
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$expectedComposeFile = (Resolve-Path (Join-Path $projectRoot 'docker-compose.local-demo.yml')).Path
$temporaryRoot = [System.IO.Path]::GetTempPath().TrimEnd('\')
$runId = [Guid]::NewGuid().ToString('N')
$stagingDirectory = Join-Path $temporaryRoot "winpress-db-restore-$runId"
$localDumpPath = Join-Path $stagingDirectory 'winpress-local-demo.dump'
$sourceDumpPath = "/tmp/winpress-local-demo-$runId.dump"
$restoreContainer = "winpress-db-restore-$runId"
$restoreDatabase = 'winpress_restore_qa'
$restoreUser = 'winpress_restore'
$restorePassword = "local-restore-$runId"
$restoreStarted = $false
$sourceDumpCreated = $false

$aggregateSql = @'
SELECT json_build_object(
  'publicTables', (
    SELECT count(*)
    FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  ),
  'requirements', (SELECT count(*) FROM customer_requirement),
  'projects', (SELECT count(*) FROM project),
  'conferenceItems', (SELECT count(*) FROM conference_work_item),
  'editorialTasks', (SELECT count(*) FROM editorial_task),
  'serviceIntakeTasks', (SELECT count(*) FROM service_intake_task),
  'manuscriptVersions', (SELECT count(*) FROM manuscript_version),
  'publishPlans', (SELECT count(*) FROM publish_plan),
  'publishTasks', (SELECT count(*) FROM publish_task),
  'directOrders', (SELECT count(*) FROM direct_publish_order),
  'settlementOrders', (SELECT count(*) FROM settlement_order),
  'settlementTransactions', (SELECT count(*) FROM settlement_transaction),
  'quoteAdjustmentBatches', (SELECT count(*) FROM quote_adjustment_batch),
  'quoteAdjustments', (SELECT count(*) FROM quote_adjustment),
  'supplierApiConnections', (SELECT count(*) FROM supplier_api_connection),
  'platformAcceptanceGates', (SELECT count(*) FROM platform_acceptance_gate),
  'platformAcceptanceEvidenceItems', (SELECT count(*) FROM platform_acceptance_evidence_item),
  'legacyServiceReviews', (SELECT count(*) FROM legacy_service_review),
  'schemaMigrationLedgerRows', (SELECT count(*) FROM schema_migration_ledger),
  'openApiApplications', (SELECT count(*) FROM open_api_application),
  'openApiAccessKeys', (SELECT count(*) FROM open_api_access_key),
  'openApiRequestReceipts', (SELECT count(*) FROM open_api_request_receipt),
  'openApiAccessLogs', (SELECT count(*) FROM open_api_access_log),
  'fileAssets', (SELECT count(*) FROM file_asset),
  'users', (SELECT count(*) FROM app_user),
  'serviceTypes', (
    SELECT count(DISTINCT requested_service)
    FROM customer_requirement
    WHERE requested_service IN ('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
  ),
  'serviceIntakePlaceholderTitles', (
    SELECT count(*)
    FROM service_intake_task
    WHERE btrim(title) ~ '^\?+$'
  ),
  'serviceIntakeTitleIntegrity', EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_service_intake_task_title_not_placeholder'
      AND conrelid = 'public.service_intake_task'::regclass
  ),
  'settlementTransactionEvidence', EXISTS (
    SELECT 1
    FROM pg_constraint
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
  'integrationGovernanceIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_supplier_api_enablement'
        AND conrelid = 'public.supplier_api_connection'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_platform_acceptance_gate_evidence'
        AND conrelid = 'public.platform_acceptance_gate'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_legacy_service_review_approval'
        AND conrelid = 'public.legacy_service_review'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_platform_acceptance_evidence_reference'
        AND conrelid = 'public.platform_acceptance_evidence_item'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_supplier_order_fulfillment_mode'
        AND conrelid = 'public.supplier_order'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_platform_acceptance_gate_readiness'
        AND tgrelid = 'public.platform_acceptance_gate'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_supplier_order_fulfillment_evidence'
        AND tgrelid = 'public.supplier_order'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname = 'trg_legacy_combination_service_boundary'
        AND tgrelid = 'public.customer_requirement'::regclass
        AND NOT tgisinternal
    )
    AND NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'supplier_api_connection'
        AND column_name IN ('secret','token','password','credential_value')
    )
    AND (
      SELECT count(*)
      FROM platform_acceptance_gate
      WHERE gate_code IN (
        'EXTERNAL_MEDIA_DATA',
        'SUPPLIER_FULFILLMENT',
        'LEGAL_TRUST',
        'PRODUCTION_OPERATIONS',
        'LEGACY_COMBINATION_REVIEW'
      )
    ) = 5
    AND (SELECT count(*) FROM platform_acceptance_evidence_item WHERE required) = 28
    AND NOT EXISTS (
      SELECT 1
      FROM legacy_service_review review
      WHERE review.review_status = 'APPROVED'
        AND (review.approved_action IS NULL OR review.evidence_reference IS NULL)
    )
  ),
  'schemaMigrationLedgerIntegrity', (
    to_regclass('public.schema_migration_ledger') IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM schema_migration_ledger
      WHERE migration_version=36
        AND script_name='36-schema-migration-ledger.sql'
        AND release_contract='winpress-v4.2.25-20260731'
        AND apply_mode='BASELINE'
        AND verification_reference='SCHEMA35_STRUCTURAL_BASELINE_20260731'
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_schema_migration_ledger_append_only'
        AND tgrelid='public.schema_migration_ledger'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM schema_migration_ledger
      WHERE migration_version=37
        AND script_name='37-media-pr-result-integrity.sql'
        AND release_contract='winpress-v4.2.26-20260731'
        AND apply_mode='FORWARD'
        AND verification_reference='MEDIA_PR_RESULT_CHAIN_INTEGRITY_20260731'
    )
    AND EXISTS (
      SELECT 1 FROM schema_migration_ledger
      WHERE migration_version=38
        AND script_name='38-writing-assignment-slot-schedule-integrity.sql'
        AND release_contract='winpress-v4.2.27-20260731'
        AND apply_mode='FORWARD'
        AND verification_reference='WRITING_ASSIGNMENT_SLOT_AND_SCHEDULE_INTEGRITY_20260731'
    )
    AND EXISTS (
      SELECT 1 FROM schema_migration_ledger
      WHERE migration_version=39
        AND script_name='39-writing-assignment-radius-integrity.sql'
        AND release_contract='winpress-v4.2.28-20260731'
        AND apply_mode='FORWARD'
        AND verification_reference='WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731'
    )
    AND EXISTS (
      SELECT 1 FROM schema_migration_ledger
      WHERE migration_version=40
        AND script_name='40-conference-work-item-state-integrity.sql'
        AND release_contract='winpress-v4.2.29-20260731'
        AND apply_mode='FORWARD'
        AND verification_reference='CONFERENCE_WORK_ITEM_STATE_INTEGRITY_20260731'
    )
    AND EXISTS (
      SELECT 1 FROM schema_migration_ledger
      WHERE migration_version=41
        AND script_name='41-conference-media-candidate-state-integrity.sql'
        AND release_contract='winpress-v4.2.30-20260731'
        AND apply_mode='FORWARD'
        AND verification_reference='CONFERENCE_MEDIA_CANDIDATE_STATE_INTEGRITY_20260731'
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ex_writing_assignment_member_no_overlap'
        AND conrelid='public.writing_assignment_member'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_writer_profile_service_radius_nonnegative'
        AND conrelid='public.writer_profile'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_writing_assignment_member_distance_nonnegative'
        AND conrelid='public.writing_assignment_member'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_writing_assignment_member_radius_integrity'
        AND tgrelid='public.writing_assignment_member'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_writer_profile_radius_integrity'
        AND tgrelid='public.writer_profile'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_conference_work_item_completion_time'
        AND conrelid='public.conference_work_item'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_conference_work_item_terminal_integrity'
        AND tgrelid='public.conference_work_item'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_conference_project_completion_integrity'
        AND tgrelid='public.conference_project'::regclass
        AND NOT tgisinternal
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_conference_media_candidate_status_timeline'
        AND conrelid='public.conference_media_candidate'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_conference_media_candidate_contact_time_order'
        AND conrelid='public.conference_media_candidate'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_conference_media_candidate_outcome_note'
        AND conrelid='public.conference_media_candidate'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_conference_media_candidate_state_integrity'
        AND tgrelid='public.conference_media_candidate'::regclass
        AND NOT tgisinternal
    )
  ),
  'openApiGovernanceIntegrity', (
    to_regclass('public.open_api_application') IS NOT NULL
    AND to_regclass('public.open_api_access_key') IS NOT NULL
    AND to_regclass('public.open_api_request_receipt') IS NOT NULL
    AND to_regclass('public.open_api_access_log') IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_open_api_application_activation'
        AND conrelid = 'public.open_api_application'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname = 'ck_open_api_access_key_revocation'
        AND conrelid = 'public.open_api_access_key'::regclass
        AND convalidated
    )
    AND NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='open_api_access_key'
        AND column_name IN ('access_key','raw_key','secret','token','password')
    )
    AND NOT EXISTS (
      SELECT 1 FROM open_api_application
      WHERE status='ACTIVE' AND (
        authorization_status <> 'VERIFIED'
        OR sandbox_status <> 'PASSED'
        OR (environment='PRODUCTION' AND production_status <> 'APPROVED')
      )
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

function Invoke-Docker {
  param([string[]]$Arguments)

  $output = & docker @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "Docker command failed: $($Arguments[0])"
  }
  return $output
}

function Get-DatabaseAggregate {
  param(
    [string]$Container,
    [string]$DatabaseUser,
    [string]$DatabaseName
  )

  $raw = & docker exec $Container psql -At -v ON_ERROR_STOP=1 -U $DatabaseUser -d $DatabaseName -c $aggregateSql 2>&1
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($raw -join ''))) {
    throw "Database aggregate check failed for isolated container: $Container"
  }
  return (($raw -join "`n") | ConvertFrom-Json)
}

function Remove-TemporaryArtifacts {
  if ($restoreStarted -and $restoreContainer -like 'winpress-db-restore-*') {
    & docker rm -f $restoreContainer *> $null
  }

  if ($sourceDumpCreated -and $sourceDumpPath -like '/tmp/winpress-local-demo-*.dump') {
    & docker exec $SourceContainer rm -f $sourceDumpPath *> $null
  }

  if (Test-Path -LiteralPath $stagingDirectory) {
    $resolvedStagingDirectory = (Resolve-Path -LiteralPath $stagingDirectory).Path
    if ($resolvedStagingDirectory.StartsWith(($temporaryRoot + '\'), [System.StringComparison]::OrdinalIgnoreCase)) {
      Remove-Item -LiteralPath $resolvedStagingDirectory -Recurse -Force
    }
  }
}

try {
  $inspectRaw = & docker inspect $SourceContainer 2>&1
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($inspectRaw -join ''))) {
    throw 'The local demonstration PostgreSQL container is not available.'
  }

  $inspect = (($inspectRaw -join "`n") | ConvertFrom-Json)[0]
  if (-not $inspect.State.Running -or $inspect.State.Health.Status -ne 'healthy') {
    throw 'The local demonstration PostgreSQL container must be running and healthy.'
  }

  $composeFileLabel = [string]$inspect.Config.Labels.'com.docker.compose.project.config_files'
  $workingDirectoryLabel = [string]$inspect.Config.Labels.'com.docker.compose.project.working_dir'
  if (
    -not $composeFileLabel.Equals($expectedComposeFile, [System.StringComparison]::OrdinalIgnoreCase) -or
    -not $workingDirectoryLabel.Equals($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)
  ) {
    throw 'The selected source is not this project local-demonstration database. Production or unrelated containers are not accepted.'
  }

  $sourceEnvironment = @{}
  foreach ($entry in $inspect.Config.Env) {
    $separator = $entry.IndexOf('=')
    if ($separator -gt 0) {
      $sourceEnvironment[$entry.Substring(0, $separator)] = $entry.Substring($separator + 1)
    }
  }
  $sourceUser = [string]$sourceEnvironment['POSTGRES_USER']
  $sourceDatabase = [string]$sourceEnvironment['POSTGRES_DB']
  if ([string]::IsNullOrWhiteSpace($sourceUser) -or [string]::IsNullOrWhiteSpace($sourceDatabase)) {
    throw 'The local demonstration database identity is incomplete.'
  }

  New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

  $dumpCommand = 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges --serializable-deferrable --file="' + $sourceDumpPath + '"'
  $sourceDumpCreated = $true
  Invoke-Docker @('exec', $SourceContainer, 'sh', '-ec', $dumpCommand) | Out-Null
  Invoke-Docker @('cp', "${SourceContainer}:$sourceDumpPath", $localDumpPath) | Out-Null

  $dumpFile = Get-Item -LiteralPath $localDumpPath
  if ($dumpFile.Length -lt 1024) {
    throw 'The generated PostgreSQL backup is unexpectedly small.'
  }
  $dumpHash = (Get-FileHash -LiteralPath $localDumpPath -Algorithm SHA256).Hash.ToLowerInvariant()

  Invoke-Docker @(
    'run', '--detach', '--rm',
    '--name', $restoreContainer,
    '--label', 'winpress.qa.purpose=database-backup-restore',
    '--env', "POSTGRES_DB=$restoreDatabase",
    '--env', "POSTGRES_USER=$restoreUser",
    '--env', "POSTGRES_PASSWORD=$restorePassword",
    '--tmpfs', '/var/lib/postgresql/data:rw,noexec,nosuid,size=1g',
    $RestoreImage
  ) | Out-Null
  $restoreStarted = $true

  $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
  $restoreReady = $false
  while ((Get-Date) -lt $deadline) {
    & docker exec $restoreContainer pg_isready -U $restoreUser -d $restoreDatabase *> $null
    if ($LASTEXITCODE -eq 0) {
      $restoreReady = $true
      break
    }
    Start-Sleep -Seconds 1
  }
  if (-not $restoreReady) {
    throw 'The isolated restore database did not become ready within the configured timeout.'
  }

  $restoreDumpPath = '/tmp/winpress-local-demo.dump'
  Invoke-Docker @('cp', $localDumpPath, "${restoreContainer}:$restoreDumpPath") | Out-Null
  Invoke-Docker @(
    'exec', $restoreContainer,
    'pg_restore',
    '-U', $restoreUser,
    '-d', $restoreDatabase,
    '--no-owner',
    '--no-privileges',
    '--exit-on-error',
    $restoreDumpPath
  ) | Out-Null

  $sourceAggregate = Get-DatabaseAggregate -Container $SourceContainer -DatabaseUser $sourceUser -DatabaseName $sourceDatabase
  $restoreAggregate = Get-DatabaseAggregate -Container $restoreContainer -DatabaseUser $restoreUser -DatabaseName $restoreDatabase
  $aggregateFields = @(
    'publicTables',
    'requirements',
    'projects',
    'conferenceItems',
    'editorialTasks',
    'serviceIntakeTasks',
    'manuscriptVersions',
    'publishPlans',
    'publishTasks',
    'directOrders',
    'settlementOrders',
    'settlementTransactions',
    'quoteAdjustmentBatches',
    'quoteAdjustments',
    'supplierApiConnections',
    'platformAcceptanceGates',
    'legacyServiceReviews',
    'schemaMigrationLedgerRows',
    'openApiApplications',
    'openApiAccessKeys',
    'openApiRequestReceipts',
    'openApiAccessLogs',
    'fileAssets',
    'users',
    'serviceTypes',
    'serviceIntakePlaceholderTitles',
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
    'integrationGovernanceIntegrity',
    'schemaMigrationLedgerIntegrity',
    'openApiGovernanceIntegrity'
  )
  foreach ($field in $aggregateFields) {
    if ([string]$sourceAggregate.$field -ne [string]$restoreAggregate.$field) {
      throw "Restored aggregate does not match the local demonstration snapshot: $field"
    }
  }
  if ([int]$restoreAggregate.publicTables -lt 48) {
    throw 'The restored database is missing required workflow tables.'
  }
  if ([int]$restoreAggregate.serviceTypes -lt 4) {
    throw 'The restored database does not retain all four independent service types.'
  }
  if ([int]$restoreAggregate.serviceIntakePlaceholderTitles -ne 0 -or $restoreAggregate.serviceIntakeTitleIntegrity -ne $true) {
    throw 'The restored database does not retain the service-intake title integrity rule.'
  }
  if ($restoreAggregate.settlementTransactionEvidence -ne $true) {
    throw 'The restored database does not retain the settlement-transaction evidence rule.'
  }
  if ($restoreAggregate.batchQuoteAdjustmentIdempotency -ne $true) {
    throw 'The restored database does not retain the batch quote-adjustment idempotency rules.'
  }
  if ($restoreAggregate.publishPlanServiceIntegrity -ne $true) {
    throw 'The restored database does not retain the publish-plan service boundary.'
  }
  if ($restoreAggregate.integrationGovernanceIntegrity -ne $true) {
    throw 'The restored database does not retain the supplier integration and acceptance-gate boundaries.'
  }
  if ($restoreAggregate.schemaMigrationLedgerIntegrity -ne $true) {
    throw 'The restored database does not retain the schema migration ledger baseline.'
  }
  if ($restoreAggregate.openApiGovernanceIntegrity -ne $true) {
    throw 'The restored database does not retain the Open API application, key and receipt boundaries.'
  }
  if ($restoreAggregate.requirementIdempotency -ne $true) {
    throw 'The restored database does not retain the requirement idempotency rule.'
  }
  if ($restoreAggregate.taskAcceptanceIntegrity -ne $true) {
    throw 'The restored database does not retain the task acceptance integrity rules.'
  }
  if ($restoreAggregate.mediaInvitationProgressIntegrity -ne $true) {
    throw 'The restored database does not retain the media invitation progress integrity rules.'
  }
  if ($restoreAggregate.publishTaskTerminalIntegrity -ne $true) {
    throw 'The restored database does not retain the publish-task terminal integrity guard.'
  }
  if ($restoreAggregate.publishPlanIdempotency -ne $true) {
    throw 'The restored database does not retain the publish-plan idempotency rule.'
  }
  if ($restoreAggregate.settlementTransactionIdempotency -ne $true) {
    throw 'The restored database does not retain the settlement-transaction idempotency rule.'
  }

  [pscustomobject]@{
    Result = 'PASS'
    SourceBoundary = 'current project local demonstration database only'
    RestoreIsolation = 'temporary container without host ports or persistent volume'
    BackupFormat = 'PostgreSQL custom'
    BackupBytes = [long]$dumpFile.Length
    BackupSha256 = $dumpHash
    PublicTables = [int]$restoreAggregate.publicTables
    IndependentServiceTypes = [int]$restoreAggregate.serviceTypes
    CriticalAggregateMatch = $true
    ServiceIntakePlaceholderTitles = [int]$restoreAggregate.serviceIntakePlaceholderTitles
    OpenApiGovernance = [bool]$restoreAggregate.openApiGovernanceIntegrity
    SchemaMigrationLedger = [bool]$restoreAggregate.schemaMigrationLedgerIntegrity
    FileStorage = 'not included; database metadata only'
    ProductionAcceptance = 'not asserted'
  }
}
finally {
  Remove-TemporaryArtifacts
}
