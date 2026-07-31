param(
  [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

function Read-Utf8File {
  param([string]$RelativePath)

  $path = Join-Path $ProjectRoot $RelativePath
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Missing file: $RelativePath"
  }
  return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Evidence
  )

  $checks.Add([pscustomobject]@{
      Check = $Name
      Status = $(if ($Passed) { 'PASS' } else { 'FAIL' })
      Evidence = $Evidence
    })
}

$services = Read-Utf8File 'frontend\src\constants\services.ts'
$calendar = Read-Utf8File 'frontend\src\utils\calendar.ts'
$projectDetail = Read-Utf8File 'frontend\src\views\ProjectDetailView.vue'
$router = Read-Utf8File 'frontend\src\router\index.ts'
$channelsView = Read-Utf8File 'frontend\src\views\ChannelsView.vue'
$workflowController = Read-Utf8File 'backend\src\main\java\com\winpress\commercial\controller\WorkflowController.java'
$workflowService = Read-Utf8File 'backend\src\main\java\com\winpress\commercial\service\WorkflowService.java'
$e2e = Read-Utf8File 'frontend\tests\e2e\commercial-smoke.spec.ts'

$expectedServices = @('ONSITE_WRITING', 'MEDIA_PR', 'DIRECT_PUBLISHING', 'NEWS_CONFERENCE')
$serviceMatches = [regex]::Matches(
  $services,
  "'(ONSITE_WRITING|MEDIA_PR|DIRECT_PUBLISHING|NEWS_CONFERENCE)'"
) | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
$serviceBoundaryPassed = $serviceMatches.Count -eq 4
foreach ($service in $expectedServices) {
  if ($serviceMatches -notcontains $service) {
    $serviceBoundaryPassed = $false
  }
}
$serviceBoundaryPassed = $serviceBoundaryPassed -and
  ($services -notmatch 'WRITING_AND_PUBLISHING') -and
  ($services -notmatch 'API_INTEGRATION')
Add-Check 'Four independent service types' $serviceBoundaryPassed 'Customer order constants contain only the four approved service types'

$calendarPassed = ($calendar -match 'BEGIN:VCALENDAR') -and
  ($calendar -match 'BEGIN:VEVENT') -and
  ($projectDetail -match 'exportConferenceCalendar')
Add-Check 'Conference calendar export' $calendarPassed 'Project detail exports ICS from existing dates'

$contentPagesPassed = ($router -match "path:\s*'/methodology'") -and
  ($router -match "path:\s*'/cases'") -and
  (Test-Path -LiteralPath (Join-Path $ProjectRoot 'frontend\src\views\MethodologyView.vue')) -and
  (Test-Path -LiteralPath (Join-Path $ProjectRoot 'frontend\src\views\CasesView.vue'))
Add-Check 'Independent content routes' $contentPagesPassed 'Methodology and cases have independent routes and pages'

$advancedFilterNames = @(
  'link_type',
  'news_source',
  'entry_level',
  'special_industry',
  'weekend_policy'
)
$advancedFiltersPassed = $true
foreach ($filterName in $advancedFilterNames) {
  if (($channelsView -notmatch [regex]::Escape($filterName)) -or
    ($workflowController -notmatch [regex]::Escape($filterName))) {
    $advancedFiltersPassed = $false
  }
}
Add-Check 'Advanced direct-publishing filters' $advancedFiltersPassed 'Frontend and backend share five catalogue reference filters'

$customerBoundaryPassed = ($workflowService -match 'linkType') -and
  ($workflowService -match 'weekendPolicy') -and
  ($workflowService -notmatch 'PUBLIC_CHANNEL_FIELDS[\s\S]{0,500}costPrice') -and
  ($workflowService -notmatch 'PUBLIC_CHANNEL_FIELDS[\s\S]{0,500}supplierName')
Add-Check 'Customer catalogue field boundary' $customerBoundaryPassed 'Public fields exclude cost and supplier identity'

$automationPassed = ($e2e -match 'AxeBuilder') -and
  ($e2e -match 'wcag2aa') -and
  ($e2e -match 'BEGIN:VCALENDAR') -and
  ($e2e -match 'forbiddenKeys')
Add-Check 'Cross-viewport automated acceptance' $automationPassed 'Playwright covers WCAG, ICS and catalogue boundary checks'

$failed = @($checks | Where-Object { $_.Status -eq 'FAIL' })
$checks | Format-Table -AutoSize

if ($failed.Count -gt 0) {
  throw "Recovery traceability check failures: $($failed.Count)"
}

Write-Host "Recovery traceability checks passed: $($checks.Count)"
