[CmdletBinding()]
param(
  [string]$ApiBaseUrl = 'http://127.0.0.1:8192/api/v1',
  [string]$DatabaseContainer = 'winpress-commercial-postgres',
  [string]$DatabaseName = 'winpress_commercial',
  [string]$DatabaseUser = 'winpress'
)

<#
Local-only regression for batch quote-adjustment idempotency.

The script creates two isolated direct-publishing channels with known prices, proves that a missing
request key is rejected, repeats one successful percentage adjustment, and verifies that the retry
returns the original quotes without compounding either customer price. It removes every QA record
and local session before returning. Credentials and bearer tokens are never printed.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$accountDocument = Join-Path $projectRoot 'docs\TEST-ACCOUNTS.md'
. (Join-Path $PSScriptRoot 'lib\LocalTestAccounts.ps1')

$apiUri = [Uri]$ApiBaseUrl
if ($apiUri.Scheme -ne 'http' -or $apiUri.Host -notin @('127.0.0.1', 'localhost')) {
  throw 'Batch quote-adjustment verification is restricted to a loopback HTTP address.'
}
if ($DatabaseContainer -ne 'winpress-commercial-postgres') {
  throw 'Batch quote-adjustment verification is restricted to the local WinPress database container.'
}

$checks = New-Object System.Collections.Generic.List[object]
$sessionTokens = New-Object System.Collections.Generic.List[string]
$runId = [Guid]::NewGuid().ToString('N').Substring(0, 16).ToUpperInvariant()
$channelPrefix = "QA-BATCH-$runId"
$idempotencyKey = [Guid]::NewGuid().ToString()
$firstChannelId = $null
$secondChannelId = $null

function Add-Check {
  param([string]$Name, [bool]$Passed, [string]$Detail)
  $checks.Add([PSCustomObject]@{
    Check = $Name
    Result = $(if ($Passed) { 'PASS' } else { 'FAIL' })
    Detail = $Detail
  })
  if (-not $Passed) {
    throw "Batch quote-adjustment check failed: $Name ($Detail)"
  }
}

