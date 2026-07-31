[CmdletBinding()]
param(
  [ValidateRange(20, 55)]
  [int]$StartupTimeoutSeconds = 55,
  [string]$PostgresImage = 'postgres:17-alpine'
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$databaseRoot = Join-Path $projectRoot 'database'
$accountDocument = Join-Path $projectRoot 'docs\TEST-ACCOUNTS.md'
. (Join-Path $PSScriptRoot 'lib\LocalTestAccounts.ps1')
$temporaryRoot = [System.IO.Path]::GetTempPath().TrimEnd('\\')
$runId = [Guid]::NewGuid().ToString('N')
$stagingDirectory = Join-Path $temporaryRoot "winpress-clean-db-$runId"
$containerName = "winpress-clean-db-$runId"
$databaseName = 'winpress_clean_smoke'
$databaseUser = 'winpress_smoke'
$databasePassword = "local-smoke-$runId"
$startedContainer = $false

$initFiles = @(
  @{ Source = 'schema.sql'; Destination = '01-schema.sql' },
  @{ Source = 'seed.sql'; Destination = '02-seed.sql' },
  @{ Source = '10-brand-case-demo-data.sql'; Destination = '02z-brand-case-demo-data.sql' },
  @{ Source = '03-media-import.sql'; Destination = '03-media-import.sql' },
  @{ Source = '04-normalize-public-channel-data.sql'; Destination = '04-normalize-public-channel-data.sql' },
  @{ Source = '13-conference-progressive-intake.sql'; Destination = '13-conference-progressive-intake.sql' },
  @{ Source = '14-supplier-orders-and-conference-workbench.sql'; Destination = '14-supplier-orders-and-conference-workbench.sql' },
  @{ Source = '15-niumedia-reporter-and-multi-invitation.sql'; Destination = '15-niumedia-reporter-and-multi-invitation.sql' },
  @{ Source = '16-media-partnership-inquiry.sql'; Destination = '16-media-partnership-inquiry.sql' },
  @{ Source = '17-local-demo-writing-assignment.sql'; Destination = '17-local-demo-writing-assignment.sql' },
  @{ Source = '18-service-intake-tasks.sql'; Destination = '18-service-intake-tasks.sql' },
  @{ Source = '19-activity-project-linkage.sql'; Destination = '19-activity-project-linkage.sql' },
  @{ Source = '20-direct-manuscript-source.sql'; Destination = '20-direct-manuscript-source.sql' },
  @{ Source = '21-manual-media-invitation-pending-verification.sql'; Destination = '21-manual-media-invitation-pending-verification.sql' },
  @{ Source = '22-service-intake-title-integrity.sql'; Destination = '22-service-intake-title-integrity.sql' },
  @{ Source = '23-settlement-transaction-ledger.sql'; Destination = '23-settlement-transaction-ledger.sql' },
  @{ Source = '24-requirement-idempotency.sql'; Destination = '24-requirement-idempotency.sql' },
  @{ Source = '25-task-acceptance-integrity.sql'; Destination = '25-task-acceptance-integrity.sql' },
  @{ Source = '26-channel-quote-integrity.sql'; Destination = '26-channel-quote-integrity.sql' },
  @{ Source = '27-media-invitation-progress-integrity.sql'; Destination = '27-media-invitation-progress-integrity.sql' },
  @{ Source = '28-publish-task-terminal-integrity.sql'; Destination = '28-publish-task-terminal-integrity.sql' },
  @{ Source = '29-publish-plan-idempotency.sql'; Destination = '29-publish-plan-idempotency.sql' },
  @{ Source = '30-settlement-transaction-idempotency.sql'; Destination = '30-settlement-transaction-idempotency.sql' },
  @{ Source = '31-batch-quote-adjustment-idempotency.sql'; Destination = '31-batch-quote-adjustment-idempotency.sql' },
  @{ Source = '32-publish-plan-service-integrity.sql'; Destination = '32-publish-plan-service-integrity.sql' },
  @{ Source = '33-supplier-api-connections.sql'; Destination = '33-supplier-api-connections.sql' },
  @{ Source = '34-open-api-management.sql'; Destination = '34-open-api-management.sql' },
  @{ Source = '35-release-governance-and-evidence.sql'; Destination = '35-release-governance-and-evidence.sql' },
  @{ Source = '36-schema-migration-ledger.sql'; Destination = '36-schema-migration-ledger.sql' },
  @{ Source = '37-media-pr-result-integrity.sql'; Destination = '37-media-pr-result-integrity.sql' },
  @{ Source = '38-writing-assignment-slot-schedule-integrity.sql'; Destination = '38-writing-assignment-slot-schedule-integrity.sql' },
  @{ Source = '39-writing-assignment-radius-integrity.sql'; Destination = '39-writing-assignment-radius-integrity.sql' },
  @{ Source = '40-conference-work-item-state-integrity.sql'; Destination = '40-conference-work-item-state-integrity.sql' },
  @{ Source = '41-conference-media-candidate-state-integrity.sql'; Destination = '41-conference-media-candidate-state-integrity.sql' },
  @{ Source = 'media_channels.csv'; Destination = 'media_channels.csv' },
  @{ Source = 'media_quotes.csv'; Destination = 'media_quotes.csv' }
)

function Invoke-Docker {
  param([string[]]$Arguments)
  $output = & docker @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "Docker command failed: $($Arguments -join ' ')"
  }
  return $output
}

