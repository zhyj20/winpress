[CmdletBinding()]
param(
  [string]$ApiBaseUrl = 'http://127.0.0.1:8192/api/v1'
)

<#
Local-only access-change regression.

The check deliberately removes one local demo user's Redis session index, changes the account
status to suspended and back to active, then proves the pre-change token is still rejected. It
never prints account names, passwords, bearer tokens, user ids or response bodies.
#>

$ErrorActionPreference = 'Stop'
if ($ApiBaseUrl -notmatch '^http://(127\.0\.0\.1|localhost):\d+/api/v1$') {
  throw 'This verification can run only against a loopback local-demo API.'
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$accountDocument = Join-Path $projectRoot 'docs\TEST-ACCOUNTS.md'
. (Join-Path $PSScriptRoot 'lib\LocalTestAccounts.ps1')

function Invoke-LocalJson {
  param(
    [ValidateSet('GET','POST','PATCH')] [string]$Method,
    [string]$Path,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )
  $parameters = @{
    Method = $Method
    Uri = "$ApiBaseUrl$Path"
    Headers = $Headers
    UseBasicParsing = $true
    TimeoutSec = 20
  }
  if ($null -ne $Body) {
    $parameters.ContentType = 'application/json'
    $parameters.Body = $Body | ConvertTo-Json -Depth 6 -Compress
  }
  try {
    $response = Invoke-WebRequest @parameters
    return [PSCustomObject]@{
      Status = [int]$response.StatusCode
      Data = if ([string]::IsNullOrWhiteSpace($response.Content)) {
        $null
      } else {
        $response.Content | ConvertFrom-Json
      }
    }
  } catch {
    if ($null -ne $_.Exception.Response) {
      return [PSCustomObject]@{ Status = [int]$_.Exception.Response.StatusCode; Data = $null }
    }
    return [PSCustomObject]@{ Status = 0; Data = $null }
  }
}

function Login-LocalAccount {
  param([object]$Account)
  $response = Invoke-LocalJson -Method POST -Path '/auth/login' -Body @{
    username = $Account.Username
    password = $Account.Password
  }
  if ($response.Status -ne 200 -or [string]::IsNullOrWhiteSpace([string]$response.Data.data.token)) {
    throw 'A required local demo login failed.'
  }
  return $response
}

$adminToken = $null
$freshTargetToken = $null
$targetUserId = $null
$targetRole = $null
$targetWasChanged = $false
$targetWasRestored = $false

try {
  $accounts = @(Get-WinPressLocalDemoCredentials -AccountDocument $accountDocument)
  $targetAccount = @($accounts | Where-Object { $_.Group -eq 'CASE_DEMO' } | Select-Object -First 1)
  if ($targetAccount.Count -ne 1) { throw 'No isolated local demo customer is available.' }

  $adminLogin = $null
  foreach ($candidate in @($accounts | Where-Object { $_.Group -eq 'QUICK_LOGIN' })) {
    $login = Login-LocalAccount -Account $candidate
    if ($login.Data.data.user.role -eq 'PLATFORM_ADMIN') {
      $adminLogin = $login
      break
    }
    Invoke-LocalJson -Method POST -Path '/auth/logout' -Headers @{
      Authorization = "Bearer $($login.Data.data.token)"
    } | Out-Null
  }
  if ($null -eq $adminLogin) { throw 'The local platform administrator account is unavailable.' }
  $adminToken = [string]$adminLogin.Data.data.token
  $adminHeaders = @{ Authorization = "Bearer $adminToken" }

  $targetLogin = Login-LocalAccount -Account $targetAccount[0]
  $oldTargetToken = [string]$targetLogin.Data.data.token

  $users = Invoke-LocalJson -Method GET -Path '/admin/users?page=1&pageSize=100' -Headers $adminHeaders
  $targetRows = @($users.Data.data.items | Where-Object { $_.username -eq $targetAccount[0].Username })
  if ($users.Status -ne 200 -or $targetRows.Count -ne 1) {
    throw 'The isolated local demo account could not be resolved in the administrator scope.'
  }
  $targetUserId = [long]$targetRows[0].id
  $targetRole = [string]$targetRows[0].role
  if ($targetRows[0].status -ne 'ACTIVE') {
    throw 'The isolated local demo account is not active before verification.'
  }

  $redisContainerId = (docker compose -f (Join-Path $projectRoot 'docker-compose.local-demo.yml') ps -q redis).Trim()
  if ([string]::IsNullOrWhiteSpace($redisContainerId)) {
    throw 'The local-demo Redis container is not running.'
  }
  $redisInspect = @(docker inspect $redisContainerId | ConvertFrom-Json)
  if ($redisInspect.Count -ne 1 -or $redisInspect[0].State.Health.Status -ne 'healthy') {
    throw 'The local-demo Redis container is not healthy.'
  }
  $backendContainerId = (docker compose -f (Join-Path $projectRoot 'docker-compose.local-demo.yml') ps -q backend).Trim()
  if ([string]::IsNullOrWhiteSpace($backendContainerId)) {
    throw 'The local-demo backend container is not running.'
  }
  $backendInspect = @(docker inspect $backendContainerId | ConvertFrom-Json)
  $passwordEntry = @(
    $backendInspect[0].Config.Env | Where-Object { $_.StartsWith('WINPRESS_REDIS_PASSWORD=') }
  )
  if ($passwordEntry.Count -ne 1) { throw 'The local-demo Redis credential is unavailable.' }
  $redisPassword = $passwordEntry[0].Substring('WINPRESS_REDIS_PASSWORD='.Length)
  $deletedIndex = & docker exec -e "REDISCLI_AUTH=$redisPassword" $redisContainerId `
    redis-cli --no-auth-warning DEL "winpress:user-sessions:$targetUserId"
  if ($LASTEXITCODE -ne 0) { throw 'The local session-index test setup failed.' }

  $suspended = Invoke-LocalJson -Method PATCH -Path "/admin/users/$targetUserId" `
    -Headers $adminHeaders -Body @{ role = $targetRole; status = 'SUSPENDED' }
  if ($suspended.Status -ne 200) { throw 'The local account suspension step failed.' }
  $targetWasChanged = $true

  $restored = Invoke-LocalJson -Method PATCH -Path "/admin/users/$targetUserId" `
    -Headers $adminHeaders -Body @{ role = $targetRole; status = 'ACTIVE' }
  if ($restored.Status -ne 200) { throw 'The local account restoration step failed.' }
  $targetWasRestored = $true

  $staleSession = Invoke-LocalJson -Method GET -Path '/auth/me' -Headers @{
    Authorization = "Bearer $oldTargetToken"
  }
  if ($staleSession.Status -ne 401) {
    throw 'A token issued before the access change became valid again.'
  }

  $freshLogin = Login-LocalAccount -Account $targetAccount[0]
  $freshTargetToken = [string]$freshLogin.Data.data.token
  if ($freshLogin.Data.data.user.role -ne $targetRole) {
    throw 'The restored local account did not return to its original role.'
  }

  [PSCustomObject]@{
    AccountStatusRestored = $true
    MissingIndexRevocation = 'PASS'
    StaleTokenAfterReactivation = 'REJECTED'
    FreshLoginAfterRestoration = 'PASS'
  } | Format-List
}
finally {
  if ($targetWasChanged -and -not $targetWasRestored -and
      $null -ne $targetUserId -and $null -ne $targetRole -and $null -ne $adminToken) {
    Invoke-LocalJson -Method PATCH -Path "/admin/users/$targetUserId" `
      -Headers @{ Authorization = "Bearer $adminToken" } `
      -Body @{ role = $targetRole; status = 'ACTIVE' } | Out-Null
  }
  foreach ($token in @($freshTargetToken, $adminToken)) {
    if (-not [string]::IsNullOrWhiteSpace($token)) {
      Invoke-LocalJson -Method POST -Path '/auth/logout' -Headers @{
        Authorization = "Bearer $token"
      } | Out-Null
    }
  }
}
