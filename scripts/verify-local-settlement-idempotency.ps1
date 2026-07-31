[CmdletBinding()]
param(
  [string]$ApiBaseUrl = 'http://127.0.0.1:8192/api/v1',
  [string]$DatabaseContainer = 'winpress-commercial-postgres',
  [string]$DatabaseName = 'winpress_commercial',
  [string]$DatabaseUser = 'winpress'
)

<#
Local-only regression for settlement-transaction idempotency.

The script creates one isolated settlement in the local demonstration database, proves that a
missing request key is rejected, repeats the same request with one key, and proves that only one
financial fact is booked. It removes the settlement, transaction, audit log, and local sessions
before returning. Credentials and bearer tokens are never printed.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$accountDocument = Join-Path $projectRoot 'docs\TEST-ACCOUNTS.md'
. (Join-Path $PSScriptRoot 'lib\LocalTestAccounts.ps1')

$apiUri = [Uri]$ApiBaseUrl
if ($apiUri.Scheme -ne 'http' -or $apiUri.Host -notin @('127.0.0.1', 'localhost')) {
  throw 'Settlement idempotency verification is restricted to a loopback HTTP address.'
}
if ($DatabaseContainer -ne 'winpress-commercial-postgres') {
  throw 'Settlement idempotency verification is restricted to the local WinPress database container.'
}

$checks = New-Object System.Collections.Generic.List[object]
$sessionTokens = New-Object System.Collections.Generic.List[string]
$settlementId = $null
$settlementNo = 'SET-QA-IDEM-' + (Get-Date -Format 'yyyyMMddHHmmss') + '-' +
  [Guid]::NewGuid().ToString('N').Substring(0, 8).ToUpperInvariant()

function Add-Check {
  param([string]$Name, [bool]$Passed, [string]$Detail)
  $checks.Add([PSCustomObject]@{
    Check = $Name
    Result = $(if ($Passed) { 'PASS' } else { 'FAIL' })
    Detail = $Detail
  })
  if (-not $Passed) {
    throw "Settlement idempotency check failed: $Name ($Detail)"
  }
}

function Invoke-LocalSql {
  param([Parameter(Mandatory = $true)][string]$Sql)
  $output = @(
    & docker exec $DatabaseContainer psql -qAt -v ON_ERROR_STOP=1 `
      -U $DatabaseUser -d $DatabaseName -c $Sql 2>&1
  )
  if ($LASTEXITCODE -ne 0) {
    throw 'The local settlement-idempotency database check failed.'
  }
  return ($output -join "`n").Trim()
}

function Invoke-LocalHttp {
  param(
    [ValidateSet('POST')][string]$Method = 'POST',
    [Parameter(Mandatory = $true)][string]$Path,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )
  $client = [System.Net.Http.HttpClient]::new()
  $request = $null
  $response = $null
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $request = [System.Net.Http.HttpRequestMessage]::new(
      [System.Net.Http.HttpMethod]::new($Method),
      "$($ApiBaseUrl.TrimEnd('/'))$Path"
    )
    foreach ($header in $Headers.GetEnumerator()) {
      [void]$request.Headers.TryAddWithoutValidation(
        [string]$header.Key,
        [string]$header.Value
      )
    }
    if ($null -ne $Body) {
      $json = $Body | ConvertTo-Json -Depth 8 -Compress
      $request.Content = [System.Net.Http.StringContent]::new(
        $json,
        [System.Text.Encoding]::UTF8,
        'application/json'
      )
    }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $contentBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    return [PSCustomObject]@{
      Status = [int]$response.StatusCode
      Content = [System.Text.Encoding]::UTF8.GetString($contentBytes)
    }
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $request) { $request.Dispose() }
    $client.Dispose()
  }
}