function Remove-TemporaryArtifacts {
  if ($startedContainer -and $containerName -like 'winpress-clean-db-*') {
    & docker rm -f $containerName *> $null
  }
  if (Test-Path -LiteralPath $stagingDirectory) {
    $resolvedStagingDirectory = (Resolve-Path -LiteralPath $stagingDirectory).Path
    if ($resolvedStagingDirectory.StartsWith(($temporaryRoot + '\\'), [System.StringComparison]::OrdinalIgnoreCase)) {
      Remove-Item -LiteralPath $resolvedStagingDirectory -Recurse -Force
    }
  }
}

try {
  $demoCredentials = @(Get-WinPressLocalDemoCredentials -AccountDocument $accountDocument)
  New-Item -ItemType Directory -Path $stagingDirectory | Out-Null
  foreach ($file in $initFiles) {
    $source = Join-Path $databaseRoot $file.Source
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
      throw "Required database input is missing: $($file.Source)"
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $stagingDirectory $file.Destination)
  }
  $expectedMediaChannels = @((Import-Csv -LiteralPath (Join-Path $databaseRoot 'media_channels.csv') -Encoding UTF8).channel_no | Sort-Object -Unique).Count
  $expectedMediaQuotes = @((Import-Csv -LiteralPath (Join-Path $databaseRoot 'media_quotes.csv') -Encoding UTF8).quote_no | Sort-Object -Unique).Count
  if ($expectedMediaChannels -lt 1 -or $expectedMediaQuotes -lt 1) {
    throw 'Static media seed files do not contain the expected identifiers.'
  }

  $mount = "${stagingDirectory}:/docker-entrypoint-initdb.d:ro"
  Invoke-Docker @(
    'run', '--detach', '--name', $containerName,
    '--env', "POSTGRES_DB=$databaseName",
    '--env', "POSTGRES_USER=$databaseUser",
    '--env', "POSTGRES_PASSWORD=$databasePassword",
    '--volume', $mount,
    $PostgresImage
  ) | Out-Null
  $startedContainer = $true

  $readinessSql = @'