function Invoke-LocalSql {
  param([Parameter(Mandatory = $true)][string]$Sql)
  $output = @(
    & docker exec $DatabaseContainer psql -qAt -v ON_ERROR_STOP=1 `
      -U $DatabaseUser -d $DatabaseName -c $Sql 2>&1
  )
  if ($LASTEXITCODE -ne 0) {
    throw 'The local batch quote-adjustment database check failed.'
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

  $safePrefix = $channelPrefix.Replace("'", "''")
  $setupSql = @"
WITH first_channel AS (
  INSERT INTO publish_channel
    (channel_no, channel_name, channel_type, category, region, publish_form,
     expected_days, public_notes, source_type, source_ref, last_verified_at, status)
  VALUES
    ('$safePrefix-A', 'QA batch pricing channel A', 'DIRECT_PUBLISHING', 'QA',
     'QA', 'ARTICLE', 1, 'Isolated local verification only', 'INTERNAL',
     '$safePrefix-A', CURRENT_TIMESTAMP, 'ACTIVE')
  RETURNING id
),
second_channel AS (
  INSERT INTO publish_channel
    (channel_no, channel_name, channel_type, category, region, publish_form,
     expected_days, public_notes, source_type, source_ref, last_verified_at, status)
  VALUES
    ('$safePrefix-B', 'QA batch pricing channel B', 'DIRECT_PUBLISHING', 'QA',
     'QA', 'ARTICLE', 1, 'Isolated local verification only', 'INTERNAL',
     '$safePrefix-B', CURRENT_TIMESTAMP, 'ACTIVE')
  RETURNING id
),
first_quote AS (
  INSERT INTO channel_quote
    (quote_no, channel_id, customer_tier, cost_price, customer_price, currency,
     valid_from, valid_until, public_terms, status)
  SELECT '$safePrefix-QA', id, 'STANDARD', 50.00, 100.00, 'CNY',
         CURRENT_TIMESTAMP - INTERVAL '1 minute',
         CURRENT_TIMESTAMP + INTERVAL '30 days',
         'QA verification terms', 'ACTIVE'
  FROM first_channel
  RETURNING channel_id
),
second_quote AS (
  INSERT INTO channel_quote
    (quote_no, channel_id, customer_tier, cost_price, customer_price, currency,
     valid_from, valid_until, public_terms, status)
  SELECT '$safePrefix-QB', id, 'STANDARD', 60.00, 120.00, 'CNY',
         CURRENT_TIMESTAMP - INTERVAL '1 minute',
         CURRENT_TIMESTAMP + INTERVAL '30 days',
         'QA verification terms', 'ACTIVE'
  FROM second_channel
  RETURNING channel_id
)
SELECT json_build_object(
  'firstChannelId', (SELECT channel_id FROM first_quote),
  'secondChannelId', (SELECT channel_id FROM second_quote)
)::text;
"@
  $setup = (Invoke-LocalSql -Sql $setupSql) | ConvertFrom-Json
  $firstChannelId = [long]$setup.firstChannelId
  $secondChannelId = [long]$setup.secondChannelId
  Add-Check -Name 'isolated pricing setup' `
    -Passed ($firstChannelId -gt 0 -and $secondChannelId -gt 0 -and $firstChannelId -ne $secondChannelId) `
    -Detail 'two temporary direct-publishing channels were created'

  $payload = [ordered]@{
    channelIds = @($secondChannelId, $firstChannelId)
    percentage = 10
    validUntil = [DateTimeOffset]::UtcNow.AddDays(60).ToString('o')
    publicTerms = 'QA verification terms after adjustment'
    reason = '本机批量调价防重回归'
  }
  $baseHeaders = @{ Authorization = "Bearer $adminToken" }
  $missingKey = Invoke-LocalHttp `
    -Path '/admin/pricing/adjustments' `
    -Headers $baseHeaders `
    -Body $payload
  Add-Check -Name 'missing idempotency key' -Passed ($missingKey.Status -eq 400) `
    -Detail "HTTP $($missingKey.Status); no price change accepted"

  $adjustmentHeaders = @{
    Authorization = "Bearer $adminToken"
    'Idempotency-Key' = $idempotencyKey
  }
  $first = Invoke-LocalHttp `
    -Path '/admin/pricing/adjustments' `
    -Headers $adjustmentHeaders `
    -Body $payload
  Add-Check -Name 'first batch adjustment' -Passed ($first.Status -eq 200) `
    -Detail "HTTP $($first.Status); two prices adjusted once"
  $firstData = $first.Content | ConvertFrom-Json

  $retry = Invoke-LocalHttp `
    -Path '/admin/pricing/adjustments' `
    -Headers $adjustmentHeaders `
    -Body $payload
  Add-Check -Name 'same batch retry' -Passed ($retry.Status -eq 200) `
    -Detail "HTTP $($retry.Status); original adjustment returned"
  $retryData = $retry.Content | ConvertFrom-Json

  $firstItems = @($firstData.data.items | Sort-Object { [long]$_.channelId })
  $retryItems = @($retryData.data.items | Sort-Object { [long]$_.channelId })
  $sameItems = (
    $firstItems.Count -eq 2 -and
    $retryItems.Count -eq 2 -and
    [long]$firstData.data.adjustedCount -eq 2 -and
    [long]$retryData.data.adjustedCount -eq 2
  )
  if ($sameItems) {
    for ($index = 0; $index -lt 2; $index++) {
      if (
        [long]$firstItems[$index].channelId -ne [long]$retryItems[$index].channelId -or
        [long]$firstItems[$index].quoteId -ne [long]$retryItems[$index].quoteId -or
        [decimal]$firstItems[$index].customerPrice -ne [decimal]$retryItems[$index].customerPrice
      ) {
        $sameItems = $false
        break
      }
    }
  }
  Add-Check -Name 'retry quote identity' -Passed $sameItems `
    -Detail 'the retry returned the same two quote records and prices'

  $changedPayload = [ordered]@{
    channelIds = $payload.channelIds
    percentage = 11
    validUntil = $payload.validUntil
    publicTerms = $payload.publicTerms
    reason = $payload.reason
  }
  $reusedKey = Invoke-LocalHttp `
    -Path '/admin/pricing/adjustments' `
    -Headers $adjustmentHeaders `
    -Body $changedPayload
  Add-Check -Name 'request key content binding' -Passed ($reusedKey.Status -eq 409) `
    -Detail "HTTP $($reusedKey.Status); a key cannot be reused for another percentage"

  $safeIdempotencyKey = $idempotencyKey.Replace("'", "''")
  $ledgerSql = @"
SELECT json_build_object(
  'batchCount', (
    SELECT count(*) FROM quote_adjustment_batch
    WHERE submission_key='$safeIdempotencyKey'
  ),
  'completedBatchCount', (
    SELECT count(*) FROM quote_adjustment_batch
    WHERE submission_key='$safeIdempotencyKey'
      AND status='COMPLETED' AND channel_count=2 AND adjusted_count=2
  ),
  'adjustmentCount', (
    SELECT count(*)
    FROM quote_adjustment adjustment
    JOIN quote_adjustment_batch batch ON batch.id=adjustment.batch_id
    WHERE batch.submission_key='$safeIdempotencyKey'
      AND adjustment.adjustment_mode='BATCH_PERCENT'
  ),
  'firstActiveQuoteCount', (
    SELECT count(*) FROM channel_quote
    WHERE channel_id=$firstChannelId AND status='ACTIVE'
  ),
  'secondActiveQuoteCount', (
    SELECT count(*) FROM channel_quote
    WHERE channel_id=$secondChannelId AND status='ACTIVE'
  ),
  'firstQuoteCount', (
    SELECT count(*) FROM channel_quote WHERE channel_id=$firstChannelId
  ),
  'secondQuoteCount', (
    SELECT count(*) FROM channel_quote WHERE channel_id=$secondChannelId
  ),
  'firstCustomerPrice', (
    SELECT customer_price FROM channel_quote
    WHERE channel_id=$firstChannelId AND status='ACTIVE'
  ),
  'secondCustomerPrice', (
    SELECT customer_price FROM channel_quote
    WHERE channel_id=$secondChannelId AND status='ACTIVE'
  )
)::text;
"@
  $ledger = (Invoke-LocalSql -Sql $ledgerSql) | ConvertFrom-Json
  Add-Check -Name 'single applied batch' `
    -Passed (
      [long]$ledger.batchCount -eq 1 -and
      [long]$ledger.completedBatchCount -eq 1 -and
      [long]$ledger.adjustmentCount -eq 2 -and
      [long]$ledger.firstActiveQuoteCount -eq 1 -and
      [long]$ledger.secondActiveQuoteCount -eq 1 -and
      [long]$ledger.firstQuoteCount -eq 2 -and
      [long]$ledger.secondQuoteCount -eq 2 -and
      [decimal]$ledger.firstCustomerPrice -eq [decimal]110.00 -and
      [decimal]$ledger.secondCustomerPrice -eq [decimal]132.00
    ) `
    -Detail 'one completed batch, two ledger entries, prices CNY 110.00 and CNY 132.00'

  $checks | Format-Table -AutoSize
  Write-Output 'Local batch quote-adjustment idempotency verification passed.'
}
finally {
  if (-not [string]::IsNullOrWhiteSpace($channelPrefix)) {
    $safePrefix = $channelPrefix.Replace("'", "''")
    $safeKey = $idempotencyKey.Replace("'", "''")
    $cleanupSql = @"
BEGIN;
DELETE FROM operation_log
WHERE (
    action='ADJUST_CHANNEL_PRICE'
    AND target_type='CHANNEL'
    AND target_id IN (
      SELECT id::text FROM publish_channel
      WHERE channel_no IN ('$safePrefix-A','$safePrefix-B')
    )
  )
  OR (
    action='BATCH_ADJUST_CHANNEL_PRICE'
    AND target_type='QUOTE_ADJUSTMENT_BATCH'
    AND target_id IN (
      SELECT id::text FROM quote_adjustment_batch
      WHERE submission_key='$safeKey'
    )
  );
DELETE FROM quote_adjustment
WHERE channel_id IN (
  SELECT id FROM publish_channel
  WHERE channel_no IN ('$safePrefix-A','$safePrefix-B')
);
DELETE FROM quote_adjustment_batch
WHERE submission_key='$safeKey';
DELETE FROM channel_quote
WHERE channel_id IN (
  SELECT id FROM publish_channel
  WHERE channel_no IN ('$safePrefix-A','$safePrefix-B')
);
DELETE FROM publish_channel
WHERE channel_no IN ('$safePrefix-A','$safePrefix-B');
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
