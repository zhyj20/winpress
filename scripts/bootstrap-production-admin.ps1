[CmdletBinding()]
param(
  [string]$OrganizationName = '',
  [string]$DisplayName = '',
  [string]$Mobile = '',
  [string]$Email = '',
  [System.Security.SecureString]$Password,
  [string]$ContainerName = 'winpress-production-postgres',
  [switch]$ConfirmCreate,
  [switch]$AllowColdStartValidation
)

<#
Creates the first production PLATFORM_ADMIN account exactly once.

The normal invocation is interactive so that the password never appears in shell history. The
script accepts a SecureString only to support isolated automated validation. It refuses local-demo
containers, refuses a second platform administrator, writes one audit record, and never prints the
password, its encoding, the database password, or the contact fields.
#>

$ErrorActionPreference = 'Stop'

if (-not $ConfirmCreate) {
  throw 'ADMIN_BOOTSTRAP_CONFIRMATION_REQUIRED: rerun with -ConfirmCreate after verifying the production target.'
}

$expectedProject = if ($AllowColdStartValidation) {
  'winpress-production-cold-start'
} else {
  'winpress-commercial-production'
}

function Read-RequiredText {
  param(
    [string]$Value,
    [string]$Prompt,
    [int]$MinimumLength,
    [int]$MaximumLength
  )
  $resolved = $Value
  if ([string]::IsNullOrWhiteSpace($resolved)) {
    $resolved = Read-Host $Prompt
  }
  $resolved = ([string]$resolved).Trim()
  if (
    $resolved.Length -lt $MinimumLength -or
    $resolved.Length -gt $MaximumLength -or
    $resolved -match '[\x00-\x1F]'
  ) {
    throw "ADMIN_BOOTSTRAP_INVALID_INPUT: $Prompt has an invalid length or contains control characters."
  }
  return $resolved
}

function Convert-ToBase64Utf8 {
  param([string]$Value)
  return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
}

