param(
  [string]$Container = "winpress-coldstart-qa-20260728",
  [string]$MediaChannelsCsv,
  [string]$MediaQuotesCsv
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$databaseDir = (Resolve-Path (Join-Path $projectRoot "database")).Path
. (Join-Path $PSScriptRoot 'lib\MediaSeedInputs.ps1')
$mediaSeed = Resolve-WinPressMediaSeedInputs -ProjectRoot $projectRoot -MediaChannelsCsv $MediaChannelsCsv -MediaQuotesCsv $MediaQuotesCsv
$expectedMediaChannels = [int]$mediaSeed.ChannelCount
$expectedMediaQuotes = [int]$mediaSeed.QuoteCount
$started = $false

$mounts = @(
  @("schema.sql", "01-schema.sql"),
  @("seed.sql", "02-seed.sql"),
  @("10-brand-case-demo-data.sql", "02z-brand-case-demo-data.sql"),
  @("03-media-import.sql", "03-media-import.sql"),
  @("04-normalize-public-channel-data.sql", "04-normalize-public-channel-data.sql"),
  @("13-conference-progressive-intake.sql", "13-conference-progressive-intake.sql"),
  @("14-supplier-orders-and-conference-workbench.sql", "14-supplier-orders-and-conference-workbench.sql"),
  @("15-niumedia-reporter-and-multi-invitation.sql", "15-niumedia-reporter-and-multi-invitation.sql"),
  @("16-media-partnership-inquiry.sql", "16-media-partnership-inquiry.sql"),
  @("17-local-demo-writing-assignment.sql", "17-local-demo-writing-assignment.sql"),
  @("18-service-intake-tasks.sql", "18-service-intake-tasks.sql"),
  @("19-activity-project-linkage.sql", "19-activity-project-linkage.sql"),
  @("20-direct-manuscript-source.sql", "20-direct-manuscript-source.sql"),
  @("21-manual-media-invitation-pending-verification.sql", "21-manual-media-invitation-pending-verification.sql"),
  @("22-service-intake-title-integrity.sql", "22-service-intake-title-integrity.sql"),
  @("23-settlement-transaction-ledger.sql", "23-settlement-transaction-ledger.sql"),
  @("24-requirement-idempotency.sql", "24-requirement-idempotency.sql"),
  @("25-task-acceptance-integrity.sql", "25-task-acceptance-integrity.sql"),
  @("26-channel-quote-integrity.sql", "26-channel-quote-integrity.sql"),
  @("27-media-invitation-progress-integrity.sql", "27-media-invitation-progress-integrity.sql"),
  @("28-publish-task-terminal-integrity.sql", "28-publish-task-terminal-integrity.sql"),
  @("29-publish-plan-idempotency.sql", "29-publish-plan-idempotency.sql"),
  @("30-settlement-transaction-idempotency.sql", "30-settlement-transaction-idempotency.sql"),
  @("31-batch-quote-adjustment-idempotency.sql", "31-batch-quote-adjustment-idempotency.sql"),
  @("32-publish-plan-service-integrity.sql", "32-publish-plan-service-integrity.sql"),
  @("33-supplier-api-connections.sql", "33-supplier-api-connections.sql"),
  @("34-open-api-management.sql", "34-open-api-management.sql"),
  @("35-release-governance-and-evidence.sql", "35-release-governance-and-evidence.sql"),
  @("36-schema-migration-ledger.sql", "36-schema-migration-ledger.sql"),
  @("37-media-pr-result-integrity.sql", "37-media-pr-result-integrity.sql"),
  @("38-writing-assignment-slot-schedule-integrity.sql", "38-writing-assignment-slot-schedule-integrity.sql"),
  @("39-writing-assignment-radius-integrity.sql", "39-writing-assignment-radius-integrity.sql"),
  @("40-conference-work-item-state-integrity.sql", "40-conference-work-item-state-integrity.sql"),
  @("41-conference-media-candidate-state-integrity.sql", "41-conference-media-candidate-state-integrity.sql")
)

$existing = docker ps -a --filter "name=^/${Container}$" --format "{{.Names}}"
if ($existing) {
  throw "Container already exists: $Container"
}

$dockerArgs = @(
  "run", "-d",
  "--name", $Container,
  "-e", "POSTGRES_DB=winpress_coldstart",
  "-e", "POSTGRES_USER=winpress",
  "-e", "POSTGRES_PASSWORD=winpress_coldstart_qa",
  "--tmpfs", "/var/lib/postgresql/data"
)

foreach ($mount in $mounts) {
  $source = Join-Path $databaseDir $mount[0]
  if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
    throw "Missing cold-start input: $source"
  }
  $dockerArgs += @(
    "--mount",
    "type=bind,source=$source,target=/docker-entrypoint-initdb.d/$($mount[1]),readonly"
  )
}
$dockerArgs += @(
  "--mount",
  "type=bind,source=$($mediaSeed.ChannelsPath),target=/docker-entrypoint-initdb.d/media_channels.csv,readonly",
  "--mount",
  "type=bind,source=$($mediaSeed.QuotesPath),target=/docker-entrypoint-initdb.d/media_quotes.csv,readonly"
)
$dockerArgs += "postgres:17-alpine"

