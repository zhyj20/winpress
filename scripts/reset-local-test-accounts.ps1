[CmdletBinding()]
param(
  [switch]$Force,
  [string]$ComposeFile = ''
)

$ErrorActionPreference = 'Stop'

if (-not $Force) {
  throw 'This resets local demo-account passwords. Confirm the target is a local test database, then rerun with -Force.'
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$accountsDocument = Join-Path $root 'docs\TEST-ACCOUNTS.md'
. (Join-Path $PSScriptRoot 'lib\LocalTestAccounts.ps1')
if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
  $ComposeFile = Join-Path $root 'docker-compose.local-demo.yml'
}
if (-not (Test-Path -LiteralPath $accountsDocument)) {
  throw 'docs\TEST-ACCOUNTS.md was not found; local test accounts cannot be identified.'
}
if (-not (Test-Path -LiteralPath $ComposeFile)) {
  throw 'The Docker Compose file was not found.'
}
$localComposeFile = (Resolve-Path (Join-Path $root 'docker-compose.local-demo.yml')).Path
$resolvedComposeFile = (Resolve-Path -LiteralPath $ComposeFile).Path
if (-not [string]::Equals($resolvedComposeFile, $localComposeFile, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw 'Only docker-compose.local-demo.yml may be used to reset local demo accounts.'
}

$accounts = @(Get-WinPressLocalDemoCredentials -AccountDocument $accountsDocument)

if ($accounts.Count -lt 4) {
  throw 'The test-account document format or account count is unexpected; no database changes were made.'
}

$sql = [System.Collections.Generic.List[string]]::new()
$sql.Add('CREATE EXTENSION IF NOT EXISTS pgcrypto;')
foreach ($account in $accounts) {
  $username = $account.Username.Replace("'", "''")
  $passwordBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($account.Password))
  $sql.Add(@"
UPDATE app_user
SET password_hash = crypt(convert_from(decode('$passwordBase64', 'base64'), 'UTF8'), gen_salt('bf', 12)),
    updated_at = CURRENT_TIMESTAMP
WHERE username = '$username';
"@)
}

$composePath = $resolvedComposeFile
& docker compose -f $composePath exec -T postgres psql -v ON_ERROR_STOP=1 -U winpress -d winpress_commercial -c ($sql -join "`n") | Out-Null

$userChecks = foreach ($account in $accounts) {
  "username = '$($account.Username.Replace("'", "''"))'"
}
$accountIds = @(& docker compose -f $composePath exec -T postgres psql -v ON_ERROR_STOP=1 -U winpress -d winpress_commercial -At -c "SELECT id FROM app_user WHERE $($userChecks -join ' OR ');")
if ($accountIds.Count -ne $accounts.Count) {
  throw 'The documented test accounts were not all found; sessions were not changed.'
}

$redisPassword = $env:REDIS_PASSWORD
if ([string]::IsNullOrWhiteSpace($redisPassword)) {
  $envFile = Join-Path $root '.env'
  if (Test-Path -LiteralPath $envFile) {
    $passwordLine = Get-Content -LiteralPath $envFile -Encoding utf8 |
      Where-Object { $_ -match '^REDIS_PASSWORD=' } | Select-Object -First 1
    if ($passwordLine) { $redisPassword = $passwordLine.Substring('REDIS_PASSWORD='.Length) }
  }
}
if ([string]::IsNullOrWhiteSpace($redisPassword)) {
  $redisPassword = 'winpress_local_redis_2026'
}
foreach ($id in $accountIds) {
  $redisScript = 'for token in $(redis-cli --no-auth-warning SMEMBERS "winpress:user-sessions:' + $id + '"); do redis-cli --no-auth-warning DEL "winpress:session:$token" >/dev/null; done; redis-cli --no-auth-warning DEL "winpress:user-sessions:' + $id + '" >/dev/null'
  & docker compose -f $composePath exec -T -e "REDISCLI_AUTH=$redisPassword" redis sh -c $redisScript | Out-Null
}

$checks = foreach ($account in $accounts) {
  $username = $account.Username.Replace("'", "''")
  $passwordBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($account.Password))
  "(username = '$username' AND password_hash = crypt(convert_from(decode('$passwordBase64', 'base64'), 'UTF8'), password_hash))"
}
$verified = (& docker compose -f $composePath exec -T postgres psql -v ON_ERROR_STOP=1 -U winpress -d winpress_commercial -At -c "SELECT count(*) FROM app_user WHERE $($checks -join ' OR ');").Trim()
if ([int]$verified -ne $accounts.Count) {
  throw 'Local test-account verification failed; check the container state and test-account document.'
}

Write-Output "Synchronized $verified local test accounts. Passwords were not displayed; previous sessions should sign in again."