function Invoke-ProductionPsql {
  param([string]$Sql)
  $command = 'exec psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atf -'
  $result = @($Sql | & docker exec -i $ContainerName sh -c $command 2>&1)
  if ($LASTEXITCODE -ne 0) {
    $safeError = ($result | ForEach-Object { [string]$_ }) -join "`n"
    foreach ($knownCode in @(
      'ADMIN_BOOTSTRAP_SCHEMA_NOT_READY',
      'ADMIN_BOOTSTRAP_ROLE_NOT_READY',
      'ADMIN_BOOTSTRAP_ADMIN_EXISTS',
      'ADMIN_BOOTSTRAP_USERNAME_EXISTS'
    )) {
      if ($safeError.Contains($knownCode)) {
        throw "$knownCode`: the controlled production administrator bootstrap was refused."
      }
    }
    throw 'ADMIN_BOOTSTRAP_DATABASE_ERROR: the controlled administrator transaction failed without exposing database details.'
  }
  return @($result | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
}

$allContainerNames = @(& docker ps -a --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) {
  throw 'ADMIN_BOOTSTRAP_DOCKER_UNAVAILABLE: Docker could not be inspected.'
}
if ($ContainerName -notin $allContainerNames) {
  throw 'ADMIN_BOOTSTRAP_CONTAINER_NOT_FOUND: the expected production PostgreSQL container does not exist.'
}

$rawInspection = & docker inspect $ContainerName
if ($LASTEXITCODE -ne 0) {
  throw 'ADMIN_BOOTSTRAP_CONTAINER_INSPECTION_FAILED: the target container could not be inspected.'
}
$inspection = @($rawInspection | ConvertFrom-Json)[0]
$composeProject = [string]$inspection.Config.Labels.'com.docker.compose.project'
$composeService = [string]$inspection.Config.Labels.'com.docker.compose.service'
$composeFiles = [string]$inspection.Config.Labels.'com.docker.compose.project.config_files'
$healthStatus = [string]$inspection.State.Health.Status

if (
  $composeProject -ne $expectedProject -or
  $composeService -ne 'postgres' -or
  $composeFiles -notmatch '(?i)docker-compose[.]production[.]yml' -or
  -not [bool]$inspection.State.Running -or
  $healthStatus -ne 'healthy'
) {
  throw 'ADMIN_BOOTSTRAP_TARGET_REJECTED: the container is not the expected healthy production PostgreSQL service.'
}

$OrganizationName = Read-RequiredText `
  -Value $OrganizationName `
  -Prompt 'Platform operating organization name' `
  -MinimumLength 2 `
  -MaximumLength 160
$DisplayName = Read-RequiredText `
  -Value $DisplayName `
  -Prompt 'First platform administrator display name' `
  -MinimumLength 2 `
  -MaximumLength 80
$Mobile = Read-RequiredText `
  -Value $Mobile `
  -Prompt 'First platform administrator mobile number' `
  -MinimumLength 11 `
  -MaximumLength 11
$Email = Read-RequiredText `
  -Value $Email `
  -Prompt 'First platform administrator email and login account' `
  -MinimumLength 5 `
  -MaximumLength 160

if ($Mobile -notmatch '^1[3-9]\d{9}$') {
  throw 'ADMIN_BOOTSTRAP_INVALID_MOBILE: use a valid mainland China mobile number.'
}

try {
  $mailAddress = New-Object -TypeName System.Net.Mail.MailAddress -ArgumentList $Email
} catch {
  throw 'ADMIN_BOOTSTRAP_INVALID_EMAIL: use a valid email address.'
}
$normalizedEmail = $mailAddress.Address.ToLowerInvariant()
if ($normalizedEmail -ne $Email.ToLowerInvariant() -or $normalizedEmail.Length -gt 80) {
  throw 'ADMIN_BOOTSTRAP_INVALID_EMAIL: the normalized login email must fit the 80-character account field.'
}

if ($null -eq $Password) {
  $Password = Read-Host 'First platform administrator password (input hidden)' -AsSecureString
}
if ($null -eq $Password -or $Password.Length -eq 0) {
  throw 'ADMIN_BOOTSTRAP_INVALID_PASSWORD: a non-empty SecureString password is required.'
}

$passwordPointer = [IntPtr]::Zero
$plainPassword = $null
try {
  $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
  $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
  if (
    $plainPassword.Length -lt 12 -or
    $plainPassword.Length -gt 64 -or
    $plainPassword -notmatch '[a-z]' -or
    $plainPassword -notmatch '[A-Z]' -or
    $plainPassword -notmatch '\d' -or
    $plainPassword -notmatch '[^A-Za-z0-9]'
  ) {
    throw 'ADMIN_BOOTSTRAP_INVALID_PASSWORD: use 12 to 64 characters with uppercase, lowercase, number, and symbol.'
  }

  $organizationNumber = 'ORG-PLATFORM-' + [Guid]::NewGuid().ToString('N').Substring(0, 12).ToUpperInvariant()
  $userNumber = 'USR-ADMIN-' + [Guid]::NewGuid().ToString('N').Substring(0, 12).ToUpperInvariant()
  $logNumber = 'LOG-BOOTSTRAP-' + [Guid]::NewGuid().ToString('N').Substring(0, 16).ToUpperInvariant()

  $tokens = @{
    '__ORGANIZATION_NAME_B64__' = Convert-ToBase64Utf8 $OrganizationName
    '__DISPLAY_NAME_B64__' = Convert-ToBase64Utf8 $DisplayName
    '__MOBILE_B64__' = Convert-ToBase64Utf8 $Mobile
    '__EMAIL_B64__' = Convert-ToBase64Utf8 $normalizedEmail
    '__PASSWORD_B64__' = Convert-ToBase64Utf8 $plainPassword
    '__ORGANIZATION_NO__' = $organizationNumber
    '__USER_NO__' = $userNumber
    '__LOG_NO__' = $logNumber
  }

  $bootstrapSql = @'
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('winpress-production-admin-bootstrap-v1'));

DO $bootstrap_guard$
DECLARE
  platform_admin_count BIGINT;
BEGIN
  IF to_regclass('public.organization') IS NULL
     OR to_regclass('public.app_user') IS NULL
     OR to_regclass('public.user_role') IS NULL
     OR to_regclass('public.sys_role') IS NULL
     OR to_regclass('public.sys_permission') IS NULL
     OR to_regclass('public.role_permission') IS NULL
     OR to_regclass('public.operation_log') IS NULL
     OR to_regclass('public.settlement_transaction') IS NULL THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_SCHEMA_NOT_READY';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE role_code = 'PLATFORM_ADMIN' AND status = 'ACTIVE'
  ) OR (
    SELECT count(*)
    FROM role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_code = 'PLATFORM_ADMIN'
      AND rp.status = 'ACTIVE'
      AND p.status = 'ACTIVE'
  ) <> 10 THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_ROLE_NOT_READY';
  END IF;

  SELECT count(*)
  INTO platform_admin_count
  FROM user_role ur
  JOIN sys_role r ON r.id = ur.role_id
  WHERE r.role_code = 'PLATFORM_ADMIN';

  IF platform_admin_count > 0 THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_ADMIN_EXISTS';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM app_user
    WHERE lower(username) = lower(
      convert_from(decode('__EMAIL_B64__', 'base64'), 'UTF8')
    )
  ) THEN
    RAISE EXCEPTION 'ADMIN_BOOTSTRAP_USERNAME_EXISTS';
  END IF;