try {
  $containerId = docker @dockerArgs
  if (-not $containerId) {
    throw "Docker did not return a container id"
  }
  $started = $true

  $initialized = $false
  for ($attempt = 1; $attempt -le 120; $attempt++) {
    $ErrorActionPreference = "Continue"
    $logText = (docker logs $Container 2>&1 | Out-String)
    $ErrorActionPreference = "Stop"
    if ($logText -match "PostgreSQL init process complete") {
      $initialized = $true
      break
    }
    Start-Sleep -Seconds 2
  }
  if (-not $initialized) {
    docker logs $Container --tail 120
    throw "Cold-start initialization did not complete"
  }

  $ready = $false
  for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker exec $Container pg_isready -U winpress -d winpress_coldstart | Out-Null
    if ($LASTEXITCODE -eq 0) {
      $ready = $true
      break
    }
    Start-Sleep -Seconds 2
  }
  if (-not $ready) {
    docker logs $Container --tail 120
    throw "Cold-start database did not become ready"
  }

  $tables = [int](docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")
  $channels = [int](docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT count(*) FROM publish_channel")
  $quotes = [int](docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT count(*) FROM channel_quote")
  $activeQuotes = [int](docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT count(*) FROM channel_quote WHERE status='ACTIVE' AND valid_until>CURRENT_TIMESTAMP")
  $users = [int](docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT count(*) FROM app_user")
  $transactionLedgerReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT to_regclass('public.settlement_transaction') IS NOT NULL AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_settlement_transaction_evidence' AND conrelid='public.settlement_transaction'::regclass) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_settlement_transaction_submission_pair' AND conrelid='public.settlement_transaction'::regclass) AND to_regclass('public.uq_settlement_transaction_submission_key') IS NOT NULL"
  $taskAcceptanceReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_publish_task_status' AND conrelid='public.publish_task'::regclass) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_media_pr_invitation_status' AND conrelid='public.media_pr_invitation'::regclass) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_result_link_status' AND conrelid='public.result_link'::regclass) AND to_regclass('public.uq_result_link_task_url') IS NOT NULL"
  $publishPlanServiceReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_publish_plan_item_service_integrity' AND tgrelid='public.publish_plan_item'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_publish_plan_project_service_integrity' AND tgrelid='public.publish_plan'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_project_plan_service_integrity' AND tgrelid='public.project'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_requirement_plan_service_integrity' AND tgrelid='public.customer_requirement'::regclass AND NOT tgisinternal)"
  $openApiReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT to_regclass('public.open_api_application') IS NOT NULL AND to_regclass('public.open_api_access_key') IS NOT NULL AND to_regclass('public.open_api_request_receipt') IS NOT NULL AND to_regclass('public.open_api_access_log') IS NOT NULL AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_open_api_application_activation' AND conrelid='public.open_api_application'::regclass) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_open_api_access_key_revocation' AND conrelid='public.open_api_access_key'::regclass)"
  $releaseGovernanceReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT to_regclass('public.platform_acceptance_evidence_item') IS NOT NULL AND (SELECT count(*) FROM platform_acceptance_evidence_item WHERE required)=28 AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_platform_acceptance_gate_readiness' AND tgrelid='public.platform_acceptance_gate'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_supplier_order_fulfillment_evidence' AND tgrelid='public.supplier_order'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_legacy_combination_service_boundary' AND tgrelid='public.customer_requirement'::regclass AND NOT tgisinternal)"
  $schemaMigrationLedgerReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT to_regclass('public.schema_migration_ledger') IS NOT NULL AND EXISTS (SELECT 1 FROM schema_migration_ledger WHERE migration_version=36 AND script_name='36-schema-migration-ledger.sql' AND release_contract='winpress-v4.2.25-20260731' AND apply_mode='BASELINE' AND verification_reference='SCHEMA35_STRUCTURAL_BASELINE_20260731') AND EXISTS (SELECT 1 FROM schema_migration_ledger WHERE migration_version=37 AND script_name='37-media-pr-result-integrity.sql' AND release_contract='winpress-v4.2.26-20260731' AND apply_mode='FORWARD' AND verification_reference='MEDIA_PR_RESULT_CHAIN_INTEGRITY_20260731') AND EXISTS (SELECT 1 FROM schema_migration_ledger WHERE migration_version=38 AND script_name='38-writing-assignment-slot-schedule-integrity.sql' AND release_contract='winpress-v4.2.27-20260731' AND apply_mode='FORWARD' AND verification_reference='WRITING_ASSIGNMENT_SLOT_AND_SCHEDULE_INTEGRITY_20260731') AND EXISTS (SELECT 1 FROM schema_migration_ledger WHERE migration_version=39 AND script_name='39-writing-assignment-radius-integrity.sql' AND release_contract='winpress-v4.2.28-20260731' AND apply_mode='FORWARD' AND verification_reference='WRITING_ASSIGNMENT_RADIUS_INTEGRITY_20260731') AND EXISTS (SELECT 1 FROM schema_migration_ledger WHERE migration_version=40 AND script_name='40-conference-work-item-state-integrity.sql' AND release_contract='winpress-v4.2.29-20260731' AND apply_mode='FORWARD' AND verification_reference='CONFERENCE_WORK_ITEM_STATE_INTEGRITY_20260731') AND EXISTS (SELECT 1 FROM schema_migration_ledger WHERE migration_version=41 AND script_name='41-conference-media-candidate-state-integrity.sql' AND release_contract='winpress-v4.2.30-20260731' AND apply_mode='FORWARD' AND verification_reference='CONFERENCE_MEDIA_CANDIDATE_STATE_INTEGRITY_20260731') AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_schema_migration_ledger_append_only' AND tgrelid='public.schema_migration_ledger'::regclass AND NOT tgisinternal)"
  $writingAssignmentScheduleIntegrityReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT to_regclass('public.writing_assignment_member') IS NOT NULL AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ex_writing_assignment_member_no_overlap' AND conrelid='public.writing_assignment_member'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_extension WHERE extname='btree_gist') AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_writer_profile_service_radius_nonnegative' AND conrelid='public.writer_profile'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_writing_assignment_member_distance_nonnegative' AND conrelid='public.writing_assignment_member'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_writing_assignment_member_radius_integrity' AND tgrelid='public.writing_assignment_member'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_writer_profile_radius_integrity' AND tgrelid='public.writer_profile'::regclass AND NOT tgisinternal)"
  $conferenceWorkItemIntegrityReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_conference_work_item_completion_time' AND conrelid='public.conference_work_item'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_conference_work_item_terminal_integrity' AND tgrelid='public.conference_work_item'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_conference_project_completion_integrity' AND tgrelid='public.conference_project'::regclass AND NOT tgisinternal)"
  $conferenceMediaCandidateIntegrityReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_conference_media_candidate_status_timeline' AND conrelid='public.conference_media_candidate'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_conference_media_candidate_contact_time_order' AND conrelid='public.conference_media_candidate'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_conference_media_candidate_outcome_note' AND conrelid='public.conference_media_candidate'::regclass AND convalidated) AND EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_conference_media_candidate_state_integrity' AND tgrelid='public.conference_media_candidate'::regclass AND NOT tgisinternal)"
  $mediaResultGuardReady = docker exec $Container psql -U winpress -d winpress_coldstart -tAc "SELECT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_publish_task_terminal_integrity' AND tgrelid='public.publish_task'::regclass AND NOT tgisinternal) AND EXISTS (SELECT 1 FROM pg_proc proc JOIN pg_namespace namespace ON namespace.oid=proc.pronamespace WHERE namespace.nspname='public' AND proc.proname='enforce_publish_task_terminal_integrity' AND proc.prosrc LIKE '%completed media tasks require a recorded invitation and reported outcome%')"

  $mediaResultGuardSql = @'
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
    RAISE EXCEPTION 'cold-start media result integrity fixture is missing';
  END IF;

  INSERT INTO result_link (
    result_no, project_id, publish_task_id, channel_name, title, url, status
  )
  SELECT
    'QA-MEDIA-GUARD-' || task.id,
    task.project_id,
    task.id,
    invitation.media_name,
    'Synthetic migration guard check',
    'https://example.invalid/media-result-guard/' || task.id,
    'VERIFIED'
  FROM publish_task task
  JOIN media_pr_invitation invitation ON invitation.publish_task_id=task.id
  WHERE task.id=target_task_id;

  BEGIN
    UPDATE publish_task SET status='COMPLETED' WHERE id=target_task_id;
    RAISE EXCEPTION 'cold-start media result integrity guard did not reject completion';
  EXCEPTION WHEN OTHERS THEN
    IF position('completed media tasks require a recorded invitation and reported outcome' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
  END;
END;
$$;
ROLLBACK;
'@
  docker exec $Container psql -v ON_ERROR_STOP=1 -U winpress -d winpress_coldstart -c $mediaResultGuardSql | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw 'Cold-start media-result database guard rejection test failed.'
  }

  $conferenceWorkItemIntegrityGuardSql = @'
