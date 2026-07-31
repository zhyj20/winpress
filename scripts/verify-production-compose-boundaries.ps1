[CmdletBinding()]
param(
  [switch]$SkipComposeConfig
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composePath = Join-Path $projectRoot 'docker-compose.production.yml'
$envExamplePath = Join-Path $projectRoot '.env.example'
$deploymentDocPath = Join-Path $projectRoot 'docs\DEPLOYMENT.md'
$migrationDocPath = Join-Path $projectRoot 'docs\MIGRATION.md'
$databaseDocPath = Join-Path $projectRoot 'docs\DATABASE.md'
$handoffDocPath = Join-Path $projectRoot 'docs\HANDOFF.md'
$auditDocPath = Join-Path $projectRoot 'docs\WINPRESS-GLOBAL-PRODUCT-CODE-AUDIT-20260728.md'
$readmePath = Join-Path $projectRoot 'README.md'
$productionPreflightPath = Join-Path $projectRoot 'scripts\verify-production-readiness.ps1'
$boundaryMigrationPaths = @(
  (Join-Path $projectRoot 'database\14-supplier-orders-and-conference-workbench.sql'),
  (Join-Path $projectRoot 'database\21-manual-media-invitation-pending-verification.sql'),
  (Join-Path $projectRoot 'database\22-service-intake-title-integrity.sql'),
  (Join-Path $projectRoot 'database\23-settlement-transaction-ledger.sql'),
  (Join-Path $projectRoot 'database\24-requirement-idempotency.sql'),
  (Join-Path $projectRoot 'database\25-task-acceptance-integrity.sql'),
  (Join-Path $projectRoot 'database\26-channel-quote-integrity.sql'),
  (Join-Path $projectRoot 'database\27-media-invitation-progress-integrity.sql'),
  (Join-Path $projectRoot 'database\28-publish-task-terminal-integrity.sql'),
  (Join-Path $projectRoot 'database\29-publish-plan-idempotency.sql'),
  (Join-Path $projectRoot 'database\30-settlement-transaction-idempotency.sql'),
  (Join-Path $projectRoot 'database\31-batch-quote-adjustment-idempotency.sql'),
  (Join-Path $projectRoot 'database\32-publish-plan-service-integrity.sql'),
  (Join-Path $projectRoot 'database\33-supplier-api-connections.sql'),
  (Join-Path $projectRoot 'database\34-open-api-management.sql'),
  (Join-Path $projectRoot 'database\35-release-governance-and-evidence.sql'),
  (Join-Path $projectRoot 'database\36-schema-migration-ledger.sql'),
  (Join-Path $projectRoot 'database\37-media-pr-result-integrity.sql'),
  (Join-Path $projectRoot 'database\38-writing-assignment-slot-schedule-integrity.sql'),
  (Join-Path $projectRoot 'database\39-writing-assignment-radius-integrity.sql'),
  (Join-Path $projectRoot 'database\40-conference-work-item-state-integrity.sql'),
  (Join-Path $projectRoot 'database\41-conference-media-candidate-state-integrity.sql')
)

foreach ($path in @(
  $composePath,
  $envExamplePath,
  $deploymentDocPath,
  $migrationDocPath,
  $databaseDocPath,
  $handoffDocPath,
  $auditDocPath,
  $readmePath,
  $productionPreflightPath
) + $boundaryMigrationPaths) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Required production-boundary input is missing: $path"
  }
}