SELECT json_build_object(
  'customerRequirement', to_regclass('public.customer_requirement') IS NOT NULL,
  'project', to_regclass('public.project') IS NOT NULL,
  'conferenceWorkItem', to_regclass('public.conference_work_item') IS NOT NULL,
  'editorialTask', to_regclass('public.editorial_task') IS NOT NULL,
  'serviceIntakeTask', to_regclass('public.service_intake_task') IS NOT NULL,
  'manuscriptVersion', to_regclass('public.manuscript_version') IS NOT NULL,
  'publishTask', to_regclass('public.publish_task') IS NOT NULL,
  'directPublishOrder', to_regclass('public.direct_publish_order') IS NOT NULL,
  'settlementOrder', to_regclass('public.settlement_order') IS NOT NULL,
  'settlementTransaction', to_regclass('public.settlement_transaction') IS NOT NULL,
  'quoteAdjustmentBatch', to_regclass('public.quote_adjustment_batch') IS NOT NULL,
  'openApiApplication', to_regclass('public.open_api_application') IS NOT NULL,
  'openApiAccessKey', to_regclass('public.open_api_access_key') IS NOT NULL,
  'openApiRequestReceipt', to_regclass('public.open_api_request_receipt') IS NOT NULL,
  'openApiAccessLog', to_regclass('public.open_api_access_log') IS NOT NULL,
  'acceptanceEvidenceItem', to_regclass('public.platform_acceptance_evidence_item') IS NOT NULL,
  'schemaMigrationLedger', (
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
      SELECT 1 FROM pg_trigger
      WHERE tgname='trg_schema_migration_ledger_append_only'
        AND tgrelid='public.schema_migration_ledger'::regclass
        AND NOT tgisinternal
    )
  ),
  'writingAssignmentScheduleIntegrity', (
    to_regclass('public.writing_assignment_member') IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ex_writing_assignment_member_no_overlap'
        AND conrelid='public.writing_assignment_member'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_extension WHERE extname='btree_gist'
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
  ),
  'conferenceWorkItemIntegrity', (
    EXISTS (
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
  ),
  'conferenceMediaCandidateIntegrity', (
    EXISTS (
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
  'activityRootProjectId', EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'project'
      AND column_name = 'activity_root_project_id'
  ),
  'sourceManuscriptId', EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'manuscript_version'
      AND column_name = 'source_manuscript_id'
  ),
  'sourceVersionId', EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'manuscript_version'
      AND column_name = 'source_version_id'
  ),
  'manualInvitationPlanItem', EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'publish_plan_item'
      AND column_name = 'channel_id' AND is_nullable = 'YES'
  ),
  'manualInvitationTask', EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'publish_task'
      AND column_name = 'channel_id' AND is_nullable = 'YES'
  ),
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
  'mediaResultChainIntegrity', (
    EXISTS (
      SELECT 1 FROM pg_proc proc
      JOIN pg_namespace namespace ON namespace.oid=proc.pronamespace
      WHERE namespace.nspname='public'
        AND proc.proname='enforce_publish_task_terminal_integrity'
        AND proc.prosrc LIKE '%completed media tasks require a recorded invitation and reported outcome%'
    )
    AND NOT EXISTS (
      SELECT 1
      FROM publish_task task
      WHERE task.status IN ('COMPLETED','CLIENT_ACCEPTED')
        AND NOT EXISTS (
          SELECT 1 FROM result_link result
          WHERE result.publish_task_id=task.id
            AND result.status='VERIFIED'
        )
    )
    AND NOT EXISTS (
      SELECT 1
      FROM publish_task task
      LEFT JOIN media_pr_invitation invitation ON invitation.publish_task_id=task.id
      WHERE task.channel_type='MEDIA_PR'
        AND task.status IN ('COMPLETED','CLIENT_ACCEPTED')
        AND (
          invitation.status IS DISTINCT FROM 'REPORTED'
          OR invitation.invited_at IS NULL
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
      WHERE conname = 'ck_quote_adjustment_batch_counts'
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
  'openApiIntegrity', (
    EXISTS (
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
      WHERE table_schema = 'public'
        AND table_name = 'open_api_access_key'
        AND column_name IN ('access_key','raw_key','secret','token','password')
    )
    AND NOT EXISTS (
      SELECT 1 FROM open_api_application
      WHERE status='ACTIVE'
        AND (
          authorization_status <> 'VERIFIED'
          OR sandbox_status <> 'PASSED'
          OR (environment='PRODUCTION' AND production_status <> 'APPROVED')
      )
    )
  ),
  'releaseGovernanceIntegrity', (
    (SELECT count(*) FROM platform_acceptance_evidence_item WHERE required) = 28
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_platform_acceptance_evidence_reference'
        AND conrelid='public.platform_acceptance_evidence_item'::regclass
        AND convalidated
    )
    AND EXISTS (
      SELECT 1 FROM pg_constraint
      WHERE conname='ck_supplier_order_fulfillment_mode'
        AND conrelid='public.supplier_order'::regclass
        AND convalidated
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
  )
)::text;
'@
  $countsSql = "SELECT json_build_object('mediaChannels', (SELECT count(*) FROM publish_channel), 'mediaQuotes', (SELECT count(*) FROM channel_quote), 'serviceIntakeTasks', (SELECT count(*) FROM service_intake_task), 'serviceIntakePlaceholderTitles', (SELECT count(*) FROM service_intake_task WHERE btrim(title) ~ '^\?+$'))::text;"
  $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
  $readiness = $null
  $counts = $null
  while ((Get-Date) -lt $deadline) {
    & docker exec $containerName pg_isready -U $databaseUser -d $databaseName *> $null
    if ($LASTEXITCODE -eq 0) {
      $initLog = (& cmd.exe /d /c "docker logs $containerName 2>&1") -join "`n"
      if ($initLog -match 'PostgreSQL init process complete; ready for start up') {
        $candidateRaw = & docker exec $containerName psql -At -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c $readinessSql 2>$null
        $candidateCountsRaw = & docker exec $containerName psql -At -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c $countsSql 2>$null
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($candidateRaw -join '')) -or [string]::IsNullOrWhiteSpace(($candidateCountsRaw -join ''))) {
          Start-Sleep -Seconds 1
          continue
        }
        try {
          $candidateReadiness = ($candidateRaw -join "`n") | ConvertFrom-Json
          $candidateCounts = ($candidateCountsRaw -join "`n") | ConvertFrom-Json
          $candidateMissing = @($candidateReadiness.PSObject.Properties | Where-Object { $_.Value -ne $true })
          $seedCountsMatch = ([int]$candidateCounts.mediaChannels -eq $expectedMediaChannels) -and ([int]$candidateCounts.mediaQuotes -eq $expectedMediaQuotes) -and ([int]$candidateCounts.serviceIntakePlaceholderTitles -eq 0)
          if ($candidateMissing.Count -eq 0 -and $seedCountsMatch) {
            $readiness = $candidateReadiness
            $counts = $candidateCounts
            break
          }
        }
        catch {
          # PostgreSQL may still be replaying the initialization scripts; retry until the deadline.
        }
      }
    }
    Start-Sleep -Seconds 1
  }
  if ($null -eq $readiness -or $null -eq $counts) {
    throw 'Temporary database initialization did not complete the required workflow structures and static seed import within the configured timeout.'
  }

  $serviceIntegrityWriteSql = @'
