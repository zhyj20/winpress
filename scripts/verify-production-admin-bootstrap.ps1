[CmdletBinding()]
param()

<#
Validates the controlled first-production-administrator bootstrap in an isolated production stack.

The script creates a fresh production-equivalent database, provisions one synthetic administrator
with an in-memory password, exercises the real login and administrator APIs, proves that a second
bootstrap is refused, validates the database and audit boundaries, and removes the isolated stack.
No generated password, token, contact field, database credential, or password hash is printed.
#>

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$coldStartPath = Join-Path $PSScriptRoot 'verify-production-cold-start.ps1'
$bootstrapPath = Join-Path $PSScriptRoot 'bootstrap-production-admin.ps1'
$runId = [Guid]::NewGuid().ToString('N')
$statePath = Join-Path (
  [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
) "winpress-production-admin-bootstrap-$runId.json"

function Get-RandomHex {
  param([int]$ByteCount = 20)
  $bytes = New-Object byte[] $ByteCount
  $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $generator.GetBytes($bytes)
  } finally {
    $generator.Dispose()
  }
  return [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

function Invoke-SafeJsonApi {
  param(
    [ValidateSet('Get', 'Post')]
    [string]$Method,
    [string]$Uri,
    [hashtable]$Headers,
    [string]$Body,
    [string]$FailureMessage
  )
  try {
    $parameters = @{
      Method = $Method
      Uri = $Uri
      TimeoutSec = 20
    }
    if ($null -ne $Headers -and $Headers.Count -gt 0) {
      $parameters.Headers = $Headers
    }
    if (-not [string]::IsNullOrWhiteSpace($Body)) {
      $parameters.ContentType = 'application/json'
      $parameters.Body = $Body
    }
    return Invoke-RestMethod @parameters
  } catch {
    throw $FailureMessage
  }
}

function Get-HttpStatus {
  param(
    [string]$Uri,
    [hashtable]$Headers
  )
  try {
    $parameters = @{
      UseBasicParsing = $true
      Uri = $Uri
      TimeoutSec = 20
    }
    if ($null -ne $Headers -and $Headers.Count -gt 0) {
      $parameters.Headers = $Headers
    }
    return [int](Invoke-WebRequest @parameters).StatusCode
  } catch {
    if ($null -ne $_.Exception.Response) {
      return [int]$_.Exception.Response.StatusCode
    }
    throw 'The bootstrap API boundary could not be reached.'
  }
}

function Invoke-ColdStartDatabaseJson {
  param([string]$Sql)
  $result = @(
    & docker exec winpress-production-postgres `
      psql -X -q -v ON_ERROR_STOP=1 `
      -U winpress_cold_start `
      -d winpress_cold_start `
      -Atc $Sql 2>&1
  )
  if ($LASTEXITCODE -ne 0) {
    throw 'A bootstrap database assertion could not be evaluated.'
  }
  $json = ($result | ForEach-Object { [string]$_ }) -join ''
  try {
    return $json | ConvertFrom-Json
  } catch {
    throw 'A bootstrap database assertion returned an invalid result.'
  }
}

if (-not (Test-Path -LiteralPath $coldStartPath -PathType Leaf)) {
  throw 'The production cold-start verifier is missing.'
}
if (-not (Test-Path -LiteralPath $bootstrapPath -PathType Leaf)) {
  throw 'The controlled administrator bootstrap tool is missing.'
}
if (Test-Path -LiteralPath $statePath) {
  throw 'The isolated administrator-bootstrap state path is unexpectedly occupied.'
}

$plainPassword = 'Wp!9' + (Get-RandomHex)
$securePassword = ConvertTo-SecureString $plainPassword -AsPlainText -Force
$syntheticEmail = "bootstrap-$($runId.Substring(0, 12))@qa.winpress.invalid"
$secondEmail = "bootstrap-second-$($runId.Substring(0, 8))@qa.winpress.invalid"
$token = $null
$loginBody = $null
$secondPassword = $null
$secondSecurePassword = $null
$cleanupRequired = $false

try {
  & $coldStartPath -KeepRunning -StateFile $statePath
  $cleanupRequired = $true

  if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    throw 'The isolated production stack did not publish its protected state record.'
  }
  $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
  if (
    [string]$state.projectName -ne 'winpress-production-cold-start' -or
    [int]$state.backendPort -le 0
  ) {
    throw 'The isolated production stack state is not valid for bootstrap verification.'
  }

  $bootstrapResult = & $bootstrapPath `
    -OrganizationName 'WinPress Bootstrap QA' `
    -DisplayName 'Cold Start Administrator' `
    -Mobile '13900000000' `
    -Email $syntheticEmail `
    -Password $securePassword `
    -ConfirmCreate `
    -AllowColdStartValidation

  if (
    $null -eq $bootstrapResult -or
    -not [bool]$bootstrapResult.Created -or
    [string]$bootstrapResult.Role -ne 'PLATFORM_ADMIN' -or
    [int]$bootstrapResult.Permissions -ne 10 -or
    [string]$bootstrapResult.AuditAction -ne 'BOOTSTRAP_PLATFORM_ADMIN' -or
    [string]$bootstrapResult.ComposeProject -ne 'winpress-production-cold-start'
  ) {
    throw 'The controlled bootstrap did not return the accepted role and audit contract.'
  }

  $backendBase = "http://127.0.0.1:$([int]$state.backendPort)/api/v1"
  $loginBody = @{
    username = $syntheticEmail
    password = $plainPassword
  } | ConvertTo-Json -Compress
  $login = Invoke-SafeJsonApi `
    -Method Post `
    -Uri "$backendBase/auth/login" `
    -Headers @{} `
    -Body $loginBody `
    -FailureMessage 'The bootstrapped administrator could not log in through the production API.'

  if (
    -not [bool]$login.success -or
    [string]::IsNullOrWhiteSpace([string]$login.data.token) -or
    [string]$login.data.user.role -ne 'PLATFORM_ADMIN' -or
    @($login.data.user.permissions).Count -ne 10
  ) {
    throw 'The bootstrapped administrator login response does not match the accepted permission contract.'
  }
  $token = [string]$login.data.token
  $authHeaders = @{ Authorization = "Bearer $token" }

  $me = Invoke-SafeJsonApi `
    -Method Get `
    -Uri "$backendBase/auth/me" `
    -Headers $authHeaders `
    -Body '' `
    -FailureMessage 'The authenticated administrator profile could not be read.'
  if (
    -not [bool]$me.success -or
    [string]$me.data.role -ne 'PLATFORM_ADMIN' -or
    @($me.data.permissions).Count -ne 10
  ) {
    throw 'The authenticated administrator profile does not match the accepted permission contract.'
  }

  $adminUsers = Invoke-SafeJsonApi `
    -Method Get `
    -Uri "$backendBase/admin/users?page=1&pageSize=20" `
    -Headers $authHeaders `
    -Body '' `
    -FailureMessage 'The bootstrapped administrator could not access the protected account-management API.'
  if (
    -not [bool]$adminUsers.success -or
    [int]$adminUsers.data.total -ne 1 -or
    @($adminUsers.data.items).Count -ne 1
  ) {
    throw 'The protected account-management API did not return the single bootstrapped account.'
  }

  if ((Get-HttpStatus -Uri "$backendBase/admin/users?page=1&pageSize=20" -Headers @{}) -ne 401) {
    throw 'The administrator account-management API is accessible without authentication.'
  }

  $databaseState = Invoke-ColdStartDatabaseJson -Sql @'
SELECT json_build_object(
  'organizationCount', (SELECT count(*) FROM organization),
  'platformOrganizationCount', (
    SELECT count(*) FROM organization
    WHERE organization_type = 'PLATFORM' AND status = 'ACTIVE'
  ),
  'userCount', (SELECT count(*) FROM app_user),
  'activeUserCount', (SELECT count(*) FROM app_user WHERE status = 'ACTIVE'),
  'bcryptCost12Count', (
    SELECT count(*) FROM app_user
    WHERE password_hash ~ '^\$2[aby]\$12\$'
  ),
  'platformAdminCount', (
    SELECT count(*)
    FROM user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE ur.status = 'ACTIVE' AND r.status = 'ACTIVE'
      AND r.role_code = 'PLATFORM_ADMIN'
  ),
  'nonAdminRoleCount', (
    SELECT count(*)
    FROM user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE r.role_code <> 'PLATFORM_ADMIN'
  ),
  'permissionCount', (
    SELECT count(DISTINCT p.permission_code)
    FROM user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    JOIN role_permission rp ON rp.role_id = r.id AND rp.status = 'ACTIVE'
    JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 'ACTIVE'
    WHERE ur.status = 'ACTIVE' AND r.role_code = 'PLATFORM_ADMIN'
  ),
  'bootstrapAuditCount', (
    SELECT count(*) FROM operation_log
    WHERE action = 'BOOTSTRAP_PLATFORM_ADMIN'
      AND actor_role = 'PLATFORM_ADMIN'
      AND target_type = 'USER'
      AND status = 'SUCCESS'
  ),
  'sensitiveAuditCount', (
    SELECT count(*) FROM operation_log
    WHERE action = 'BOOTSTRAP_PLATFORM_ADMIN'
      AND (
        detail_json ?| array[
          'email','mobile','phone','password','passwordHash',
          'token','secret','databasePassword'
        ]
        OR detail_json::text LIKE '%@%'
      )
  ),
  'businessDataCount', (
    (SELECT count(*) FROM customer_requirement) +
    (SELECT count(*) FROM project) +
    (SELECT count(*) FROM supplier) +
    (SELECT count(*) FROM supplier_order) +
    (SELECT count(*) FROM business_inquiry)
  )
)::text;
'@

  if (
    [int]$databaseState.organizationCount -ne 1 -or
    [int]$databaseState.platformOrganizationCount -ne 1 -or
    [int]$databaseState.userCount -ne 1 -or
    [int]$databaseState.activeUserCount -ne 1 -or
    [int]$databaseState.bcryptCost12Count -ne 1 -or
    [int]$databaseState.platformAdminCount -ne 1 -or
    [int]$databaseState.nonAdminRoleCount -ne 0 -or
    [int]$databaseState.permissionCount -ne 10 -or
    [int]$databaseState.bootstrapAuditCount -ne 1 -or
    [int]$databaseState.sensitiveAuditCount -ne 0 -or
    [int]$databaseState.businessDataCount -ne 0
  ) {
    throw 'The first administrator database, role, audit, or data-boundary state is incorrect.'
  }

  $secondPassword = 'Wp!8' + (Get-RandomHex)
  $secondSecurePassword = ConvertTo-SecureString $secondPassword -AsPlainText -Force
  $duplicateRefused = $false
  try {
    $ignored = & $bootstrapPath `
      -OrganizationName 'WinPress Bootstrap QA 2' `
      -DisplayName 'Second Cold Start Administrator' `
      -Mobile '13800000000' `
      -Email $secondEmail `
      -Password $secondSecurePassword `
      -ConfirmCreate `
      -AllowColdStartValidation
  } catch {
    if ($_.Exception.Message.Contains('ADMIN_BOOTSTRAP_ADMIN_EXISTS')) {
      $duplicateRefused = $true
    } else {
      throw 'The second bootstrap failed for an unexpected reason.'
    }
  }
  if (-not $duplicateRefused) {
    throw 'The controlled bootstrap accepted a second platform administrator.'
  }

  $postRefusalState = Invoke-ColdStartDatabaseJson -Sql @'
SELECT json_build_object(
  'organizationCount', (SELECT count(*) FROM organization),
  'userCount', (SELECT count(*) FROM app_user),
  'userRoleCount', (SELECT count(*) FROM user_role),
  'bootstrapAuditCount', (
    SELECT count(*) FROM operation_log
    WHERE action = 'BOOTSTRAP_PLATFORM_ADMIN'
  )
)::text;
'@
  if (
    [int]$postRefusalState.organizationCount -ne 1 -or
    [int]$postRefusalState.userCount -ne 1 -or
    [int]$postRefusalState.userRoleCount -ne 1 -or
    [int]$postRefusalState.bootstrapAuditCount -ne 1
  ) {
    throw 'The refused second bootstrap changed production data.'
  }

  $logout = Invoke-SafeJsonApi `
    -Method Post `
    -Uri "$backendBase/auth/logout" `
    -Headers $authHeaders `
    -Body '' `
    -FailureMessage 'The bootstrap verification session could not be revoked.'
  if (-not [bool]$logout.success) {
    throw 'The bootstrap verification session logout did not succeed.'
  }
  if ((Get-HttpStatus -Uri "$backendBase/auth/me" -Headers $authHeaders) -ne 401) {
    throw 'The bootstrap verification token remained valid after logout.'
  }

  Write-Output (
    'Production administrator bootstrap verification passed: one controlled PLATFORM_ADMIN, ' +
    '10 permissions, protected administrator API access, redacted audit evidence, ' +
    'and an unchanged database after the refused second bootstrap.'
  )
} finally {
  $token = $null
  $loginBody = $null
  $plainPassword = $null
  $securePassword = $null
  $secondPassword = $null
  $secondSecurePassword = $null
  $syntheticEmail = $null
  $secondEmail = $null

  if ($cleanupRequired -or (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    & $coldStartPath -CleanupOnly -StateFile $statePath
  }
}