$composeText = Get-Content -LiteralPath $composePath -Raw -Encoding utf8
$deploymentText = Get-Content -LiteralPath $deploymentDocPath -Raw -Encoding utf8
$migrationText = Get-Content -LiteralPath $migrationDocPath -Raw -Encoding utf8
$databaseText = Get-Content -LiteralPath $databaseDocPath -Raw -Encoding utf8
$handoffText = Get-Content -LiteralPath $handoffDocPath -Raw -Encoding utf8
$auditText = Get-Content -LiteralPath $auditDocPath -Raw -Encoding utf8
$readmeText = Get-Content -LiteralPath $readmePath -Raw -Encoding utf8
$requiredProductionInputs = @(
  'database/schema.sql',
  'database/02-production-access-control.sql',
  'database/13-conference-progressive-intake.sql',
  'database/14-supplier-orders-and-conference-workbench.sql',
  'database/15-niumedia-reporter-and-multi-invitation.sql',
  'database/16-media-partnership-inquiry.sql',
  'database/18-service-intake-tasks.sql',
  'database/19-activity-project-linkage.sql',
  'database/20-direct-manuscript-source.sql',
  'database/21-manual-media-invitation-pending-verification.sql',
  'database/22-service-intake-title-integrity.sql',
  'database/23-settlement-transaction-ledger.sql',
  'database/24-requirement-idempotency.sql',
  'database/25-task-acceptance-integrity.sql',
  'database/26-channel-quote-integrity.sql',
  'database/27-media-invitation-progress-integrity.sql',
  'database/28-publish-task-terminal-integrity.sql',
  'database/29-publish-plan-idempotency.sql',
  'database/30-settlement-transaction-idempotency.sql',
  'database/31-batch-quote-adjustment-idempotency.sql',
  'database/32-publish-plan-service-integrity.sql',
  'database/33-supplier-api-connections.sql',
  'database/34-open-api-management.sql',
  'database/35-release-governance-and-evidence.sql',
  'database/36-schema-migration-ledger.sql',
  'database/37-media-pr-result-integrity.sql',
  'database/38-writing-assignment-slot-schedule-integrity.sql',
  'database/39-writing-assignment-radius-integrity.sql',
  'database/40-conference-work-item-state-integrity.sql',
  'database/41-conference-media-candidate-state-integrity.sql'
)
$forbiddenProductionInputs = @(
  'database/seed.sql',
  'database/10-brand-case-demo-data.sql',
  'database/17-local-demo-writing-assignment.sql',
  'database/03-media-import.sql',
  'database/04-normalize-public-channel-data.sql',
  'database/media_channels.csv',
  'database/media_quotes.csv'
)

foreach ($input in $requiredProductionInputs) {
  if (-not $composeText.Contains($input)) {
    throw "Production Compose is missing required schema input: $input"
  }
}
foreach ($input in $forbiddenProductionInputs) {
  if ($composeText.Contains($input)) {
    throw "Production Compose must not auto-load unverified or local-only input: $input"
  }
}

if (-not $composeText.Contains("VITE_LOCAL_DEMO: 'false'")) {
  throw 'Production Compose must compile the frontend without the local demonstration marker.'
}
if (-not $composeText.Contains("WINPRESS_API_DOCS_ENABLED: 'false'")) {
  throw 'Production Compose must explicitly disable Swagger and OpenAPI documentation.'
}
if ($composeText -match 'WINPRESS_API_DOCS_ENABLED:\s*(true|''true''|\"true\")') {
  throw 'Production Compose must never publish Swagger or OpenAPI documentation.'
}
if (-not $composeText.Contains('WINPRESS_STORAGE_MAX_FILE_BYTES: ${WINPRESS_STORAGE_MAX_FILE_BYTES:?Set WINPRESS_STORAGE_MAX_FILE_BYTES in .env}')) {
  throw 'Production Compose must require an explicit production attachment-size limit.'
}
foreach ($requiredFederationBoundary in @(
  'WINPRESS_FEDERATION_ENABLED: ${WINPRESS_FEDERATION_ENABLED:-false}',
  'WINPRESS_FEDERATION_MIGRATE_ON_START: ${WINPRESS_FEDERATION_MIGRATE_ON_START:-false}',
  'WINPRESS_FEDERATION_SHARED_SECRET: ${WINPRESS_FEDERATION_SHARED_SECRET:-}',
  'WINPRESS_FEDERATION_MAX_REQUESTS_PER_MINUTE: ${WINPRESS_FEDERATION_MAX_REQUESTS_PER_MINUTE:-120}'
)) {
  if (-not $composeText.Contains($requiredFederationBoundary)) {
    throw "Production Compose is missing a safe federation boundary: $requiredFederationBoundary"
  }
}
if ($composeText -match 'WINPRESS_FEDERATION_(ENABLED|MIGRATE_ON_START):\s*(true|''true''|\"true\")') {
  throw 'Production Compose must not enable GEO federation or its migration by default.'
}

