param(
  [string]$Container = "winpress-commercial-postgres",
  [string]$Database = "winpress_commercial",
  [string]$DatabaseUser = "winpress"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$databaseDir = (Resolve-Path (Join-Path $projectRoot "database")).Path
$channelTarget = Join-Path $databaseDir "media_channels.csv"
$quoteTarget = Join-Path $databaseDir "media_quotes.csv"
$channelPart = "$channelTarget.part"
$quotePart = "$quoteTarget.part"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

if (Test-Path -LiteralPath $channelTarget) {
  Copy-Item -LiteralPath $channelTarget -Destination "$channelTarget.$stamp.bak"
}
if (Test-Path -LiteralPath $quoteTarget) {
  Copy-Item -LiteralPath $quoteTarget -Destination "$quoteTarget.$stamp.bak"
}

$channelSql = @"
\copy (
  SELECT c.channel_no, c.channel_name, c.channel_type, c.category, c.region, c.publish_form,
         c.expected_days, c.link_support, c.public_notes, c.source_type, c.source_ref,
         c.last_verified_at, c.status
  FROM publish_channel c
  WHERE c.status='ACTIVE'
  ORDER BY c.id
) TO '/tmp/winpress_media_channels.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
"@

$quoteSql = @"
\copy (
  SELECT c.channel_no, q.quote_no, q.cost_price, q.customer_price, q.currency,
         q.valid_from, q.valid_until, q.public_terms, q.status
  FROM channel_quote q
  JOIN publish_channel c ON c.id=q.channel_id
  WHERE q.status='ACTIVE' AND q.valid_until>CURRENT_TIMESTAMP
  ORDER BY q.id
) TO '/tmp/winpress_media_quotes.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
"@

docker exec $Container psql -v ON_ERROR_STOP=1 -U $DatabaseUser -d $Database -c $channelSql
docker exec $Container psql -v ON_ERROR_STOP=1 -U $DatabaseUser -d $Database -c $quoteSql
docker cp "${Container}:/tmp/winpress_media_channels.csv" $channelPart
docker cp "${Container}:/tmp/winpress_media_quotes.csv" $quotePart

$channelHeader = Get-Content -LiteralPath $channelPart -Encoding utf8 -TotalCount 1
$quoteHeader = Get-Content -LiteralPath $quotePart -Encoding utf8 -TotalCount 1
if ($channelHeader -notmatch "^channel_no,channel_name,channel_type") {
  throw "Invalid media channel seed header"
}
if ($quoteHeader -notmatch "^channel_no,quote_no,cost_price") {
  throw "Invalid media quote seed header"
}

Move-Item -LiteralPath $channelPart -Destination $channelTarget -Force
Move-Item -LiteralPath $quotePart -Destination $quoteTarget -Force

$channelRows = (Get-Content -LiteralPath $channelTarget -Encoding utf8 | Measure-Object -Line).Lines - 1
$quoteRows = (Get-Content -LiteralPath $quoteTarget -Encoding utf8 | Measure-Object -Line).Lines - 1
if ($channelRows -lt 20000 -or $quoteRows -lt 20000) {
  throw "Unexpected export row counts: channels=$channelRows, quotes=$quoteRows"
}

Get-Item -LiteralPath $channelTarget, $quoteTarget |
  Select-Object FullName, Length, LastWriteTime
Write-Output "rows: channels=$channelRows, quotes=$quoteRows"