BEGIN;
DO $$
DECLARE
  completed_item_id BIGINT;
  open_item_id BIGINT;
  open_project_id BIGINT;
BEGIN
  SELECT id
  INTO completed_item_id
  FROM conference_work_item
  WHERE status='COMPLETED'
  ORDER BY id
  LIMIT 1;
  IF completed_item_id IS NULL THEN
    RAISE EXCEPTION 'cold-start completed conference work-item fixture is missing';
  END IF;

  BEGIN
    UPDATE conference_work_item SET status='IN_PROGRESS' WHERE id=completed_item_id;
    RAISE EXCEPTION 'cold-start conference work-item terminal guard did not reject reopening';
  EXCEPTION WHEN check_violation THEN
    IF position('completed conference work item cannot be reopened' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
  END;

  SELECT id
  INTO open_item_id
  FROM conference_work_item
  WHERE status='PENDING'
  ORDER BY id
  LIMIT 1;
  IF open_item_id IS NULL THEN
    RAISE EXCEPTION 'cold-start pending conference work-item fixture is missing';
  END IF;

  BEGIN
    UPDATE conference_work_item
    SET status='COMPLETED', completed_at=NULL
    WHERE id=open_item_id;
    RAISE EXCEPTION 'cold-start conference completion-time constraint did not reject a missing timestamp';
  EXCEPTION WHEN check_violation THEN
    IF position('ck_conference_work_item_completion_time' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
  END;

  SELECT cp.id
  INTO open_project_id
  FROM conference_project cp
  WHERE EXISTS (
    SELECT 1
    FROM conference_work_item item
    WHERE item.conference_project_id=cp.id
      AND item.status<>'COMPLETED'
  )
  ORDER BY cp.id
  LIMIT 1;
  IF open_project_id IS NULL THEN
    RAISE EXCEPTION 'cold-start conference project fixture with unfinished items is missing';
  END IF;

  BEGIN
    UPDATE conference_project SET status='COMPLETED' WHERE id=open_project_id;
    RAISE EXCEPTION 'cold-start conference project completion guard did not reject unfinished work items';
  EXCEPTION WHEN check_violation THEN
    IF position('completed conference project requires every work item to be completed' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
  END;
END;
$$;
ROLLBACK;
'@
  docker exec $Container psql -v ON_ERROR_STOP=1 -U winpress -d winpress_coldstart -c $conferenceWorkItemIntegrityGuardSql | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw 'Cold-start conference work-item database guard rejection test failed.'
  }

  $conferenceCandidateIntegrityGuardSql = @'
BEGIN;
DO $$
DECLARE
  candidate_id BIGINT;
BEGIN
  SELECT id
  INTO candidate_id
  FROM conference_media_candidate
  WHERE status='READY_TO_INVITE'
  ORDER BY id
  LIMIT 1;
  IF candidate_id IS NULL THEN
    RAISE EXCEPTION 'cold-start pre-invitation candidate fixture is missing';
  END IF;

  BEGIN
    UPDATE conference_media_candidate
    SET status='ATTENDING', invited_at=CURRENT_TIMESTAMP, responded_at=CURRENT_TIMESTAMP,
        note='Synthetic cold-start guard test'
    WHERE id=candidate_id;
    RAISE EXCEPTION 'cold-start candidate-state guard did not reject a skipped invitation timeline';
  EXCEPTION WHEN check_violation THEN
    IF position('conference media candidate status transition is not allowed' IN SQLERRM)=0 THEN
      RAISE;
    END IF;
  END;
END;
$$;
ROLLBACK;
'@
  docker exec $Container psql -v ON_ERROR_STOP=1 -U winpress -d winpress_coldstart -c $conferenceCandidateIntegrityGuardSql | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw 'Cold-start conference media-candidate database guard rejection test failed.'
  }

  $mediaSeedCountsMatch = if ($mediaSeed.Mode -eq 'PUBLIC_HEADERS_ONLY') {
    $channels -ge 4 -and $quotes -ge 3
  } else {
    $channels -eq $expectedMediaChannels -and $quotes -eq $expectedMediaQuotes
  }
  if ($tables -lt 47 -or -not $mediaSeedCountsMatch -or $users -lt 3 -or $transactionLedgerReady -ne 't' -or $taskAcceptanceReady -ne 't' -or $publishPlanServiceReady -ne 't' -or $openApiReady -ne 't' -or $releaseGovernanceReady -ne 't' -or $schemaMigrationLedgerReady -ne 't' -or $writingAssignmentScheduleIntegrityReady -ne 't' -or $conferenceWorkItemIntegrityReady -ne 't' -or $conferenceMediaCandidateIntegrityReady -ne 't' -or $mediaResultGuardReady -ne 't') {
    throw "Cold-start assertions failed: media_seed_mode=$($mediaSeed.Mode) expected_channels=$expectedMediaChannels expected_quotes=$expectedMediaQuotes tables=$tables channels=$channels quotes=$quotes active_quotes=$activeQuotes users=$users transaction_ledger=$transactionLedgerReady task_acceptance=$taskAcceptanceReady publish_plan_service=$publishPlanServiceReady open_api=$openApiReady release_governance=$releaseGovernanceReady migration_ledger=$schemaMigrationLedgerReady writing_assignment_schedule=$writingAssignmentScheduleIntegrityReady conference_work_item=$conferenceWorkItemIntegrityReady conference_media_candidate=$conferenceMediaCandidateIntegrityReady media_result_guard=$mediaResultGuardReady"
  }
  Write-Output "cold-start-ok media_seed_mode=$($mediaSeed.Mode) input_channels=$expectedMediaChannels input_quotes=$expectedMediaQuotes tables=$tables channels=$channels quotes=$quotes active_quotes=$activeQuotes users=$users transaction_ledger=ready task_acceptance=ready publish_plan_service=ready open_api=ready release_governance=ready migration_ledger=ready writing_assignment_schedule=ready conference_work_item=ready conference_media_candidate=ready media_result_guard=ready external_media_data=not_asserted"
}
finally {
  if ($started) {
    docker rm -f $Container | Out-Null
  }
}