BEGIN;
DO $$
DECLARE
  qa_user_id BIGINT;
  qa_organization_id BIGINT;
  qa_requirement_id BIGINT;
  qa_project_id BIGINT;
  qa_plan_id BIGINT;
  rejected BOOLEAN := FALSE;
  qa_suffix TEXT := txid_current()::TEXT;
BEGIN
  SELECT user_record.id, user_record.organization_id
  INTO qa_user_id, qa_organization_id
  FROM app_user user_record
  JOIN user_role role_link ON role_link.user_id=user_record.id
  JOIN sys_role role_record ON role_record.id=role_link.role_id
  WHERE role_record.role_code='CUSTOMER' AND user_record.status='ACTIVE'
  ORDER BY user_record.id
  LIMIT 1;

  IF qa_user_id IS NULL THEN
    RAISE EXCEPTION 'clean database has no customer account for service-integrity verification';
  END IF;

  INSERT INTO customer_requirement
    (requirement_no, customer_id, organization_id, title, requested_service)
  VALUES
    ('REQ-SVC-QA-' || qa_suffix, qa_user_id, qa_organization_id,
     '发布计划服务边界验证', 'NEWS_CONFERENCE')
  RETURNING id INTO qa_requirement_id;

  INSERT INTO project
    (project_no, requirement_id, organization_id, customer_id, project_name)
  VALUES
    ('PRJ-SVC-QA-' || qa_suffix, qa_requirement_id, qa_organization_id, qa_user_id,
     '发布计划服务边界验证')
  RETURNING id INTO qa_project_id;

  INSERT INTO publish_plan
    (plan_no, project_id, plan_name, created_by)
  VALUES
    ('PLAN-SVC-QA-' || qa_suffix, qa_project_id, '不应写入的跨服务计划', qa_user_id)
  RETURNING id INTO qa_plan_id;

  BEGIN
    INSERT INTO publish_plan_item
      (item_no, publish_plan_id, channel_type, media_name)
    VALUES
      ('PLAN-ITM-SVC-QA-' || qa_suffix, qa_plan_id, 'MEDIA_PR', '边界验证媒体');
  EXCEPTION WHEN check_violation THEN
    rejected := TRUE;
  END;

  IF NOT rejected THEN
    RAISE EXCEPTION 'publish plan service-integrity trigger did not reject a cross-service item';
  END IF;
