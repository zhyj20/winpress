[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$EnvFile,
  [switch]$SkipComposeConfig,
  [switch]$SkipPortCheck
)

<#
Read-only production deployment preflight.

It never starts containers, connects to a database, or prints secrets. It rejects the sample
environment file, placeholder values, invalid production CORS origins, duplicate ports, and ports
already occupied on the local host before a deployment owner invokes production Compose.
#>

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composePath = Join-Path $projectRoot 'docker-compose.production.yml'
$exampleEnvPath = Join-Path $projectRoot '.env.example'

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
  throw 'The requested production environment file does not exist.'
}
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
  throw 'The production Compose file does not exist.'
}

$resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
$resolvedExampleEnv = (Resolve-Path -LiteralPath $exampleEnvPath).Path
if ([string]::Equals($resolvedEnvFile, $resolvedExampleEnv, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw 'The sample .env.example is not a deployable production environment file.'
}

function Read-EnvironmentMap {
  param([string]$Path)
  $values = @{}
  foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
    $line = $rawLine.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { throw 'The production environment file contains an invalid assignment.' }
    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    if ($value.Length -ge 2 -and (
      ($value.StartsWith('"') -and $value.EndsWith('"')) -or
      ($value.StartsWith("'") -and $value.EndsWith("'"))
    )) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    if ($values.ContainsKey($name)) { throw 'The production environment file contains a duplicate variable name.' }
    $values[$name] = $value
  }
  return $values
}

function Test-PlaceholderValue {
  param([string]$Value)
  if ([string]::IsNullOrWhiteSpace($Value)) { return $true }
  return $Value -match '(?i)(change_this|replace_me|your[_ -]|<[^>]+>|\bexample\b|\btodo\b)'
}

function Read-StrictBoolean {
  param(
    [hashtable]$Values,
    [string]$Name
  )
  if (-not $Values.ContainsKey($Name)) {
    throw "The production environment file is missing required variable: $Name."
  }
  $value = [string]$Values[$Name]
  if ($value -notin @('true', 'false')) {
    throw "$Name must be written explicitly as true or false."
  }
  return $value -eq 'true'
}

function Read-IntegerInRange {
  param(
    [hashtable]$Values,
    [string]$Name,
    [int]$Minimum,
    [int]$Maximum
  )
  $parsed = 0
  if (
    -not $Values.ContainsKey($Name) -or
    -not [int]::TryParse([string]$Values[$Name], [ref]$parsed) -or
    $parsed -lt $Minimum -or
    $parsed -gt $Maximum
  ) {
    throw "$Name must be an integer between $Minimum and $Maximum."
  }
  return $parsed
}

function Test-PublicHttpsUri {
  param([string]$Value)
  $uri = $null
  if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref]$uri)) {
    return $false
  }
  if (
    $uri.Scheme -ne 'https' -or
    [string]::IsNullOrWhiteSpace($uri.Host) -or
    -not [string]::IsNullOrWhiteSpace($uri.UserInfo)
  ) {
    return $false
  }
  return $uri.Host -notmatch '(?i)^(localhost|127\.0\.0\.1|\[?::1\]?)$'
}

function Get-PortOwners {
  param([int]$Port)
  $pattern = "^\s*TCP\s+(\S+):$Port\s+\S+\s+LISTENING\s+(\d+)\s*$"
  $owners = @()
  $rows = netstat -ano | Select-String -Pattern $pattern
  foreach ($row in $rows) {
    $portPid = [int]$row.Matches[0].Groups[2].Value
    $owners += [pscustomobject]@{
      Pid = $portPid
      Process = (Get-Process -Id $portPid -ErrorAction SilentlyContinue).ProcessName
    }
  }
  return @($owners | Sort-Object Pid -Unique)
}

$environment = Read-EnvironmentMap -Path $resolvedEnvFile
$requiredVariables = @(
  'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_PORT',
  'REDIS_PASSWORD', 'REDIS_PORT', 'FRONTEND_PORT', 'BACKEND_PORT', 'WINPRESS_CORS_ORIGINS',
  'WINPRESS_STORAGE_MAX_FILE_BYTES', 'WINPRESS_API_DOCS_ENABLED', 'WINPRESS_FEDERATION_ENABLED',
  'WINPRESS_FEDERATION_MIGRATE_ON_START'
)
$missing = @($requiredVariables | Where-Object {
  -not $environment.ContainsKey($_) -or [string]::IsNullOrWhiteSpace([string]$environment[$_])
})
if ($missing.Count -gt 0) {
  throw "The production environment file is missing required variables: $($missing -join ', ')."
}

$placeholderKeys = @($requiredVariables | Where-Object { Test-PlaceholderValue ([string]$environment[$_]) })
if ($placeholderKeys.Count -gt 0) {
  throw "The production environment file still contains placeholder values for: $($placeholderKeys -join ', ')."
}

$niumedialBaseConfigured = -not [string]::IsNullOrWhiteSpace([string]$environment['WINPRESS_NIUMEDIA_BASE_URL'])
$niumedialTokenConfigured = -not [string]::IsNullOrWhiteSpace([string]$environment['WINPRESS_NIUMEDIA_TOKEN'])
if ($niumedialBaseConfigured -xor $niumedialTokenConfigured) {
  throw 'The external media base URL and token must either both be supplied after authorization or both remain empty.'
}