END
$bootstrap_guard$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH new_organization AS (
  INSERT INTO organization (
    organization_no,
    name,
    organization_type,
    contact_name,
    contact_phone,
    contact_email,
    status
  )
  VALUES (
    '__ORGANIZATION_NO__',
    convert_from(decode('__ORGANIZATION_NAME_B64__', 'base64'), 'UTF8'),
    'PLATFORM',
    convert_from(decode('__DISPLAY_NAME_B64__', 'base64'), 'UTF8'),
    convert_from(decode('__MOBILE_B64__', 'base64'), 'UTF8'),
    convert_from(decode('__EMAIL_B64__', 'base64'), 'UTF8'),
    'ACTIVE'
  )
  RETURNING id
),
new_user AS (
  INSERT INTO app_user (
    user_no,
    organization_id,
    username,
    password_hash,
    display_name,
    mobile,
    email,
    status
  )
  SELECT
    '__USER_NO__',
    new_organization.id,
    convert_from(decode('__EMAIL_B64__', 'base64'), 'UTF8'),
    crypt(
      convert_from(decode('__PASSWORD_B64__', 'base64'), 'UTF8'),
      gen_salt('bf', 12)
    ),
    convert_from(decode('__DISPLAY_NAME_B64__', 'base64'), 'UTF8'),
    convert_from(decode('__MOBILE_B64__', 'base64'), 'UTF8'),
    convert_from(decode('__EMAIL_B64__', 'base64'), 'UTF8'),
    'ACTIVE'
  FROM new_organization
  RETURNING id, user_no
),
new_role AS (
  INSERT INTO user_role (user_id, role_id, status)
  SELECT new_user.id, sys_role.id, 'ACTIVE'
  FROM new_user
  CROSS JOIN sys_role
  WHERE sys_role.role_code = 'PLATFORM_ADMIN'
  RETURNING user_id
)
INSERT INTO operation_log (
  log_no,
  actor_id,
  actor_role,
  action,
  target_type,
  target_id,
  detail_json,
  status
)
SELECT
  '__LOG_NO__',
  new_user.id,
  'PLATFORM_ADMIN',
  'BOOTSTRAP_PLATFORM_ADMIN',
  'USER',
  new_user.user_no,
  jsonb_build_object(
    'method', 'CONTROLLED_PRODUCTION_BOOTSTRAP',
    'organizationNo', '__ORGANIZATION_NO__'
  ),
  'SUCCESS'
FROM new_user
JOIN new_role ON new_role.user_id = new_user.id;

COMMIT;
'@

  foreach ($token in $tokens.GetEnumerator()) {
    $bootstrapSql = $bootstrapSql.Replace([string]$token.Key, [string]$token.Value)
  }

  [void](Invoke-ProductionPsql -Sql $bootstrapSql)

  $verificationSql = @'
SELECT json_build_object(
  'adminCount', (
    SELECT count(*)
    FROM app_user u
    JOIN user_role ur ON ur.user_id = u.id AND ur.status = 'ACTIVE'
    JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
    WHERE u.user_no = '__USER_NO__'
      AND u.status = 'ACTIVE'
      AND r.role_code = 'PLATFORM_ADMIN'
  ),
  'permissionCount', (
    SELECT count(DISTINCT p.permission_code)
    FROM app_user u
    JOIN user_role ur ON ur.user_id = u.id AND ur.status = 'ACTIVE'
    JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.status = 'ACTIVE'
    JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 'ACTIVE'
    WHERE u.user_no = '__USER_NO__'
  ),
  'auditCount', (
    SELECT count(*)
    FROM operation_log
    WHERE action = 'BOOTSTRAP_PLATFORM_ADMIN'
      AND target_id = '__USER_NO__'
      AND status = 'SUCCESS'
  ),
  'auditSensitiveKeys', (
    SELECT count(*)
    FROM operation_log
    WHERE action = 'BOOTSTRAP_PLATFORM_ADMIN'
      AND target_id = '__USER_NO__'
      AND detail_json ?| array['email', 'mobile', 'password', 'passwordHash']
  )
)::text;
'@
  $verificationSql = $verificationSql.Replace('__USER_NO__', $userNumber)
  $verificationOutput = @(Invoke-ProductionPsql -Sql $verificationSql)
  if ($verificationOutput.Count -ne 1) {
    throw 'ADMIN_BOOTSTRAP_VERIFICATION_FAILED: the database did not return one verification record.'
  }
  $verification = $verificationOutput[0] | ConvertFrom-Json
  if (
    [int]$verification.adminCount -ne 1 -or
    [int]$verification.permissionCount -ne 10 -or
    [int]$verification.auditCount -ne 1 -or
    [int]$verification.auditSensitiveKeys -ne 0
  ) {
    throw 'ADMIN_BOOTSTRAP_VERIFICATION_FAILED: role, permission, or audit verification did not pass.'
  }

  [pscustomobject]@{
    Created = $true
    UserNo = $userNumber
    Role = 'PLATFORM_ADMIN'
    Permissions = 10
    AuditAction = 'BOOTSTRAP_PLATFORM_ADMIN'
    ComposeProject = $composeProject
  }
} finally {
  if ($passwordPointer -ne [IntPtr]::Zero) {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
  }
  $plainPassword = $null
  $tokens = $null
  $bootstrapSql = $null
}