END
$$;
ROLLBACK;
'@
  & docker exec $containerName psql -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c $serviceIntegrityWriteSql *> $null
  if ($LASTEXITCODE -ne 0) {
    throw 'Temporary database did not enforce the publish-plan service boundary.'
  }

  $mediaResultIntegrityWriteSql = @'
BEGIN;
DO $$
DECLARE
  target_task_id BIGINT;
BEGIN
  SELECT task.id
  INTO target_task_id
  FROM publish_task task
  JOIN media_pr_invitation invitation ON invitation.publish_task_id=task.id
  WHERE task.channel_type='MEDIA_PR'
    AND task.status NOT IN ('COMPLETED','CLIENT_ACCEPTED','NOT_PROCEEDING')
    AND invitation.status='PENDING'
  ORDER BY task.id
  LIMIT 1;

  IF target_task_id IS NULL THEN
    RAISE EXCEPTION 'clean database has no pending media invitation for result-integrity verification';
  END IF;

  INSERT INTO result_link (
    result_no, project_id, publish_task_id, channel_name, title, url, status
  )
  SELECT
    'QA-MEDIA-GUARD-' || task.id,
    task.project_id,
    task.id,
    invitation.media_name,
    'Synthetic result integrity check',
    'https://example.invalid/media-result-guard/' || task.id,
    'VERIFIED'
  FROM publish_task task
  JOIN media_pr_invitation invitation ON invitation.publish_task_id=task.id
  WHERE task.id=target_task_id;

  BEGIN
    UPDATE publish_task SET status='COMPLETED' WHERE id=target_task_id;
    RAISE EXCEPTION 'media result integrity trigger did not reject a pending invitation';
  EXCEPTION WHEN OTHERS THEN
    IF position('completed media tasks require a recorded invitation and reported outcome' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
  END;
END;
$$;
ROLLBACK;
'@
  & docker exec $containerName psql -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c $mediaResultIntegrityWriteSql *> $null
  if ($LASTEXITCODE -ne 0) {
    throw 'Temporary database did not enforce media invitation before result completion.'
  }

  $writingAssignmentScheduleIntegrityWriteSql = @'
BEGIN;
DO $$
DECLARE
  qa_user_id BIGINT;
  qa_organization_id BIGINT;
  qa_writer_profile_id BIGINT;
  qa_requirement_one_id BIGINT;
  qa_requirement_two_id BIGINT;
  qa_project_one_id BIGINT;
  qa_project_two_id BIGINT;
  qa_task_one_id BIGINT;
  qa_task_two_id BIGINT;
  qa_assignment_one_id BIGINT;
  qa_assignment_two_id BIGINT;
  rejected BOOLEAN := FALSE;
  qa_suffix TEXT := txid_current()::TEXT;
BEGIN
  SELECT user_record.id, user_record.organization_id
  INTO qa_user_id, qa_organization_id
  FROM app_user user_record
  JOIN user_role role_link ON role_link.user_id=user_record.id
  JOIN sys_role role_record ON role_record.id=role_link.role_id
  WHERE role_record.role_code='CUSTOMER' AND user_record.status='ACTIVE'
  ORDER BY user_record.id
  LIMIT 1;

  SELECT id INTO qa_writer_profile_id
  FROM writer_profile
  WHERE status='ACTIVE' AND availability_status='AVAILABLE'
  ORDER BY id
  LIMIT 1;

  IF qa_user_id IS NULL OR qa_writer_profile_id IS NULL THEN
    RAISE EXCEPTION 'clean database is missing a customer or available writer for schedule-integrity verification';
  END IF;

  UPDATE writer_profile
  SET service_radius_km=1000000
  WHERE id=qa_writer_profile_id;

  INSERT INTO customer_requirement (
    requirement_no, customer_id, organization_id, title, event_time,
    requested_service, service_days, writer_count, unit_price, estimated_amount
  ) VALUES (
    'REQ-WRT-SCHED-A-' || qa_suffix, qa_user_id, qa_organization_id,
    '写手档期互斥验证 A', TIMESTAMPTZ '2035-01-10 08:00:00+00',
    'ONSITE_WRITING', 1, 1, 980, 980
  ) RETURNING id INTO qa_requirement_one_id;

  INSERT INTO customer_requirement (
    requirement_no, customer_id, organization_id, title, event_time,
    requested_service, service_days, writer_count, unit_price, estimated_amount
  ) VALUES (
    'REQ-WRT-SCHED-B-' || qa_suffix, qa_user_id, qa_organization_id,
    '写手档期互斥验证 B', TIMESTAMPTZ '2035-01-10 12:00:00+00',
    'ONSITE_WRITING', 1, 1, 980, 980
  ) RETURNING id INTO qa_requirement_two_id;

  INSERT INTO project (project_no, requirement_id, organization_id, customer_id, project_name)
  VALUES (
    'PRJ-WRT-SCHED-A-' || qa_suffix, qa_requirement_one_id,
    qa_organization_id, qa_user_id, '写手档期互斥验证 A'
  ) RETURNING id INTO qa_project_one_id;

  INSERT INTO project (project_no, requirement_id, organization_id, customer_id, project_name)
  VALUES (
    'PRJ-WRT-SCHED-B-' || qa_suffix, qa_requirement_two_id,
    qa_organization_id, qa_user_id, '写手档期互斥验证 B'
  ) RETURNING id INTO qa_project_two_id;

  INSERT INTO editorial_task (task_no, project_id, requirement_id, status)
  VALUES (
    'EDT-WRT-SCHED-A-' || qa_suffix, qa_project_one_id,
    qa_requirement_one_id, 'PENDING_ASSIGNMENT'
  ) RETURNING id INTO qa_task_one_id;

  INSERT INTO editorial_task (task_no, project_id, requirement_id, status)
  VALUES (
    'EDT-WRT-SCHED-B-' || qa_suffix, qa_project_two_id,
    qa_requirement_two_id, 'PENDING_ASSIGNMENT'
  ) RETURNING id INTO qa_task_two_id;

  INSERT INTO writing_assignment (
    assignment_no, editorial_task_id, matching_mode, service_days,
    writer_count, unit_price_snapshot, estimated_amount_snapshot, status
  ) VALUES (
    'WRT-SCHED-A-' || qa_suffix, qa_task_one_id, 'NEAREST_AVAILABLE',
    1, 1, 980, 980, 'WAITING_MATCH'
  ) RETURNING id INTO qa_assignment_one_id;

  INSERT INTO writing_assignment (
    assignment_no, editorial_task_id, matching_mode, service_days,
    writer_count, unit_price_snapshot, estimated_amount_snapshot, status
  ) VALUES (
    'WRT-SCHED-B-' || qa_suffix, qa_task_two_id, 'NEAREST_AVAILABLE',
    1, 1, 980, 980, 'WAITING_MATCH'
  ) RETURNING id INTO qa_assignment_two_id;

  INSERT INTO writing_assignment_member (
    member_no, assignment_id, writer_profile_id, service_window, distance_km, status
  ) VALUES (
    'WRT-MBR-SCHED-A-' || qa_suffix, qa_assignment_one_id, qa_writer_profile_id,
    tstzrange(TIMESTAMPTZ '2035-01-10 08:00:00+00', TIMESTAMPTZ '2035-01-11 08:00:00+00', '[)'),
    10, 'ACCEPTED'
  );

  BEGIN
    INSERT INTO writing_assignment_member (
      member_no, assignment_id, writer_profile_id, service_window, distance_km, status
    ) VALUES (
      'WRT-MBR-SCHED-B-' || qa_suffix, qa_assignment_two_id, qa_writer_profile_id,
      tstzrange(TIMESTAMPTZ '2035-01-10 12:00:00+00', TIMESTAMPTZ '2035-01-11 12:00:00+00', '[)'),
      10, 'ACCEPTED'
    );
  EXCEPTION WHEN exclusion_violation THEN
    rejected := TRUE;
  END;

  IF NOT rejected THEN
    RAISE EXCEPTION 'writing assignment schedule exclusion constraint did not reject an overlapping confirmed writer seat';
  END IF;

  rejected := FALSE;
  BEGIN
    UPDATE writing_assignment_member
    SET distance_km=NULL
    WHERE member_no='WRT-MBR-SCHED-A-' || qa_suffix;
    RAISE EXCEPTION 'writing assignment radius trigger did not require a verified distance';
  EXCEPTION WHEN check_violation THEN
    IF position('distance is required when writer service radius is configured' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
    rejected := TRUE;
  END;
  IF NOT rejected THEN
    RAISE EXCEPTION 'writing assignment radius trigger did not reject a missing distance';
  END IF;

  rejected := FALSE;
  BEGIN
    UPDATE writing_assignment_member
    SET distance_km=1000001
    WHERE member_no='WRT-MBR-SCHED-A-' || qa_suffix;
    RAISE EXCEPTION 'writing assignment radius trigger did not reject an outside-radius distance';
  EXCEPTION WHEN check_violation THEN
    IF position('distance exceeds writer service radius' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
    rejected := TRUE;
  END;
  IF NOT rejected THEN
    RAISE EXCEPTION 'writing assignment radius trigger did not reject an outside-radius distance';
  END IF;

  rejected := FALSE;
  BEGIN
    UPDATE writer_profile SET service_radius_km=5 WHERE id=qa_writer_profile_id;
    RAISE EXCEPTION 'writer profile radius trigger allowed an active assignment outside the revised radius';
  EXCEPTION WHEN check_violation THEN
    IF position('writer service radius cannot be reduced below an active assignment distance' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
    rejected := TRUE;
  END;
  IF NOT rejected THEN
    RAISE EXCEPTION 'writer profile radius trigger did not reject an invalid radius reduction';
  END IF;
END;
$$;
ROLLBACK;
'@
  & docker exec $containerName psql -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c $writingAssignmentScheduleIntegrityWriteSql *> $null
  if ($LASTEXITCODE -ne 0) {
    throw 'Temporary database did not reject an overlapping confirmed writing assignment.'
  }

  & docker exec $containerName psql -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto;' *> $null
  if ($LASTEXITCODE -ne 0) { throw 'Temporary database could not enable the local-only bcrypt verification extension.' }
  $credentialPredicates = foreach ($account in $demoCredentials) {
    $usernameBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($account.Username))
    $passwordBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($account.Password))
    "(username=convert_from(decode('$usernameBase64','base64'),'UTF8') AND password_hash=crypt(convert_from(decode('$passwordBase64','base64'),'UTF8'),password_hash))"
  }
  $credentialSql = "SELECT count(*) FROM app_user WHERE $($credentialPredicates -join ' OR ');"
  $verifiedDemoAccountsRaw = & docker exec $containerName psql -At -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -c $credentialSql 2>$null
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($verifiedDemoAccountsRaw -join ''))) {
    throw 'Temporary database could not verify local demo-account password hashes.'
  }
  $verifiedDemoAccounts = [int](($verifiedDemoAccountsRaw -join '').Trim())
  if ($verifiedDemoAccounts -ne $demoCredentials.Count) {
    throw 'Clean local demo account hashes do not match the documented local test accounts.'
  }

  [pscustomobject]@{
    Result = 'PASS'
    InputMode = 'clean local demonstration baseline'
    ExternalMediaData = 'not asserted; static seed integrity only'
    WorkflowStructures = 'all required tables and columns present'
    MediaChannels = [int]$counts.mediaChannels
    MediaQuotes = [int]$counts.mediaQuotes
    ServiceIntakeTasks = [int]$counts.serviceIntakeTasks
    ServiceIntakePlaceholderTitles = [int]$counts.serviceIntakePlaceholderTitles
    LocalDemoAccounts = $verifiedDemoAccounts
  }
}
finally {
  Remove-TemporaryArtifacts
}