foreach ($requiredDocFragment in @(
  'database/21-manual-media-invitation-pending-verification.sql',
  'database/22-service-intake-title-integrity.sql',
  'database/23-settlement-transaction-ledger.sql',
  'database/24-requirement-idempotency.sql',
  'database/25-task-acceptance-integrity.sql',
  'database/26-channel-quote-integrity.sql',
  'database/27-media-invitation-progress-integrity.sql',
  'database/28-publish-task-terminal-integrity.sql',
  'database/29-publish-plan-idempotency.sql',
  'database/30-settlement-transaction-idempotency.sql',
  'database/31-batch-quote-adjustment-idempotency.sql',
  'database/32-publish-plan-service-integrity.sql',
  'database/33-supplier-api-connections.sql',
  'database/34-open-api-management.sql',
  'database/35-release-governance-and-evidence.sql',
  'database/36-schema-migration-ledger.sql',
  'database/37-media-pr-result-integrity.sql',
  'database/38-writing-assignment-slot-schedule-integrity.sql',
  'database/39-writing-assignment-radius-integrity.sql',
  'database/40-conference-work-item-state-integrity.sql',
  'database/41-conference-media-candidate-state-integrity.sql',
  'scripts/verify-production-readiness.ps1 -EnvFile .env'
)) {
  if (-not $deploymentText.Contains($requiredDocFragment)) {
    throw "Production deployment documentation is missing: $requiredDocFragment"
  }
}
if (-not $readmeText.Contains('database/41-conference-media-candidate-state-integrity.sql')) {
  throw 'README migration list is missing migration 41.'
}
$lineBreakPattern = "\r?\n"
$readmeLocalLine = @($readmeText -split $lineBreakPattern | Where-Object {
  $_.Contains('seed.sql') -and $_.Contains('17-local-demo-writing-assignment.sql')
} | Select-Object -First 1)
$readmeProductionLine = @($readmeText -split $lineBreakPattern | Where-Object {
  $_.Contains('02-production-access-control.sql') -and $_.Contains('Swagger') -and $_.Contains('CORS')
} | Select-Object -First 1)
$deploymentLocalLine = @($deploymentText -split $lineBreakPattern | Where-Object {
  $_.Contains('10-brand-case-demo-data.sql') -and $_.Contains('17-local-demo-writing-assignment.sql')
} | Select-Object -First 1)
$deploymentSnapshotLine = @($deploymentText -split $lineBreakPattern | Where-Object {
  $_.Contains('winpress_full.sql') -and $_.Contains('P0/P1')
} | Select-Object -First 1)
$migrationBaselineLine = @($migrationText -split $lineBreakPattern | Where-Object {
  $_.Contains('schema.sql') -and $_.Contains('winpress_full.dump')
} | Select-Object -First 1)
$migrationComposeLine = @($migrationText -split $lineBreakPattern | Where-Object {
  $_.Contains('docker-compose.local-demo.yml') -and $_.Contains('docker-compose.production.yml')
} | Select-Object -First 1)
$databaseBaselineLine = @($databaseText -split $lineBreakPattern | Where-Object {
  $_.Contains('schema.sql') -and $_.Contains('41')
} | Select-Object -First 1)
$handoffBaselineLine = @($handoffText -split $lineBreakPattern | Where-Object {
  $_.Contains('10-brand-case-demo-data.sql') -and $_.Contains('docs/MIGRATION.md')
} | Select-Object -First 1)
foreach ($contractLine in @(
  @{ Name = 'README local migration range'; Value = $readmeLocalLine; Forbidden = '31' },
  @{ Name = 'README production migration range'; Value = $readmeProductionLine; Forbidden = '31' },
  @{ Name = 'deployment local migration range'; Value = $deploymentLocalLine; Forbidden = '31' },
  @{ Name = 'deployment snapshot migration range'; Value = $deploymentSnapshotLine; Forbidden = '31' },
  @{ Name = 'migration baseline range'; Value = $migrationBaselineLine; Forbidden = '31' },
  @{ Name = 'migration Compose range'; Value = $migrationComposeLine; Forbidden = '31' },
  @{ Name = 'database baseline range'; Value = $databaseBaselineLine; Forbidden = '31' },
  @{ Name = 'handoff migration range'; Value = $handoffBaselineLine; Forbidden = '31' }
)) {
  if (
    $contractLine.Value.Count -ne 1 -or
    -not $contractLine.Value[0].Contains('41') -or
    $contractLine.Value[0].Contains($contractLine.Forbidden)
  ) {
    throw "$($contractLine.Name) is not aligned with schema 41."
  }
}
if (-not $deploymentText.Contains('schemaVersion=41')) {
  throw 'Deployment documentation does not require the current schemaVersion=41 health gate.'
}

foreach ($migrationPath in $boundaryMigrationPaths) {
  $migrationText = Get-Content -LiteralPath $migrationPath -Raw -Encoding utf8
  if ($migrationText -match 'SUP-DEMO|本机演示直编服务商|SUPMAP-DEMO') {
    throw 'Production migration still contains a local demonstration supplier fixture.'
  }
}

if (-not $SkipComposeConfig) {
  & docker compose --env-file $envExamplePath -f $composePath config --quiet
  if ($LASTEXITCODE -ne 0) {
    throw 'Production Compose static configuration validation failed.'
  }
}

Write-Output 'Production Compose boundary verification passed: no demo fixture or unverified media catalogue is auto-loaded.'