$federationEnabled = Read-StrictBoolean -Values $environment -Name 'WINPRESS_FEDERATION_ENABLED'
$federationMigrateOnStart = Read-StrictBoolean -Values $environment -Name 'WINPRESS_FEDERATION_MIGRATE_ON_START'
$apiDocsEnabled = Read-StrictBoolean -Values $environment -Name 'WINPRESS_API_DOCS_ENABLED'
if ($apiDocsEnabled) {
  throw 'Swagger and OpenAPI documentation must remain disabled in production.'
}
[void](Read-IntegerInRange -Values $environment -Name 'WINPRESS_STORAGE_MAX_FILE_BYTES' -Minimum 1048576 -Maximum 104857600)
if ($federationMigrateOnStart -and -not $federationEnabled) {
  throw 'Federation migrations cannot be enabled while federation itself is disabled.'
}

if ($federationEnabled) {
  $federationVariables = @(
    'WINPRESS_FEDERATION_SHARED_SECRET',
    'WINPRESS_FEDERATION_PLATFORM_ISSUER',
    'WINPRESS_FEDERATION_WINPRESS_ISSUER',
    'WINPRESS_FEDERATION_SOURCE_INSTANCE_ID',
    'WINPRESS_FEDERATION_GEO_CALLBACK_URL',
    'WINPRESS_FEDERATION_CALLBACK_TIMEOUT_SECONDS',
    'WINPRESS_FEDERATION_MAX_REQUESTS_PER_MINUTE'
  )
  $missingFederation = @($federationVariables | Where-Object {
    -not $environment.ContainsKey($_) -or [string]::IsNullOrWhiteSpace([string]$environment[$_])
  })
  if ($missingFederation.Count -gt 0) {
    throw "Federation is enabled but required variables are missing: $($missingFederation -join ', ')."
  }

  $federationPlaceholderKeys = @($federationVariables | Where-Object {
    Test-PlaceholderValue ([string]$environment[$_])
  })
  if ($federationPlaceholderKeys.Count -gt 0) {
    throw "Federation is enabled but placeholder values remain for: $($federationPlaceholderKeys -join ', ')."
  }

  $sharedSecretBytes = [System.Text.Encoding]::UTF8.GetByteCount(
    [string]$environment['WINPRESS_FEDERATION_SHARED_SECRET']
  )
  if ($sharedSecretBytes -lt 32) {
    throw 'WINPRESS_FEDERATION_SHARED_SECRET must contain at least 32 UTF-8 bytes.'
  }

  foreach ($issuerKey in @('WINPRESS_FEDERATION_PLATFORM_ISSUER', 'WINPRESS_FEDERATION_WINPRESS_ISSUER')) {
    $issuer = ([string]$environment[$issuerKey]).Trim()
    if ($issuer.Length -gt 128 -or $issuer -notmatch '^[A-Za-z0-9][A-Za-z0-9._:-]*$') {
      throw "$issuerKey contains an invalid issuer identifier."
    }
  }

  $sourceInstanceId = ([string]$environment['WINPRESS_FEDERATION_SOURCE_INSTANCE_ID']).Trim()
  if (
    $sourceInstanceId -cne $sourceInstanceId.ToLowerInvariant() -or
    $sourceInstanceId -notmatch '^[a-z0-9][a-z0-9._:-]{0,127}$' -or
    $sourceInstanceId -eq 'default'
  ) {
    throw 'WINPRESS_FEDERATION_SOURCE_INSTANCE_ID must be a unique normalized lowercase instance identifier, not default.'
  }

  if (-not (Test-PublicHttpsUri ([string]$environment['WINPRESS_FEDERATION_GEO_CALLBACK_URL']))) {
    throw 'WINPRESS_FEDERATION_GEO_CALLBACK_URL must be an explicit public HTTPS URL without embedded credentials.'
  }

  [void](Read-IntegerInRange -Values $environment -Name 'WINPRESS_FEDERATION_CALLBACK_TIMEOUT_SECONDS' -Minimum 5 -Maximum 60)
  [void](Read-IntegerInRange -Values $environment -Name 'WINPRESS_FEDERATION_MAX_REQUESTS_PER_MINUTE' -Minimum 10 -Maximum 10000)
}

$origins = @([string]$environment['WINPRESS_CORS_ORIGINS'] -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($origins.Count -eq 0 -or @($origins | Where-Object {
  $_ -notmatch '^https://' -or $_ -match '(?i)localhost|127\.0\.0\.1'
}).Count -gt 0) {
  throw 'Production CORS origins must be explicit HTTPS public origins, not localhost addresses.'
}

$portKeys = @('POSTGRES_PORT', 'REDIS_PORT', 'FRONTEND_PORT', 'BACKEND_PORT')
$ports = @{}
foreach ($key in $portKeys) {
  $parsedPort = 0
  if (-not [int]::TryParse([string]$environment[$key], [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535) {
    throw "The production environment file contains an invalid port for $key."
  }
  $ports[$key] = $parsedPort
}
if ((@($ports.Values | Sort-Object -Unique)).Count -ne $portKeys.Count) {
  throw 'Production PostgreSQL, Redis, frontend, and backend ports must be distinct.'
}

if (-not $SkipComposeConfig) {
  & docker compose --env-file $resolvedEnvFile -f $composePath config --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Production Compose configuration validation failed.' }
}

if (-not $SkipPortCheck) {
  $conflicts = @()
  foreach ($key in $portKeys) {
    $owners = @(Get-PortOwners -Port $ports[$key])
    if ($owners.Count -gt 0) {
      $conflicts += "$key"
    }
  }
  if ($conflicts.Count -gt 0) {
    throw "Production ports are already occupied for: $($conflicts -join ', '). Choose unused ports or stop the conflicting service before deployment."
  }
}

if ($SkipPortCheck) {
  Write-Output 'Production deployment preflight passed: environment values, public CORS, and Compose configuration are valid; host port availability was intentionally skipped.'
} else {
  Write-Output 'Production deployment preflight passed: environment values, public CORS, Compose configuration, and host port availability are ready for an authorized deployment.'
}