try {
  $containerStatus = (& docker inspect -f '{{.State.Status}}' $DatabaseContainer 2>$null).Trim()
  Add-Check -Name 'local database container' -Passed ($containerStatus -eq 'running') `
    -Detail 'the expected local database container is running'

  $adminToken = $null
  $localAccounts = @(Get-WinPressLocalDemoCredentials -AccountDocument $accountDocument)
  foreach ($account in @($localAccounts | Where-Object { $_.Group -eq 'QUICK_LOGIN' })) {
    $login = Invoke-LocalHttp -Path '/auth/login' -Body @{
      username = $account.Username
      password = $account.Password
    }
    if ($login.Status -ne 200 -or [string]::IsNullOrWhiteSpace($login.Content)) {
      continue
    }
    $loginData = $login.Content | ConvertFrom-Json
    $token = [string]$loginData.data.token
    if (-not [string]::IsNullOrWhiteSpace($token)) {
      $sessionTokens.Add($token)
    }
    if ($loginData.data.user.role -eq 'PLATFORM_ADMIN') {
      $adminToken = $token
      break
    }
  }
  Add-Check -Name 'platform administrator session' `
    -Passed (-not [string]::IsNullOrWhiteSpace($adminToken)) `
    -Detail 'an authorised local session is available'

  $safeSettlementNo = $settlementNo.Replace("'", "''")
  $createSettlementSql = @"
WITH candidate AS (
  SELECT p.id AS project_id, p.organization_id
  FROM project p
  JOIN customer_requirement requirement ON requirement.id=p.requirement_id
  WHERE requirement.requested_service IN (
    'ONSITE_WRITING', 'MEDIA_PR', 'DIRECT_PUBLISHING', 'NEWS_CONFERENCE'
  )
  ORDER BY p.id
  LIMIT 1
)
INSERT INTO settlement_order
  (settlement_no, project_id, organization_id, amount, paid_amount,
   currency, due_at, status)
SELECT '$safeSettlementNo', project_id, organization_id, 12.34, 0,
       'CNY', CURRENT_TIMESTAMP + INTERVAL '7 days', 'CONFIRMED'
FROM candidate
RETURNING id;
"@
  $settlementIdText = Invoke-LocalSql -Sql $createSettlementSql
  $parsedSettlementId = 0L
  Add-Check -Name 'isolated settlement setup' `
    -Passed ([long]::TryParse($settlementIdText, [ref]$parsedSettlementId)) `
    -Detail 'one temporary local settlement was created'
  $settlementId = $parsedSettlementId

  $occurredAt = [DateTimeOffset]::Now.AddMinutes(-1).ToString('o')
  $payload = [ordered]@{
    transactionType = 'PAYMENT'
    amount = 12.34
    occurredAt = $occurredAt
    referenceNo = 'QA-IDEMPOTENCY-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    customerNote = '本机财务防重回归凭据'
    internalNote = $null
  }
  $baseHeaders = @{ Authorization = "Bearer $adminToken" }

  $missingKey = Invoke-LocalHttp `
    -Path "/admin/settlements/$settlementId/transactions" `
    -Headers $baseHeaders `
    -Body $payload
  Add-Check -Name 'missing idempotency key' -Passed ($missingKey.Status -eq 400) `
    -Detail "HTTP $($missingKey.Status); no financial fact accepted"

  $idempotencyKey = [Guid]::NewGuid().ToString()
  $transactionHeaders = @{
    Authorization = "Bearer $adminToken"
    'Idempotency-Key' = $idempotencyKey
  }
  $first = Invoke-LocalHttp `
    -Path "/admin/settlements/$settlementId/transactions" `
    -Headers $transactionHeaders `
    -Body $payload
  Add-Check -Name 'first financial submission' -Passed ($first.Status -eq 200) `
    -Detail "HTTP $($first.Status)"
  $firstData = $first.Content | ConvertFrom-Json

  $retry = Invoke-LocalHttp `
    -Path "/admin/settlements/$settlementId/transactions" `
    -Headers $transactionHeaders `
    -Body $payload
  Add-Check -Name 'same request retry' -Passed ($retry.Status -eq 200) `
    -Detail "HTTP $($retry.Status); original fact returned"
  $retryData = $retry.Content | ConvertFrom-Json
  Add-Check -Name 'retry identity' `
    -Passed (
      -not [string]::IsNullOrWhiteSpace([string]$firstData.data.transactionNo) -and
      [string]$firstData.data.transactionNo -eq [string]$retryData.data.transactionNo
    ) `
    -Detail 'the retry returned the original transaction number'

  $changedPayload = [ordered]@{
    transactionType = 'PAYMENT'
    amount = 12.35
    occurredAt = $occurredAt
    referenceNo = $payload.referenceNo
    customerNote = $payload.customerNote
    internalNote = $null
  }
  $reusedKey = Invoke-LocalHttp `
    -Path "/admin/settlements/$settlementId/transactions" `
    -Headers $transactionHeaders `
    -Body $changedPayload
  Add-Check -Name 'request key content binding' -Passed ($reusedKey.Status -eq 409) `
    -Detail "HTTP $($reusedKey.Status); a key cannot be reused for another amount"

  $ledgerSql = @"
SELECT json_build_object(
  'transactionCount', (
    SELECT count(*) FROM settlement_transaction
    WHERE settlement_order_id=$settlementId
  ),
  'paidAmount', paid_amount,
  'outstandingAmount', GREATEST(amount - paid_amount, 0),
  'pairedRequestState', NOT EXISTS (
    SELECT 1 FROM settlement_transaction
    WHERE settlement_order_id=$settlementId
      AND (submission_key IS NULL) <> (submission_hash IS NULL)
  )
)::text
FROM settlement_order
WHERE id=$settlementId AND settlement_no='$safeSettlementNo';
"@
  $ledger = (Invoke-LocalSql -Sql $ledgerSql) | ConvertFrom-Json
  Add-Check -Name 'single booked financial fact' `
    -Passed (
      [long]$ledger.transactionCount -eq 1 -and
      [decimal]$ledger.paidAmount -eq [decimal]12.34 -and
      [decimal]$ledger.outstandingAmount -eq [decimal]0 -and
      $ledger.pairedRequestState -eq $true
    ) `
    -Detail 'one transaction, CNY 12.34 paid, CNY 0.00 outstanding'

  $checks | Format-Table -AutoSize
  Write-Output 'Local settlement-transaction idempotency verification passed.'
}
finally {
  if (-not [string]::IsNullOrWhiteSpace($settlementNo)) {
    $cleanupSql = @"
BEGIN;
DELETE FROM operation_log
WHERE action='CREATE_SETTLEMENT_TRANSACTION'
  AND target_type='SETTLEMENT_TRANSACTION'
  AND target_id IN (
    SELECT id::text FROM settlement_transaction
    WHERE settlement_order_id IN (
      SELECT id FROM settlement_order
      WHERE settlement_no='$($settlementNo.Replace("'", "''"))'
    )
  );
DELETE FROM settlement_transaction
WHERE settlement_order_id IN (
  SELECT id FROM settlement_order
  WHERE settlement_no='$($settlementNo.Replace("'", "''"))'
);
DELETE FROM settlement_order
WHERE settlement_no='$($settlementNo.Replace("'", "''"))';
COMMIT;
"@
    try { [void](Invoke-LocalSql -Sql $cleanupSql) } catch { }
  }
  foreach ($token in $sessionTokens) {
    try {
      [void](Invoke-LocalHttp -Path '/auth/logout' -Headers @{ Authorization = "Bearer $token" })
    } catch { }
  }
}
