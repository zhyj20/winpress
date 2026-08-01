[CmdletBinding()]
param(
  [string]$ApiBaseUrl = 'http://127.0.0.1:8192/api/v1',
  [string]$FrontendBaseUrl = 'http://127.0.0.1:5217',
  [switch]$CreateActivityClosure
)

<#!
Local-only regression check for the commercial WinPress stack.

The script reads local demo accounts from docs/TEST-ACCOUNTS.md but never prints account names,
passwords, bearer tokens, response bodies, or project IDs.  -CreateActivityClosure deliberately
creates a clearly named QA activity and four independently recorded service orders in the local demo
database; it must never be used against a production address.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$scriptRoot = Split-Path -Parent $PSScriptRoot
$accountDocument = Join-Path $scriptRoot 'docs\TEST-ACCOUNTS.md'
. (Join-Path $PSScriptRoot 'lib\LocalTestAccounts.ps1')
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
  param([string]$Name, [bool]$Passed, [string]$Detail)
  $checks.Add([PSCustomObject]@{ Check = $Name; Result = $(if ($Passed) { 'PASS' } else { 'FAIL' }); Detail = $Detail })
  if (-not $Passed) { throw "Regression check failed: $Name ($Detail)" }
}

function Invoke-Http {
  param(
    [ValidateSet('GET','POST','PATCH')] [string]$Method,
    [string]$Uri,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [switch]$SkipRequirementIdempotency,
    [switch]$SkipPublishPlanIdempotency,
    [switch]$SkipSettlementTransactionIdempotency
  )
  $client = [System.Net.Http.HttpClient]::new()
  $request = $null
  $response = $null
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Uri)
    $requestHeaders = @{}
    foreach ($header in $Headers.GetEnumerator()) {
      $requestHeaders[[string]$header.Key] = [string]$header.Value
    }
    if (
      -not $SkipRequirementIdempotency -and
      $Method -eq 'POST' -and
      $Uri.TrimEnd('/') -match '/requirements$' -and
      -not $requestHeaders.ContainsKey('Idempotency-Key')
    ) {
      $requestHeaders['Idempotency-Key'] = [Guid]::NewGuid().ToString()
    }
    if (
      -not $SkipSettlementTransactionIdempotency -and
      $Method -eq 'POST' -and
      $Uri.TrimEnd('/') -match '/admin/settlements/\d+/transactions$' -and
      -not $requestHeaders.ContainsKey('Idempotency-Key')
    ) {
      $requestHeaders['Idempotency-Key'] = [Guid]::NewGuid().ToString()
    }
    if (
      -not $SkipPublishPlanIdempotency -and
      $Method -eq 'POST' -and
      $Uri.TrimEnd('/') -match '/projects/\d+/publish-plans?$' -and
      -not $requestHeaders.ContainsKey('Idempotency-Key')
    ) {
      $requestHeaders['Idempotency-Key'] = [Guid]::NewGuid().ToString()
    }
    foreach ($header in $requestHeaders.GetEnumerator()) {
      [void]$request.Headers.TryAddWithoutValidation([string]$header.Key, [string]$header.Value)
    }
    if ($null -ne $Body) {
      $json = $Body | ConvertTo-Json -Depth 10 -Compress
      $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $contentBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $content = [System.Text.Encoding]::UTF8.GetString($contentBytes)
    $responseHeaders = @{}
    foreach ($header in $response.Headers) {
      $responseHeaders[[string]$header.Key] = [string]($header.Value -join ', ')
    }
    foreach ($header in $response.Content.Headers) {
      $responseHeaders[[string]$header.Key] = [string]($header.Value -join ', ')
    }
    return [PSCustomObject]@{ Status = [int]$response.StatusCode; Content = $content; Headers = $responseHeaders }
  } catch {
    return [PSCustomObject]@{ Status = 0; Content = ''; Headers = @{} }
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $request) { $request.Dispose() }
    $client.Dispose()
  }
}

function Invoke-FileUpload {
  param(
    [hashtable]$Headers,
    [long]$ProjectId,
    [string]$FileName = 'local-qa-conference-material.txt',
    [string]$Text = 'Local-only upload regression material. This is not a customer document or production record.',
    [string]$ContentType = 'text/plain'
  )
  $client = [System.Net.Http.HttpClient]::new()
  $form = $null
  $response = $null
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    foreach ($header in $Headers.GetEnumerator()) {
      [void]$client.DefaultRequestHeaders.TryAddWithoutValidation([string]$header.Key, [string]$header.Value)
    }
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = [System.Net.Http.ByteArrayContent]::new([System.Text.Encoding]::UTF8.GetBytes($Text))
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
    $form.Add($fileContent, 'file', $FileName)
    $form.Add([System.Net.Http.StringContent]::new([string]$ProjectId, [System.Text.Encoding]::UTF8), 'projectId')
    $response = $client.PostAsync("$ApiBaseUrl/files", $form).GetAwaiter().GetResult()
    $contentBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $content = [System.Text.Encoding]::UTF8.GetString($contentBytes)
    return [PSCustomObject]@{ Status = [int]$response.StatusCode; Content = $content }
  } catch {
    return [PSCustomObject]@{ Status = 0; Content = '' }
  } finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $form) { $form.Dispose() }
    $client.Dispose()
  }
}

function Invoke-Json {
  param(
    [ValidateSet('GET','POST','PATCH')] [string]$Method,
    [string]$Path,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [int]$ExpectedStatus = 200
  )
  $response = Invoke-Http -Method $Method -Uri "$ApiBaseUrl$Path" -Headers $Headers -Body $Body
  Add-Check -Name "$Method $Path" -Passed ($response.Status -eq $ExpectedStatus) -Detail "HTTP $($response.Status)"
  if ([string]::IsNullOrWhiteSpace($response.Content)) { return $null }
  return $response.Content | ConvertFrom-Json
}

function Find-ForbiddenKey {
  param([object]$Value)
  $forbidden = 'storagekey|operationnote|mediaid|reporterid|external|supplier|cost|token|apikey|secret|upstream|internalnote|ownername|operatorname|assignedoperator|createdby|voidedby|voidreason'
  if ($null -eq $Value) { return $false }
  if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
    foreach ($item in $Value) { if (Find-ForbiddenKey $item) { return $true } }
    return $false
  }
  foreach ($property in $Value.PSObject.Properties) {
    if ($property.Name -match $forbidden) { return $true }
    if (Find-ForbiddenKey $property.Value) { return $true }
  }
  return $false
}

function Find-ExactForbiddenKey {
  param([object]$Value, [string[]]$Keys)
  if ($null -eq $Value) { return $false }
  if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
    foreach ($item in $Value) { if (Find-ExactForbiddenKey $item $Keys) { return $true } }
    return $false
  }
  foreach ($property in $Value.PSObject.Properties) {
    if ($Keys -contains $property.Name) { return $true }
    if (Find-ExactForbiddenKey $property.Value $Keys) { return $true }
  }
  return $false
}

function Add-DashboardMetricCheck {
  param(
    [object]$Dashboard,
    [string]$Metric,
    [object]$ListResponse,
    [string]$Name
  )
  $metricProperty = $Dashboard.data.PSObject.Properties[$Metric]
  $totalProperty = $ListResponse.data.PSObject.Properties['total']
  $passed = $null -ne $metricProperty -and $null -ne $totalProperty -and
    $metricProperty.Value -is [ValueType] -and $totalProperty.Value -is [ValueType] -and
    [long]$metricProperty.Value -eq [long]$totalProperty.Value
  Add-Check -Name $Name -Passed $passed -Detail 'dashboard metric and its linked list use the same scoped total'
}

function Add-DashboardCollectionMetricCheck {
  param(
    [object]$Dashboard,
    [string]$Metric,
    [object]$CollectionResponse,
    [string]$Name
  )
  $metricProperty = $Dashboard.data.PSObject.Properties[$Metric]
  $itemCount = @($CollectionResponse.data).Count
  $passed = $null -ne $metricProperty -and $metricProperty.Value -is [ValueType] -and
    [long]$metricProperty.Value -eq [long]$itemCount
  Add-Check -Name $Name -Passed $passed -Detail 'dashboard metric and its linked collection use the same scoped total'
}

function Submit-Requirement {
  param([hashtable]$Headers, [hashtable]$Payload)
  $response = Invoke-Json -Method POST -Path '/requirements' -Headers $Headers -Body $Payload
  if ($null -eq $response.data.projectId) { throw 'Requirement response did not include a project id.' }
  return [long]$response.data.projectId
}

function Invoke-ConcurrentRequirementPair {
  param(
    [hashtable]$Headers,
    [hashtable]$Payload,
    [string]$IdempotencyKey
  )
  $client = [System.Net.Http.HttpClient]::new()
  $requests = New-Object System.Collections.Generic.List[System.Net.Http.HttpRequestMessage]
  $responses = New-Object System.Collections.Generic.List[System.Net.Http.HttpResponseMessage]
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(20)
    $json = $Payload | ConvertTo-Json -Depth 10 -Compress
    $tasks = @()
    foreach ($index in 1..2) {
      $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$ApiBaseUrl/requirements"
      )
      foreach ($header in $Headers.GetEnumerator()) {
        [void]$request.Headers.TryAddWithoutValidation([string]$header.Key, [string]$header.Value)
      }
      [void]$request.Headers.TryAddWithoutValidation('Idempotency-Key', $IdempotencyKey)
      $request.Content = [System.Net.Http.StringContent]::new(
        $json,
        [System.Text.Encoding]::UTF8,
        'application/json'
      )
      $requests.Add($request)
      $tasks += $client.SendAsync($request)
    }
    [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)
    $results = @()
    foreach ($task in $tasks) {
      $response = $task.Result
      $responses.Add($response)
      $contentBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
      $content = [System.Text.Encoding]::UTF8.GetString($contentBytes)
      $results += [PSCustomObject]@{
        Status = [int]$response.StatusCode
        Content = $content
      }
    }
    return $results
  } finally {
    foreach ($response in $responses) { $response.Dispose() }
    foreach ($request in $requests) { $request.Dispose() }
    $client.Dispose()
  }
}

function Resolve-AdminPublishTaskId {
  param([hashtable]$Headers, [string]$TaskNo)
  $response = Invoke-Http -Method GET -Uri "$ApiBaseUrl/publish-tasks?page=1&pageSize=100" -Headers $Headers
  if ($response.Status -ne 200 -or [string]::IsNullOrWhiteSpace($response.Content)) {
    throw 'Unable to resolve the local QA task in the authorised operator ledger.'
  }
  $matches = @((($response.Content | ConvertFrom-Json).data.items) | Where-Object { $_.taskNo -eq $TaskNo })
  if ($matches.Count -ne 1 -or $null -eq $matches[0].id) {
    throw 'The authorised operator ledger did not return exactly one local QA task.'
  }
  return [long]$matches[0].id
}

$sessions = @{}
$supplementalTokens = New-Object System.Collections.Generic.List[string]
try {
  $health = Invoke-Json -Method GET -Path '/health'
  Add-Check -Name 'health payload' -Passed ($health.data.status -eq 'UP' -and $health.data.database -eq 'UP') -Detail 'backend and database UP'
  Add-Check -Name 'current database schema readiness' -Passed ($health.data.schemaStatus -eq 'UP') -Detail 'the public health response exposes only generic readiness; its backend readiness query verifies the accepted schema 41 workflow, integrity and migration-ledger baseline without disclosing deployment metadata'
  $healthFields = @($health.data.PSObject.Properties.Name)
  Add-Check -Name 'public health hides deployment metadata' -Passed (
    $healthFields.Count -eq 3 -and
    @(('status', 'database', 'schemaStatus') | Where-Object { $_ -notin $healthFields }).Count -eq 0
  ) -Detail 'only generic status, database and schema readiness fields are public'

  $anonymousFile = Invoke-Http -Method GET -Uri "$ApiBaseUrl/files/FIL-NOT-FOUND"
  Add-Check -Name 'anonymous file download is blocked' -Passed ($anonymousFile.Status -eq 401) -Detail "HTTP $($anonymousFile.Status)"
  $publicFile = Invoke-Http -Method GET -Uri "$FrontendBaseUrl/files/FIL-NOT-FOUND"
  Add-Check -Name 'public file path is disabled' -Passed ($publicFile.Status -eq 404) -Detail "HTTP $($publicFile.Status)"
  $publicAccounts = Invoke-Http -Method GET -Uri "$FrontendBaseUrl/test-accounts.html"
  Add-Check -Name 'public test accounts are disabled' -Passed ($publicAccounts.Status -eq 404) -Detail "HTTP $($publicAccounts.Status)"
  $geoStatus = Invoke-Http -Method GET -Uri "$ApiBaseUrl/integrations/geo/status"
  Add-Check -Name 'internal GEO status is not public' -Passed ($geoStatus.Status -eq 404) -Detail "HTTP $($geoStatus.Status)"
  $anonymousOpenApi = Invoke-Http -Method GET -Uri "$ApiBaseUrl/open-api/v1/services"
  Add-Check -Name 'anonymous open API catalog is blocked without an access key' -Passed (
    $anonymousOpenApi.Status -eq 401
  ) -Detail "HTTP $($anonymousOpenApi.Status)"
  $serverBaseUrl = $ApiBaseUrl -replace '/api/v1/?$', ''
  $openApiSpecResponse = Invoke-Http -Method GET -Uri "$serverBaseUrl/v3/api-docs"
  Add-Check -Name 'local OpenAPI specification is available for controlled QA' -Passed (
    $openApiSpecResponse.Status -eq 200
  ) -Detail "HTTP $($openApiSpecResponse.Status)"
  try {
    $openApiSpec = $openApiSpecResponse.Content | ConvertFrom-Json
  } catch {
    throw 'Regression check failed: local OpenAPI specification is not valid JSON'
  }
  $clientSecurity = @($openApiSpec.paths.'/api/v1/open-api/v1/services'.get.security)
  $adminSecurity = @($openApiSpec.paths.'/api/v1/admin/open-api'.get.security)
  $healthSecurity = $openApiSpec.paths.'/api/v1/open-api/health'.get.security
  $clientUsesApiKey = @($clientSecurity | ForEach-Object { $_.PSObject.Properties.Name }) -contains 'openApiKey'
  $adminUsesBearer = @($adminSecurity | ForEach-Object { $_.PSObject.Properties.Name }) -contains 'bearerAuth'
  Add-Check -Name 'OpenAPI documents customer endpoints with the API-key guard' -Passed $clientUsesApiKey -Detail 'openApiKey'
  Add-Check -Name 'OpenAPI documents admin endpoints with the session guard' -Passed $adminUsesBearer -Detail 'bearerAuth'
  Add-Check -Name 'OpenAPI leaves the public health endpoint unauthenticated' -Passed (
    $null -eq $healthSecurity -or @($healthSecurity).Count -eq 0
  ) -Detail 'no security requirement'

  $localDemoAccounts = @(Get-WinPressLocalDemoCredentials -AccountDocument $accountDocument)
  $quickLoginAccounts = @($localDemoAccounts | Where-Object { $_.Group -eq 'QUICK_LOGIN' })
  $caseDemoAccounts = @($localDemoAccounts | Where-Object { $_.Group -eq 'CASE_DEMO' })
  $primaryCustomerUsername = $null
  foreach ($account in $quickLoginAccounts) {
    $login = Invoke-Http -Method POST -Uri "$ApiBaseUrl/auth/login" -Body @{ username = $account.Username; password = $account.Password }
    Add-Check -Name 'local role login' -Passed ($login.Status -eq 200) -Detail "HTTP $($login.Status)"
    $loginData = $login.Content | ConvertFrom-Json
    $role = [string]$loginData.data.user.role
    if ([string]::IsNullOrWhiteSpace($role) -or [string]::IsNullOrWhiteSpace([string]$loginData.data.token)) {
      throw 'Login response did not contain the expected local session data.'
    }
    $sessions[$role] = [string]$loginData.data.token
    if ($role -eq 'CUSTOMER') { $primaryCustomerUsername = $account.Username }
  }
  foreach ($role in 'CUSTOMER','PUBLISH_OPERATOR','PLATFORM_ADMIN') {
    Add-Check -Name "required role $role" -Passed $sessions.ContainsKey($role) -Detail 'local session available'
  }

  $secondaryCustomerToken = $null
  foreach ($account in $caseDemoAccounts) {
    $login = Invoke-Http -Method POST -Uri "$ApiBaseUrl/auth/login" -Body @{ username = $account.Username; password = $account.Password }
    Add-Check -Name 'documented isolated customer login' -Passed ($login.Status -eq 200) -Detail "HTTP $($login.Status)"
    $loginData = $login.Content | ConvertFrom-Json
    Add-Check -Name 'documented isolated customer role' -Passed ($loginData.data.user.role -eq 'CUSTOMER') -Detail 'customer scope confirmed'
    $token = [string]$loginData.data.token
    if ([string]::IsNullOrWhiteSpace($token)) { throw 'Isolated customer login did not return a session token.' }
    $supplementalTokens.Add($token)
    if ($null -eq $secondaryCustomerToken -and $account.Username -ne $primaryCustomerUsername) {
      $secondaryCustomerToken = $token
    }
  }
  Add-Check -Name 'secondary customer session' -Passed (-not [string]::IsNullOrWhiteSpace($secondaryCustomerToken)) -Detail 'independent customer scope available'

  $customerHeaders = @{ Authorization = "Bearer $($sessions['CUSTOMER'])" }
  $operatorHeaders = @{ Authorization = "Bearer $($sessions['PUBLISH_OPERATOR'])" }
  $adminHeaders = @{ Authorization = "Bearer $($sessions['PLATFORM_ADMIN'])" }
  $secondaryCustomerHeaders = @{ Authorization = "Bearer $secondaryCustomerToken" }
  $currentCustomerServiceTypes = @('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')
  $customerTransactions = Invoke-Json -Method GET -Path '/transaction-records?page=1&pageSize=100' -Headers $customerHeaders
  Add-Check -Name 'customer transaction ledger contains no platform-only fields' -Passed (
    -not (Find-ForbiddenKey $customerTransactions.data)
  ) -Detail 'customer ledger excludes internal notes, actors and voiding reasons'
  $invalidCurrentTransactions = @(
    $customerTransactions.data.items | Where-Object {
      $_.archiveOnly -or $currentCustomerServiceTypes -notcontains $_.serviceType
    }
  )
  Add-Check -Name 'current customer transactions contain only the four independent services' -Passed (
    $invalidCurrentTransactions.Count -eq 0
  ) -Detail "non-current transaction rows=$($invalidCurrentTransactions.Count)"
  $customerArchivedTransactions = Invoke-Json -Method GET -Path '/transaction-archive-records?page=1&pageSize=100' -Headers $customerHeaders
  Add-Check -Name 'customer transaction archive contains no platform-only fields' -Passed (
    -not (Find-ForbiddenKey $customerArchivedTransactions.data)
  ) -Detail 'legacy transaction facts remain customer-safe and independently readable'
  $invalidArchivedTransactions = @(
    $customerArchivedTransactions.data.items | Where-Object {
      -not $_.archiveOnly -or $currentCustomerServiceTypes -contains $_.serviceType
    }
  )
  Add-Check -Name 'retired combined-service transactions stay outside the current ledger' -Passed (
    $invalidArchivedTransactions.Count -eq 0
  ) -Detail "invalid archive transaction rows=$($invalidArchivedTransactions.Count)"
  $secondaryCustomerTransactions = Invoke-Json -Method GET -Path '/transaction-records?page=1&pageSize=100' -Headers $secondaryCustomerHeaders
  Add-Check -Name 'secondary customer transaction ledger contains no platform-only fields' -Passed (
    -not (Find-ForbiddenKey $secondaryCustomerTransactions.data)
  ) -Detail 'transaction projection remains safe in an independent customer scope'
  $invalidSecondaryTransactions = @(
    $secondaryCustomerTransactions.data.items | Where-Object {
      $_.archiveOnly -or $currentCustomerServiceTypes -notcontains $_.serviceType
    }
  )
  Add-Check -Name 'secondary customer transactions remain in the four-service ledger' -Passed (
    $invalidSecondaryTransactions.Count -eq 0
  ) -Detail "non-current secondary transaction rows=$($invalidSecondaryTransactions.Count)"
  $operatorTransactions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/transaction-records?page=1&pageSize=20" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access customer transaction ledger' -Passed ($operatorTransactions.Status -eq 403) -Detail "HTTP $($operatorTransactions.Status)"
  $operatorArchivedTransactions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/transaction-archive-records?page=1&pageSize=20" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access customer transaction archive' -Passed ($operatorArchivedTransactions.Status -eq 403) -Detail "HTTP $($operatorArchivedTransactions.Status)"
  $adminTransactions = Invoke-Json -Method GET -Path '/admin/settlement-transactions?page=1&pageSize=1' -Headers $adminHeaders
  Add-Check -Name 'administrator can read the auditable transaction ledger' -Passed (
    $null -ne $adminTransactions.data.total
  ) -Detail 'platform-only ledger endpoint is available to the administrator'
  $customerAdminTransactions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/settlement-transactions?page=1&pageSize=1" -Headers $customerHeaders
  Add-Check -Name 'customer cannot read the platform transaction ledger' -Passed (
    $customerAdminTransactions.Status -eq 403
  ) -Detail "HTTP $($customerAdminTransactions.Status)"
  $operatorAdminTransactions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/settlement-transactions?page=1&pageSize=1" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot read the platform transaction ledger' -Passed (
    $operatorAdminTransactions.Status -eq 403
  ) -Detail "HTTP $($operatorAdminTransactions.Status)"
  $adminCustomerTransactions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/transaction-records?page=1&pageSize=1" -Headers $adminHeaders
  Add-Check -Name 'administrator cannot impersonate a customer transaction scope' -Passed (
    $adminCustomerTransactions.Status -eq 403
  ) -Detail "HTTP $($adminCustomerTransactions.Status)"
  $customerMe = Invoke-Json -Method GET -Path '/auth/me' -Headers $customerHeaders
  Add-Check -Name 'customer session role' -Passed ($customerMe.data.role -eq 'CUSTOMER') -Detail 'customer scope confirmed'
  $missingIdempotency = Invoke-Http -Method POST -Uri "$ApiBaseUrl/requirements" `
    -Headers $customerHeaders -SkipRequirementIdempotency -Body @{
      title = 'Local QA missing request identity'
      requestedService = 'MEDIA_PR'
      facts = 'This request must be rejected before any project is created.'
    }
  $missingIdempotencyCode = if ([string]::IsNullOrWhiteSpace($missingIdempotency.Content)) {
    ''
  } else {
    ($missingIdempotency.Content | ConvertFrom-Json).code
  }
  Add-Check -Name 'requirement submission requires an idempotency key' -Passed (
    $missingIdempotency.Status -eq 400 -and
    $missingIdempotencyCode -eq 'IDEMPOTENCY_KEY_REQUIRED'
  ) -Detail 'unidentified retries are rejected before creating requirements, projects or tasks'
  $legacyProject = Invoke-Json -Method GET -Path '/projects/1' -Headers $customerHeaders
  Add-Check -Name 'legacy combined-service project is retained as an archive record' -Passed (
    $legacyProject.data.project.requestedService -eq 'WRITING_AND_PUBLISHING'
  ) -Detail 'the local seed preserves the historical record without creating a current service type'
  $legacyLinkedService = Invoke-Http -Method POST -Uri "$ApiBaseUrl/requirements" -Headers $customerHeaders -Body @{
    title = 'Local QA rejected legacy activity link'; requestedService = 'MEDIA_PR'
    facts = 'This request must not attach a new independent service to a historical combined record.'
    relatedProjectId = 1
  }
  $legacyLinkedServiceCode = if ([string]::IsNullOrWhiteSpace($legacyLinkedService.Content)) { '' } else { ($legacyLinkedService.Content | ConvertFrom-Json).code }
  Add-Check -Name 'legacy combined-service project cannot become a new activity root' -Passed (
    $legacyLinkedService.Status -eq 400 -and $legacyLinkedServiceCode -eq 'RELATED_PROJECT_INVALID'
  ) -Detail 'historical combination records remain readable but cannot create new linked orders'
  $customerDashboard = Invoke-Json -Method GET -Path '/dashboard' -Headers $customerHeaders
  Add-Check -Name 'customer dashboard contains no operational data' -Passed (-not (Find-ForbiddenKey $customerDashboard.data)) -Detail 'only scoped count fields are returned'
  $customerTodoPage = Invoke-Json -Method GET -Path '/work-items?page=1&pageSize=1' -Headers $customerHeaders
  $customerPlanConfirmationPage = Invoke-Json -Method GET -Path '/work-items?scope=planConfirmation&page=1&pageSize=1' -Headers $customerHeaders
  $customerActiveProjectPage = Invoke-Json -Method GET -Path '/projects?scope=active&page=1&pageSize=1' -Headers $customerHeaders
  $customerPendingTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?scope=pending&page=1&pageSize=1' -Headers $customerHeaders
  $customerTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?page=1&pageSize=1' -Headers $customerHeaders
  $customerTaskRecordPage = Invoke-Json -Method GET -Path '/task-records?page=1&pageSize=1' -Headers $customerHeaders
  $customerPendingExecutionPage = Invoke-Json -Method GET -Path '/task-records?scope=pendingExecution&page=1&pageSize=1' -Headers $customerHeaders
  $customerAcceptanceTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?scope=awaitingAcceptance&page=1&pageSize=1' -Headers $customerHeaders
  $customerMediaInvitationTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?channelType=MEDIA_PR&page=1&pageSize=1' -Headers $customerHeaders
  $customerDirectPublishingTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?channelType=DIRECT_PUBLISHING&page=1&pageSize=1' -Headers $customerHeaders
  $customerWritingOrderPage = Invoke-Json -Method GET -Path '/order-records?serviceType=ONSITE_WRITING&page=1&pageSize=1' -Headers $customerHeaders
  $customerConferenceProjectPage = Invoke-Json -Method GET -Path '/projects?serviceType=NEWS_CONFERENCE&page=1&pageSize=1' -Headers $customerHeaders
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'todoCount' -ListResponse $customerTodoPage -Name 'dashboard todo metric matches task management'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'pendingPlanConfirmations' -ListResponse $customerPlanConfirmationPage -Name 'dashboard pending plan-confirmation metric matches its queue'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'activeProjects' -ListResponse $customerActiveProjectPage -Name 'dashboard active project metric matches project list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'pendingTasks' -ListResponse $customerPendingTaskPage -Name 'dashboard pending task metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'taskCount' -ListResponse $customerTaskPage -Name 'dashboard task metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'taskRecordCount' -ListResponse $customerTaskRecordPage -Name 'dashboard task record metric matches the four-service ledger'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'pendingPlatformExecutions' -ListResponse $customerPendingExecutionPage -Name 'dashboard pending-platform-execution metric matches the four-service queue'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'awaitingAcceptanceTasks' -ListResponse $customerAcceptanceTaskPage -Name 'dashboard acceptance metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'mediaInvitationTasks' -ListResponse $customerMediaInvitationTaskPage -Name 'dashboard media invitation metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'directPublishingTasks' -ListResponse $customerDirectPublishingTaskPage -Name 'dashboard direct publishing metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'writingAssignments' -ListResponse $customerWritingOrderPage -Name 'dashboard onsite writing metric matches order list'
  Add-DashboardMetricCheck -Dashboard $customerDashboard -Metric 'conferenceProjects' -ListResponse $customerConferenceProjectPage -Name 'dashboard conference metric matches project list'

  # Operator cards must use the same access scope as the pages they open.  This catches the
  # subtle case where an operator is assigned a task or conference checklist item but is not
  # the project owner.
  $operatorDashboard = Invoke-Json -Method GET -Path '/dashboard' -Headers $operatorHeaders
  $operatorTodoPage = Invoke-Json -Method GET -Path '/work-items?page=1&pageSize=1' -Headers $operatorHeaders
  $operatorActiveProjectPage = Invoke-Json -Method GET -Path '/projects?scope=active&page=1&pageSize=1' -Headers $operatorHeaders
  $operatorPendingTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?scope=pending&page=1&pageSize=1' -Headers $operatorHeaders
  $operatorTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?page=1&pageSize=1' -Headers $operatorHeaders
  $operatorTaskRecordPage = Invoke-Json -Method GET -Path '/task-records?page=1&pageSize=1' -Headers $operatorHeaders
  $operatorPendingExecutionPage = Invoke-Json -Method GET -Path '/task-records?scope=pendingExecution&page=1&pageSize=1' -Headers $operatorHeaders
  $operatorAcceptanceTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?scope=awaitingAcceptance&page=1&pageSize=1' -Headers $operatorHeaders
  $operatorMediaInvitationTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?channelType=MEDIA_PR&page=1&pageSize=1' -Headers $operatorHeaders
  $operatorDirectPublishingTaskPage = Invoke-Json -Method GET -Path '/publish-tasks?channelType=DIRECT_PUBLISHING&page=1&pageSize=1' -Headers $operatorHeaders
  $operatorWritingAssignmentPage = Invoke-Json -Method GET -Path '/writing-assignments' -Headers $operatorHeaders
  $operatorConferenceProjectPage = Invoke-Json -Method GET -Path '/projects?serviceType=NEWS_CONFERENCE&page=1&pageSize=1' -Headers $operatorHeaders
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'todoCount' -ListResponse $operatorTodoPage -Name 'operator dashboard todo metric matches task management'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'activeProjects' -ListResponse $operatorActiveProjectPage -Name 'operator dashboard active project metric matches project list'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'pendingTasks' -ListResponse $operatorPendingTaskPage -Name 'operator dashboard pending task metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'taskCount' -ListResponse $operatorTaskPage -Name 'operator dashboard task metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'taskRecordCount' -ListResponse $operatorTaskRecordPage -Name 'operator dashboard task record metric matches the scoped four-service ledger'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'pendingPlatformExecutions' -ListResponse $operatorPendingExecutionPage -Name 'operator dashboard pending-platform-execution metric matches the scoped four-service queue'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'awaitingAcceptanceTasks' -ListResponse $operatorAcceptanceTaskPage -Name 'operator dashboard acceptance metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'mediaInvitationTasks' -ListResponse $operatorMediaInvitationTaskPage -Name 'operator dashboard media invitation metric matches publish task list'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'directPublishingTasks' -ListResponse $operatorDirectPublishingTaskPage -Name 'operator dashboard direct publishing metric matches publish task list'
  Add-DashboardCollectionMetricCheck -Dashboard $operatorDashboard -Metric 'writingAssignments' -CollectionResponse $operatorWritingAssignmentPage -Name 'operator dashboard onsite writing metric matches assignment list'
  Add-DashboardMetricCheck -Dashboard $operatorDashboard -Metric 'conferenceProjects' -ListResponse $operatorConferenceProjectPage -Name 'operator dashboard conference metric matches project list'
  $adminDashboard = Invoke-Json -Method GET -Path '/dashboard' -Headers $adminHeaders
  $adminPendingExecutionPage = Invoke-Json -Method GET -Path '/task-records?scope=pendingExecution&page=1&pageSize=1' -Headers $adminHeaders
  Add-DashboardMetricCheck -Dashboard $adminDashboard -Metric 'pendingPlatformExecutions' -ListResponse $adminPendingExecutionPage -Name 'administrator dashboard pending-platform-execution metric matches the platform four-service queue'
  $writingAssignments = Invoke-Http -Method GET -Uri "$ApiBaseUrl/writing-assignments" -Headers $customerHeaders
  Add-Check -Name 'customer cannot see writer assignments' -Passed ($writingAssignments.Status -eq 403) -Detail "HTTP $($writingAssignments.Status)"
  $operatorRequirements = Invoke-Http -Method GET -Uri "$ApiBaseUrl/requirements" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot enumerate customer requirements' -Passed ($operatorRequirements.Status -eq 403) -Detail "HTTP $($operatorRequirements.Status)"
  $customerPricing = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/pricing?page=1&pageSize=1" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access internal pricing management' -Passed ($customerPricing.Status -eq 403) -Detail "HTTP $($customerPricing.Status)"
  $operatorPricing = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/pricing?page=1&pageSize=1" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access internal pricing management' -Passed ($operatorPricing.Status -eq 403) -Detail "HTTP $($operatorPricing.Status)"
  $customerPricingSummary = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/pricing/summary" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access pricing summary' -Passed ($customerPricingSummary.Status -eq 403) -Detail "HTTP $($customerPricingSummary.Status)"
  $customerSuppliers = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/suppliers?page=1&pageSize=1" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access supplier management' -Passed ($customerSuppliers.Status -eq 403) -Detail "HTTP $($customerSuppliers.Status)"
  $customerSupplierOptions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/suppliers/options?channelId=1" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access supplier assignment candidates' -Passed ($customerSupplierOptions.Status -eq 403) -Detail "HTTP $($customerSupplierOptions.Status)"
  $operatorSupplierOptions = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/suppliers/options?channelId=1" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access supplier assignment candidates' -Passed ($operatorSupplierOptions.Status -eq 403) -Detail "HTTP $($operatorSupplierOptions.Status)"
  $customerSupplierOrders = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/supplier-orders?page=1&pageSize=1" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access supplier order management' -Passed ($customerSupplierOrders.Status -eq 403) -Detail "HTTP $($customerSupplierOrders.Status)"
  $customerIntegrations = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/integrations" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access supplier integration configuration' -Passed ($customerIntegrations.Status -eq 403) -Detail "HTTP $($customerIntegrations.Status)"
  $operatorIntegrations = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/integrations" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access supplier integration configuration' -Passed ($operatorIntegrations.Status -eq 403) -Detail "HTTP $($operatorIntegrations.Status)"
  $customerOpenApiAdmin = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/open-api" -Headers $customerHeaders
  Add-Check -Name 'customer cannot access open API application management' -Passed ($customerOpenApiAdmin.Status -eq 403) -Detail "HTTP $($customerOpenApiAdmin.Status)"
  $operatorOpenApiAdmin = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/open-api" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access open API application management' -Passed ($operatorOpenApiAdmin.Status -eq 403) -Detail "HTTP $($operatorOpenApiAdmin.Status)"
  $adminPricing = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/pricing?page=1&pageSize=1" -Headers $adminHeaders
  Add-Check -Name 'platform admin can access internal pricing management' -Passed ($adminPricing.Status -eq 200) -Detail "HTTP $($adminPricing.Status)"
  $adminSuppliers = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/suppliers?page=1&pageSize=1" -Headers $adminHeaders
  Add-Check -Name 'platform admin can access supplier management' -Passed ($adminSuppliers.Status -eq 200) -Detail "HTTP $($adminSuppliers.Status)"
  $adminSupplierOrders = Invoke-Http -Method GET -Uri "$ApiBaseUrl/admin/supplier-orders?page=1&pageSize=1" -Headers $adminHeaders
  Add-Check -Name 'platform admin can access supplier order management' -Passed ($adminSupplierOrders.Status -eq 200) -Detail "HTTP $($adminSupplierOrders.Status)"
  $adminIntegrations = Invoke-Json -Method GET -Path '/admin/integrations' -Headers $adminHeaders
  Add-Check -Name 'platform admin can access supplier integration configuration' -Passed (
    $null -ne $adminIntegrations.data.summary
  ) -Detail 'HTTP 200 with a structured integration overview'
  Add-Check -Name 'supplier integration overview never returns credential values' -Passed (
    -not (Find-ExactForbiddenKey $adminIntegrations.data @(
      'credentialValue',
      'apiToken',
      'accessToken',
      'password',
      'clientSecret',
      'sharedSecret'
    ))
  ) -Detail 'only an environment-variable reference and readiness booleans may be returned'
  $adminOpenApi = Invoke-Json -Method GET -Path '/admin/open-api' -Headers $adminHeaders
  Add-Check -Name 'platform admin can access open API application management' -Passed (
    $null -ne $adminOpenApi.data.summary -and $null -ne $adminOpenApi.data.applications
  ) -Detail 'HTTP 200 with a structured, platform-only open API overview'
  Add-Check -Name 'open API management never returns raw access keys or key hashes' -Passed (
    -not (Find-ExactForbiddenKey $adminOpenApi.data @(
      'accessKey',
      'keyHash',
      'secretValue',
      'tokenValue'
    ))
  ) -Detail 'administrative records retain controlled metadata; one-time issuance does not enter the overview'
  Add-Check -Name 'all five external and go-live gates are present' -Passed (
    @($adminIntegrations.data.acceptanceGates).Count -eq 5 -and
    @(
      $adminIntegrations.data.acceptanceGates |
        Where-Object { $_.gateCode -notin @(
          'EXTERNAL_MEDIA_DATA',
          'SUPPLIER_FULFILLMENT',
          'LEGAL_TRUST',
          'PRODUCTION_OPERATIONS',
          'LEGACY_COMBINATION_REVIEW'
        ) }
    ).Count -eq 0
  ) -Detail 'external capabilities cannot bypass their named acceptance gates'
  Add-Check -Name 'all required release evidence items are present and still evidence-bound' -Passed (
    @($adminIntegrations.data.acceptanceEvidenceItems).Count -eq 28 -and
    [long]$adminIntegrations.data.summary.requiredEvidenceItemCount -eq 28 -and
    [long]$adminIntegrations.data.summary.verifiedRequiredEvidenceItemCount -eq 0 -and
    @(
      $adminIntegrations.data.acceptanceGates |
        Where-Object {
          [long]$_.requiredItemCount -le 0 -or
          [long]$_.pendingRequiredItemCount -le 0 -or
          $_.status -eq 'PASSED'
        }
    ).Count -eq 0
  ) -Detail '28 mandatory external, supplier, legal, operations and legacy-review items remain open until evidence is verified'
  Add-Check -Name 'built-in external adapters remain unavailable before acceptance' -Passed (
    @(
      $adminIntegrations.data.builtInAdapters |
        Where-Object { $_.acceptanceStatus -ne 'PASSED' -and $_.operationalStatus -ne 'UNAVAILABLE' }
    ).Count -eq 0
  ) -Detail 'runtime configuration alone is not treated as production acceptance'
  $reporterWithoutMedia = Invoke-Http -Method GET -Uri "$ApiBaseUrl/media-discovery?target=REPORTER" -Headers $customerHeaders
  Add-Check -Name 'reporter lookup requires a media context' -Passed ($reporterWithoutMedia.Status -eq 400) -Detail "HTTP $($reporterWithoutMedia.Status)"
  $mediaStatus = Invoke-Json -Method GET -Path '/media-discovery/status' -Headers $customerHeaders
  Add-Check -Name 'media status contains no sensitive fields' -Passed (-not (Find-ForbiddenKey $mediaStatus.data)) -Detail 'field whitelist'
  Add-Check -Name 'media status hides runtime and acceptance administration detail' -Passed (
    -not (Find-ExactForbiddenKey $mediaStatus.data @(
      'runtimeConfigured','governanceReady','verificationStatus',
      'rawMediaSearchConfigured','rawReporterSearchConfigured'
    ))
  ) -Detail 'customer receives only current search capability and a safe fallback state'
  Add-Check -Name 'unlicensed media search stays unavailable with a manual path' -Passed (
    -not [bool]$mediaStatus.data.available -and
    [bool]$mediaStatus.data.manualFallbackAvailable
  ) -Detail 'no live media claim; customer can still submit an item for manual verification'
  $approvedSources = Invoke-Json -Method GET -Path '/customer/approved-manuscripts' -Headers $customerHeaders
  $approvedSource = @($approvedSources.data | Select-Object -First 1)
  Add-Check -Name 'customer has an approved manuscript source' -Passed ($approvedSource.Count -eq 1) -Detail 'direct-publishing source selector'
  Add-Check -Name 'approved manuscript source list excludes raw content and internal provenance' -Passed (
    -not (Find-ExactForbiddenKey $approvedSources.data @('summary','content','changeNote','sourceProjectId','sourceManuscriptId','sourceVersionId','supplierId','costPrice','upstreamReference'))
  ) -Detail 'customer source selector exposes only the references and labels needed to start a direct-publishing project'
  if ($approvedSource.Count -eq 1) {
    $secondaryApprovedSources = Invoke-Json -Method GET -Path '/customer/approved-manuscripts' -Headers $secondaryCustomerHeaders
    $foreignSourceVisible = @($secondaryApprovedSources.data | Where-Object {
      [long]$_.manuscriptId -eq [long]$approvedSource[0].manuscriptId -or
      [long]$_.versionId -eq [long]$approvedSource[0].versionId
    }).Count -gt 0
    Add-Check -Name 'second customer cannot list another customer approved manuscript source' -Passed (-not $foreignSourceVisible) -Detail 'approved manuscript selector is scoped to the current customer and organization'
    $foreignManuscriptRequest = Invoke-Http -Method POST -Uri "$ApiBaseUrl/requirements" -Headers $secondaryCustomerHeaders -Body @{
      title = 'Local QA blocked foreign manuscript source'; requestedService = 'DIRECT_PUBLISHING'
      facts = 'This request must not copy a manuscript owned by another customer.'
      sourceManuscriptId = [long]$approvedSource[0].manuscriptId
      sourceManuscriptVersionId = [long]$approvedSource[0].versionId
    }
    $foreignManuscriptCode = if ([string]::IsNullOrWhiteSpace($foreignManuscriptRequest.Content)) { '' } else { ($foreignManuscriptRequest.Content | ConvertFrom-Json).code }
    Add-Check -Name 'customer cannot create direct publishing with another customer manuscript source' -Passed (
      $foreignManuscriptRequest.Status -eq 400 -and $foreignManuscriptCode -eq 'SOURCE_MANUSCRIPT_NOT_AVAILABLE'
    ) -Detail 'a foreign approved manuscript cannot create or populate a direct-publishing project'
  }
  $channelDirectory = Invoke-Json -Method GET -Path '/channels?type=DIRECT_PUBLISHING&page=1&pageSize=1' -Headers $customerHeaders
  $channelDirectoryJson = $channelDirectory.data.items | ConvertTo-Json -Depth 8 -Compress
  Add-Check -Name 'customer channel directory field boundary' -Passed ($channelDirectoryJson -notmatch '"(channelNo|quoteId|sourceType|sourceRef|costPrice|supplierId|supplierName|upstreamReference)"\s*:') -Detail 'no operational, source, supplier or cost identifier'
  Add-Check -Name 'customer channel directory only lists active channels' -Passed (@($channelDirectory.data.items | Where-Object { $_.status -ne 'ACTIVE' }).Count -eq 0) -Detail 'review-required legacy entries stay internal'

  $projectPage = Invoke-Json -Method GET -Path '/projects?page=1&pageSize=20' -Headers $customerHeaders
  Add-Check -Name 'customer project summary uses field projection' -Passed (-not (Find-ExactForbiddenKey $projectPage.data.items @('operatorName','budget','supplierId','costPrice','upstreamReference'))) -Detail 'no internal owner or pricing fields'
  $customerPublishTasks = Invoke-Json -Method GET -Path '/publish-tasks?page=1&pageSize=20' -Headers $customerHeaders
  Add-Check -Name 'customer publish task field boundary' -Passed (-not (Find-ExactForbiddenKey $customerPublishTasks.data.items @('id','manuscriptId','executionNote','exceptionReason','operatorName','supplierId','costPrice','upstreamReference'))) -Detail 'no internal task key or supplier fields'
  $operatorResultAcceptance = Invoke-Http -Method POST -Uri "$ApiBaseUrl/publish-tasks/999999/accept" -Headers $operatorHeaders -Body @{}
  Add-Check -Name 'operator cannot accept customer results' -Passed ($operatorResultAcceptance.Status -eq 403) -Detail "HTTP $($operatorResultAcceptance.Status)"
  $customerWorkItems = Invoke-Json -Method GET -Path '/work-items?page=1&pageSize=100' -Headers $customerHeaders
  Add-Check -Name 'customer work queue field boundary' -Passed (
    -not (Find-ForbiddenKey $customerWorkItems.data) -and
    -not (Find-ExactForbiddenKey $customerWorkItems.data.items @('itemType','itemId'))
  ) -Detail 'no owner, supplier, cost, upstream or internal task identifiers'
  $invalidCustomerWorkItems = @($customerWorkItems.data.items | Where-Object {
    $_.itemLabel -notin @('稿件审核','成果验收','资料补充','发布计划确认') -or
    $_.status -notin @('CLIENT_REVIEW','AWAITING_CLIENT_ACCEPTANCE','NEEDS_INFO','PENDING_INFO','WAITING_CONFIRMATION')
  })
  Add-Check -Name 'customer task management contains only customer actions' -Passed (
    $invalidCustomerWorkItems.Count -eq 0
  ) -Detail 'only customer plan confirmation, review, acceptance or information supplementation is shown'
  $customerPlanConfirmations = Invoke-Json -Method GET -Path '/work-items?scope=planConfirmation&page=1&pageSize=100' -Headers $customerHeaders
  $invalidCustomerPlanConfirmations = @($customerPlanConfirmations.data.items | Where-Object {
    $_.itemLabel -ne '发布计划确认' -or $_.status -ne 'WAITING_CONFIRMATION'
  })
  Add-Check -Name 'customer plan-confirmation queue contains only saved plans awaiting confirmation' -Passed (
    $invalidCustomerPlanConfirmations.Count -eq 0 -and
    -not (Find-ForbiddenKey $customerPlanConfirmations.data) -and
    -not (Find-ExactForbiddenKey $customerPlanConfirmations.data.items @('itemType','itemId'))
  ) -Detail 'the filtered queue exposes no operational fields and cannot include execution work'
  $customerTaskRecords = Invoke-Json -Method GET -Path '/task-records?page=1&pageSize=20' -Headers $customerHeaders
  Add-Check -Name 'customer task record field boundary' -Passed (
    -not (Find-ForbiddenKey $customerTaskRecords.data) -and
    -not (Find-ExactForbiddenKey $customerTaskRecords.data.items @('itemType','itemId'))
  ) -Detail 'no owner, supplier, cost, upstream or internal task identifiers'
  $customerPendingExecutions = Invoke-Json -Method GET -Path '/task-records?scope=pendingExecution&page=1&pageSize=100' -Headers $customerHeaders
  $invalidPendingExecutions = @($customerPendingExecutions.data.items | Where-Object {
    $executionRecord = $_
    switch ($executionRecord.serviceType) {
      'ONSITE_WRITING' { $executionRecord.status -notin @('WAITING_MATCH','OFFERED','ACCEPTED','DECLINED') }
      'NEWS_CONFERENCE' { $executionRecord.status -notin @('PENDING','IN_PROGRESS','BLOCKED') }
      'MEDIA_PR' { $executionRecord.status -notin @('PENDING_ASSIGNMENT','PENDING_EXECUTION','PENDING_ACCEPTANCE','IN_PROGRESS','EXCEPTION') }
      'DIRECT_PUBLISHING' { $executionRecord.status -notin @('PENDING_ASSIGNMENT','PENDING_EXECUTION','PENDING_ACCEPTANCE','IN_PROGRESS','EXCEPTION') }
      default { $true }
    }
  })
  $pendingExecutionHasForbidden = Find-ForbiddenKey $customerPendingExecutions.data
  $pendingExecutionHasExactForbidden = Find-ExactForbiddenKey $customerPendingExecutions.data.items @('itemType','itemId','ownerName','supplierId','costPrice')
  $pendingExecutionServiceTypes = @(
    $customerPendingExecutions.data.items |
      ForEach-Object { [string]$_.serviceType } |
      Sort-Object -Unique
  ) -join ','
  $pendingExecutionStates = @(
    $customerPendingExecutions.data.items |
      ForEach-Object { [string]$_.status } |
      Sort-Object -Unique
  ) -join ','
  Add-Check -Name 'customer pending-platform-execution queue contains only four-service execution work' -Passed (
    $invalidPendingExecutions.Count -eq 0 -and
    -not $pendingExecutionHasForbidden -and
    -not $pendingExecutionHasExactForbidden
  ) -Detail "invalid service states=$($invalidPendingExecutions.Count); service types=$pendingExecutionServiceTypes; states=$pendingExecutionStates; forbidden fields=$pendingExecutionHasForbidden; exact internal fields=$pendingExecutionHasExactForbidden"
  $invalidTaskRecordScope = Invoke-Http -Method GET -Uri "$ApiBaseUrl/task-records?scope=supplierExecution&page=1&pageSize=1" -Headers $customerHeaders
  Add-Check -Name 'unknown task-record scopes are rejected' -Passed ($invalidTaskRecordScope.Status -eq 400) -Detail "HTTP $($invalidTaskRecordScope.Status)"
  $customerOrders = Invoke-Json -Method GET -Path '/order-records?page=1&pageSize=20' -Headers $customerHeaders
  Add-Check -Name 'customer order record field boundary' -Passed (
    -not (Find-ForbiddenKey $customerOrders.data) -and
    -not (Find-ExactForbiddenKey $customerOrders.data.items @('itemType','itemId'))
  ) -Detail 'no owner, supplier, cost, raw media id, storage key or internal task identifier'
  $serviceOrderCountMismatches = @(
    foreach ($orderServiceType in @('ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING','NEWS_CONFERENCE')) {
      $serviceProjects = Invoke-Json -Method GET -Path "/projects?serviceType=$orderServiceType&page=1&pageSize=1" -Headers $customerHeaders
      $serviceOrders = Invoke-Json -Method GET -Path "/order-records?serviceType=$orderServiceType&page=1&pageSize=1" -Headers $customerHeaders
      if ([long]$serviceProjects.data.total -ne [long]$serviceOrders.data.total) {
        [pscustomobject]@{
          ServiceType = $orderServiceType
          ProjectTotal = [long]$serviceProjects.data.total
          OrderTotal = [long]$serviceOrders.data.total
        }
      }
    }
  )
  Add-Check -Name 'each independent service project retains one stable customer order' -Passed (
    $serviceOrderCountMismatches.Count -eq 0
  ) -Detail "mismatched service totals=$($serviceOrderCountMismatches.Count)"
  $customerSettlements = Invoke-Json -Method GET -Path '/settlement-records?page=1&pageSize=20' -Headers $customerHeaders
  Add-Check -Name 'customer settlement record field boundary' -Passed (-not (Find-ForbiddenKey $customerSettlements.data)) -Detail 'only customer settlement amount, status and timing fields'
  $invalidCurrentSettlements = @(
    $customerSettlements.data.items | Where-Object {
      $_.archiveOnly -or $currentCustomerServiceTypes -notcontains $_.serviceType
    }
  )
  Add-Check -Name 'current customer settlements contain only the four independent services' -Passed (
    $invalidCurrentSettlements.Count -eq 0
  ) -Detail "non-current rows=$($invalidCurrentSettlements.Count)"
  $customerArchivedSettlements = Invoke-Json -Method GET -Path '/settlement-archive-records?page=1&pageSize=20' -Headers $customerHeaders
  Add-Check -Name 'customer settlement archive field boundary' -Passed (
    -not (Find-ForbiddenKey $customerArchivedSettlements.data)
  ) -Detail 'legacy records remain customer-safe and independently readable'
  $nonArchivedLegacyRows = @(
    $customerArchivedSettlements.data.items | Where-Object { -not $_.archiveOnly }
  )
  Add-Check -Name 'retired combined-service settlements are read-only archive records' -Passed (
    [long]$customerArchivedSettlements.data.total -ge 1 -and $nonArchivedLegacyRows.Count -eq 0
  ) -Detail "archive total=$($customerArchivedSettlements.data.total)"
  $archivedSettlementNumbers = @(
    $customerArchivedSettlements.data.items | ForEach-Object { [string]$_.settlementNo }
  )
  $currentTransactionsInArchive = @(
    $customerTransactions.data.items | Where-Object {
      $archivedSettlementNumbers -contains [string]$_.settlementNo
    }
  )
  Add-Check -Name 'current transaction ledger excludes archived settlement numbers' -Passed (
    $currentTransactionsInArchive.Count -eq 0
  ) -Detail "misplaced transaction rows=$($currentTransactionsInArchive.Count)"
  $operatorSettlements = Invoke-Http -Method GET -Uri "$ApiBaseUrl/settlement-records?page=1&pageSize=20" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access customer settlement ledger' -Passed ($operatorSettlements.Status -eq 403) -Detail "HTTP $($operatorSettlements.Status)"
  $operatorArchivedSettlements = Invoke-Http -Method GET -Uri "$ApiBaseUrl/settlement-archive-records?page=1&pageSize=20" -Headers $operatorHeaders
  Add-Check -Name 'operator cannot access customer settlement archive' -Passed ($operatorArchivedSettlements.Status -eq 403) -Detail "HTTP $($operatorArchivedSettlements.Status)"
  $adminSettlements = Invoke-Json -Method GET -Path '/admin/settlements?page=1&pageSize=100' -Headers $adminHeaders
  $archivedAdminSettlement = @($adminSettlements.data.items | Where-Object { $_.archiveOnly } | Select-Object -First 1)
  Add-Check -Name 'administrator ledger labels retired combined-service settlements' -Passed (
    $archivedAdminSettlement.Count -eq 1
  ) -Detail "archive rows found=$($archivedAdminSettlement.Count)"
  if ($archivedAdminSettlement.Count -eq 1) {
    $archivedSettlementUpdate = Invoke-Http -Method PATCH -Uri "$ApiBaseUrl/admin/settlements/$($archivedAdminSettlement[0].id)" -Headers $adminHeaders -Body @{
      status = $archivedAdminSettlement[0].status
      invoiceNo = $archivedAdminSettlement[0].invoiceNo
    }
    Add-Check -Name 'administrator cannot mutate an archived combined-service settlement' -Passed (
      $archivedSettlementUpdate.Status -eq 409
    ) -Detail "HTTP $($archivedSettlementUpdate.Status)"
  }
  $firstProject = @($projectPage.data.items | Select-Object -First 1)
  if ($firstProject.Count -gt 0) {
    $projectDetail = Invoke-Json -Method GET -Path "/projects/$($firstProject[0].id)" -Headers $customerHeaders
    Add-Check -Name 'customer project detail field boundary' -Passed (-not (Find-ForbiddenKey $projectDetail.data)) -Detail 'no owner, supplier, cost, raw media id or storage key'
    $projectProjectionSafe = -not (Find-ExactForbiddenKey $projectDetail.data.project @('id','budget','activityRootProjectId','operatorName','supplierId','costPrice'))
    $taskProjectionSafe = -not (Find-ExactForbiddenKey $projectDetail.data.tasks @('manuscriptId','executionNote','exceptionReason','operatorName','supplierId','costPrice','upstreamReference'))
    $workItemProjectionSafe = -not (Find-ExactForbiddenKey $projectDetail.data.conferenceWorkItems @('id','assignedOperatorId','operatorName','note'))
    $candidateProjectionSafe = -not (Find-ExactForbiddenKey $projectDetail.data.conferenceMediaCandidates @('id','candidateKey','candidateType','mediaId','reporterId','operationNote','score','newsCount','fansCount','operatorName','note'))
    $versionProjectionSafe = -not (Find-ExactForbiddenKey $projectDetail.data.versions @('sourceProjectId','sourceManuscriptId','sourceVersionId','supplierId','costPrice','upstreamReference'))
    Add-Check -Name 'customer project detail uses field projections' -Passed ($projectProjectionSafe -and $taskProjectionSafe -and $workItemProjectionSafe -and $candidateProjectionSafe -and $versionProjectionSafe) -Detail 'project, task, checklist, candidate and manuscript-version fields are customer-safe'
    $publishPlans = Invoke-Json -Method GET -Path "/projects/$($firstProject[0].id)/publish-plans" -Headers $customerHeaders
    Add-Check -Name 'customer publish plan field boundary' -Passed (-not (Find-ForbiddenKey $publishPlans.data)) -Detail 'no owner, supplier, cost or upstream fields'
    Add-Check -Name 'customer publish plans use public plan numbers' -Passed (
      -not (Find-ExactForbiddenKey $publishPlans.data @('id','planId'))
    ) -Detail 'plan lists expose planNo, not an operational database identifier'
    Add-Check -Name 'customer publish plans hide legacy locking fields' -Passed (
      -not (Find-ExactForbiddenKey $publishPlans.data @('exclusiveMediaPr','lockExpiresAt'))
    ) -Detail 'customer plan records do not expose legacy exclusive-arrangement fields'
    $secondaryCustomerProject = Invoke-Http -Method GET -Uri "$ApiBaseUrl/projects/$($firstProject[0].id)" -Headers $secondaryCustomerHeaders
    Add-Check -Name 'second customer cannot access another customer project' -Passed ($secondaryCustomerProject.Status -eq 403) -Detail "HTTP $($secondaryCustomerProject.Status)"
    $unassignedOperatorProject = $null
    foreach ($candidateProject in @($projectPage.data.items)) {
      $candidateResponse = Invoke-Http -Method GET `
        -Uri "$ApiBaseUrl/projects/$($candidateProject.id)" -Headers $operatorHeaders
      if ($candidateResponse.Status -eq 403) {
        $unassignedOperatorProject = $candidateResponse
        break
      }
    }
    Add-Check -Name 'unassigned operator cannot access customer project' -Passed (
      $null -ne $unassignedOperatorProject -and $unassignedOperatorProject.Status -eq 403
    ) -Detail 'at least one customer project outside the operator assignment scope is denied'
  }

  if ($CreateActivityClosure) {
    if ($ApiBaseUrl -notmatch '127\.0\.0\.1|localhost') {
      throw 'CreateActivityClosure is local-demo only.'
    }
    $suffix = Get-Date -Format 'yyyyMMddHHmmss'
    $eventTime = [DateTimeOffset]::UtcNow.AddDays(7).ToString('o')
    $dueAt = [DateTimeOffset]::UtcNow.AddDays(10).ToString('o')
    $rootRequirementPayload = @{
      title = "Local QA conference-$suffix"; requestedService = 'NEWS_CONFERENCE'
      conferenceContactName = 'QA Contact'; conferenceContactMobile = '13800000003'
    }
    $rootSubmissionKey = [Guid]::NewGuid().ToString()
    $concurrentRootResponses = @(
      Invoke-ConcurrentRequirementPair -Headers $customerHeaders `
        -Payload $rootRequirementPayload -IdempotencyKey $rootSubmissionKey
    )
    $concurrentRootPayloads = @(
      $concurrentRootResponses | ForEach-Object {
        if ([string]::IsNullOrWhiteSpace($_.Content)) { $null } else { $_.Content | ConvertFrom-Json }
      }
    )
    $concurrentRootProjectIds = @(
      $concurrentRootPayloads | ForEach-Object { [long]$_.data.projectId } | Select-Object -Unique
    )
    Add-Check -Name 'concurrent retries create one requirement project' -Passed (
      $concurrentRootResponses.Count -eq 2 -and
      @($concurrentRootResponses | Where-Object { $_.Status -eq 200 }).Count -eq 2 -and
      $concurrentRootProjectIds.Count -eq 1 -and
      $concurrentRootProjectIds[0] -gt 0
    ) -Detail 'two simultaneous submissions with one request identity return the same project'
    $rootProjectId = [long]$concurrentRootProjectIds[0]
    $reusedSubmissionHeaders = @{
      Authorization = [string]$customerHeaders.Authorization
      'Idempotency-Key' = $rootSubmissionKey
    }
    $reusedSubmission = Invoke-Http -Method POST -Uri "$ApiBaseUrl/requirements" `
      -Headers $reusedSubmissionHeaders -Body @{
        title = "Local QA conflicting conference-$suffix"; requestedService = 'NEWS_CONFERENCE'
        conferenceContactName = 'QA Contact'; conferenceContactMobile = '13800000003'
      }
    $reusedSubmissionCode = if ([string]::IsNullOrWhiteSpace($reusedSubmission.Content)) {
      ''
    } else {
      ($reusedSubmission.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'one idempotency key cannot identify different order content' -Passed (
      $reusedSubmission.Status -eq 409 -and
      $reusedSubmissionCode -eq 'IDEMPOTENCY_KEY_REUSED'
    ) -Detail 'a request identity is permanently bound to the original customer order payload'
    $minimalConference = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $customerHeaders
    $conferenceWorkItems = @($minimalConference.data.conferenceWorkItems | Sort-Object { [int]$_.sortOrder })
    $expectedConferenceTitles = @(
      '确认发布目标与项目范围',
      '确定议程、嘉宾与发言分工',
      '落实场地、舞台与现场动线',
      '准备新闻材料与问答口径',
      '建立拟邀媒体清单',
      '执行媒体邀请与到场确认',
      '统筹现场接待、采访与采写',
      '安排会后发稿与渠道发布',
      '核验成果并完成项目复盘'
    )
    $expectedConferencePhases = @(
      'PRE_EVENT', 'PRE_EVENT', 'PRE_EVENT', 'PRE_EVENT', 'PRE_EVENT', 'PRE_EVENT',
      'ONSITE', 'POST_EVENT', 'POST_EVENT'
    )
    $conferenceOptionalDetailsRemainBlank =
      [string]::IsNullOrWhiteSpace([string]$minimalConference.data.project.eventTime) -and
      [string]::IsNullOrWhiteSpace([string]$minimalConference.data.project.eventLocation) -and
      [string]::IsNullOrWhiteSpace([string]$minimalConference.data.conference.eventTime) -and
      [string]::IsNullOrWhiteSpace([string]$minimalConference.data.conference.eventLocation) -and
      [string]::IsNullOrWhiteSpace([string]$minimalConference.data.conference.conferenceScale) -and
      [string]::IsNullOrWhiteSpace([string]$minimalConference.data.conference.mediaGoal)
    Add-Check -Name 'conference accepts the three required first-submission fields' -Passed (
      $minimalConference.data.project.requestedService -eq 'NEWS_CONFERENCE' -and
      $minimalConference.data.conference.contactName -eq 'QA Contact' -and
      $minimalConference.data.conference.contactMobile -eq '13800000003' -and
      $conferenceOptionalDetailsRemainBlank
    ) -Detail 'title, conference contact and mobile create a project; schedule, venue and media details may be supplied later'
    $conferenceChecklistProjectionSafe = -not (Find-ExactForbiddenKey $conferenceWorkItems @('id','assignedOperatorId','operatorName','note'))
    Add-Check -Name 'conference creates the nine-item customer-safe execution checklist' -Passed (
      $conferenceWorkItems.Count -eq 9 -and
      (@($conferenceWorkItems | ForEach-Object { [int]$_.sortOrder }) -join ',') -eq '1,2,3,4,5,6,7,8,9' -and
      (@($conferenceWorkItems | ForEach-Object { $_.title }) -join '|') -eq ($expectedConferenceTitles -join '|') -and
      (@($conferenceWorkItems | ForEach-Object { $_.phase }) -join ',') -eq ($expectedConferencePhases -join ',') -and
      @($conferenceWorkItems | Where-Object { $null -ne $_.dueAt }).Count -eq 0 -and
      $conferenceChecklistProjectionSafe
    ) -Detail 'minimal conference intake creates six pre-event, one onsite and two post-event customer-safe work items'
    $conferenceCandidateName = "Local QA conference candidate-$suffix"
    $conferenceCandidateAdded = Invoke-Json -Method POST `
      -Path "/projects/$rootProjectId/conference-media-candidates" -Headers $customerHeaders -Body @{
        candidateKey = "MANUAL:conference-candidate-$suffix"
        candidateType = 'MANUAL'
        displayName = $conferenceCandidateName
        available = $true
      }
    $conferenceCandidateAdminDetail = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $adminHeaders
    $conferenceCandidate = @($conferenceCandidateAdminDetail.data.conferenceMediaCandidates | Where-Object {
      $_.displayName -eq $conferenceCandidateName
    } | Select-Object -First 1)
    $conferenceCandidateId = if ($conferenceCandidate.Count -eq 1) { [long]$conferenceCandidate[0].id } else { 0 }
    Add-Check -Name 'conference candidate begins as a selected, pending-verification target' -Passed (
      $conferenceCandidateAdded.data.displayName -eq $conferenceCandidateName -and
      $conferenceCandidateId -gt 0 -and $conferenceCandidate[0].status -eq 'CANDIDATE'
    ) -Detail 'the customer can add a manual target, but no invitation or attendance fact is created'
    $skippedConferenceCandidate = Invoke-Http -Method PATCH `
      -Uri "$ApiBaseUrl/operator/projects/$rootProjectId/conference-media-candidates/$conferenceCandidateId" `
      -Headers $adminHeaders -Body @{
        status = 'ATTENDING'; expectedStatus = 'CANDIDATE'
        note = 'This local-only skipped state must be rejected.'
      }
    $skippedConferenceCandidateCode = if ([string]::IsNullOrWhiteSpace($skippedConferenceCandidate.Content)) {
      ''
    } else {
      ($skippedConferenceCandidate.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'conference candidate cannot skip confirmation and invitation' -Passed (
      $skippedConferenceCandidate.Status -eq 409 -and
      $skippedConferenceCandidateCode -eq 'INVALID_CONFERENCE_MEDIA_CANDIDATE_TRANSITION'
    ) -Detail 'a selected candidate cannot be labelled as attending before an invitation is recorded'
    Invoke-Json -Method PATCH `
      -Path "/operator/projects/$rootProjectId/conference-media-candidates/$conferenceCandidateId" `
      -Headers $adminHeaders -Body @{ status = 'READY_TO_INVITE'; expectedStatus = 'CANDIDATE'; note = $null } | Out-Null
    Invoke-Json -Method PATCH `
      -Path "/operator/projects/$rootProjectId/conference-media-candidates/$conferenceCandidateId" `
      -Headers $adminHeaders -Body @{ status = 'INVITED'; expectedStatus = 'READY_TO_INVITE'; note = 'Synthetic local QA invitation record; no real media contact occurred.' } | Out-Null
    Invoke-Json -Method PATCH `
      -Path "/operator/projects/$rootProjectId/conference-media-candidates/$conferenceCandidateId" `
      -Headers $adminHeaders -Body @{ status = 'ATTENDING'; expectedStatus = 'INVITED'; note = 'Synthetic local QA attendance confirmation; no real media contact occurred.' } | Out-Null
    $finalConferenceCandidate = Invoke-Http -Method PATCH `
      -Uri "$ApiBaseUrl/operator/projects/$rootProjectId/conference-media-candidates/$conferenceCandidateId" `
      -Headers $adminHeaders -Body @{
        status = 'INVITED'; expectedStatus = 'ATTENDING'
        note = 'This local-only terminal-state regression must be rejected.'
      }
    $finalConferenceCandidateCode = if ([string]::IsNullOrWhiteSpace($finalConferenceCandidate.Content)) {
      ''
    } else {
      ($finalConferenceCandidate.Content | ConvertFrom-Json).code
    }
    $conferenceCandidateCustomerDetail = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $customerHeaders
    $conferenceCandidateCustomerSafe = @($conferenceCandidateCustomerDetail.data.conferenceMediaCandidates | Where-Object {
      $_.displayName -eq $conferenceCandidateName
    } | Select-Object -First 1)
    Add-Check -Name 'conference candidate timeline is ordered, final and customer-safe' -Passed (
      $finalConferenceCandidate.Status -eq 409 -and
      $finalConferenceCandidateCode -eq 'CONFERENCE_MEDIA_CANDIDATE_FINALIZED' -and
      $conferenceCandidateCustomerSafe.Count -eq 1 -and
      $conferenceCandidateCustomerSafe[0].status -eq 'ATTENDING' -and
      -not (Find-ExactForbiddenKey $conferenceCandidateCustomerSafe @('id','candidateKey','candidateType','mediaId','reporterId','note','operatorName'))
    ) -Detail 'the final contact outcome remains visible without internal identifiers, notes or upstream data'
    $renamedConferenceUpload = Invoke-FileUpload -Headers $customerHeaders -ProjectId $rootProjectId `
      -FileName 'renamed-not-a-document.pdf' -Text 'This content is deliberately not a PDF.' `
      -ContentType 'application/pdf'
    $renamedConferenceUploadCode = if ([string]::IsNullOrWhiteSpace($renamedConferenceUpload.Content)) {
      ''
    } else {
      ($renamedConferenceUpload.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'renamed upload content is rejected before file metadata is committed' -Passed (
      $renamedConferenceUpload.Status -eq 400 -and
      $renamedConferenceUploadCode -eq 'FILE_CONTENT_MISMATCH'
    ) -Detail 'declared PDF content must carry a matching file signature'
    $conferenceUpload = Invoke-FileUpload -Headers $customerHeaders -ProjectId $rootProjectId
    $conferenceUploadData = if ([string]::IsNullOrWhiteSpace($conferenceUpload.Content)) { $null } else { $conferenceUpload.Content | ConvertFrom-Json }
    Add-Check -Name 'conference material upload is tied to the new customer project' -Passed (
      $conferenceUpload.Status -eq 200 -and $null -ne $conferenceUploadData -and
      -not [string]::IsNullOrWhiteSpace([string]$conferenceUploadData.data.fileNo)
    ) -Detail 'local QA material is uploaded through the authenticated project file endpoint'
    $conferenceFileNo = [System.Uri]::EscapeDataString([string]$conferenceUploadData.data.fileNo)
    $conferenceDownload = Invoke-Http -Method GET -Uri "$ApiBaseUrl/files/$conferenceFileNo" -Headers $customerHeaders
    Add-Check -Name 'conference material download is private and forces attachment handling' -Passed (
      $conferenceDownload.Status -eq 200 -and
      [string]$conferenceDownload.Headers['Cache-Control'] -match 'no-store' -and
      [string]$conferenceDownload.Headers['Cache-Control'] -match 'private' -and
      [string]$conferenceDownload.Headers['X-Content-Type-Options'] -eq 'nosniff' -and
      [string]$conferenceDownload.Headers['Content-Disposition'] -match '^attachment;'
    ) -Detail 'authenticated project materials are not cacheable or browser-rendered inline'
    $foreignConferenceDownload = Invoke-Http -Method GET -Uri "$ApiBaseUrl/files/$conferenceFileNo" -Headers $secondaryCustomerHeaders
    Add-Check -Name 'second customer cannot download another customer project material' -Passed (
      $foreignConferenceDownload.Status -eq 403
    ) -Detail "HTTP $($foreignConferenceDownload.Status)"
    $conferenceAfterUpload = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $customerHeaders
    $conferenceFiles = @($conferenceAfterUpload.data.files)
    Add-Check -Name 'conference material metadata stays customer-safe' -Passed (
      $conferenceFiles.Count -eq 1 -and
      -not (Find-ExactForbiddenKey $conferenceFiles @('storageKey','checksumSha256','uploaderId','supplierId','costPrice','upstreamReference'))
    ) -Detail 'customer sees only the permitted file metadata after upload'
    $foreignConferenceUpload = Invoke-FileUpload -Headers $secondaryCustomerHeaders -ProjectId $rootProjectId
    Add-Check -Name 'second customer cannot upload material to another customer project' -Passed (
      $foreignConferenceUpload.Status -eq 403
    ) -Detail "HTTP $($foreignConferenceUpload.Status)"
    $foreignActivityRequest = Invoke-Http -Method POST -Uri "$ApiBaseUrl/requirements" -Headers $secondaryCustomerHeaders -Body @{
      title = "Local QA blocked foreign activity-$suffix"; requestedService = 'MEDIA_PR'
      facts = 'This request must not be linked to another customer activity.'
      relatedProjectId = $rootProjectId; dueAt = $dueAt
    }
    $foreignActivityCode = if ([string]::IsNullOrWhiteSpace($foreignActivityRequest.Content)) { '' } else { ($foreignActivityRequest.Content | ConvertFrom-Json).code }
    Add-Check -Name 'customer cannot attach a service order to another customer activity' -Passed (
      $foreignActivityRequest.Status -eq 400 -and $foreignActivityCode -eq 'RELATED_PROJECT_INVALID'
    ) -Detail 'activity linkage requires the same customer and organization; rejected requests create no cross-customer project'
    $writingProjectId = Submit-Requirement -Headers $customerHeaders -Payload @{
      title = "Local QA onsite writing-$suffix"; requestedService = 'ONSITE_WRITING'; relatedProjectId = $rootProjectId
      facts = 'Confirmed local QA event facts.'; eventTime = $eventTime; eventLocation = 'Shenzhen'
      serviceDays = 1; writerCount = 1; onsiteContactName = 'QA Contact'; onsiteContactMobile = '13800000003'; dueAt = $dueAt
    }
    $mediaProjectId = Submit-Requirement -Headers $customerHeaders -Payload @{
      title = "Local QA media invitation-$suffix"; requestedService = 'MEDIA_PR'; relatedProjectId = $rootProjectId
      facts = 'Confirmed local QA media invitation facts.'; dueAt = $dueAt
    }
    $directProjectId = Submit-Requirement -Headers $customerHeaders -Payload @{
      title = "Local QA direct publishing-$suffix"; requestedService = 'DIRECT_PUBLISHING'; relatedProjectId = $rootProjectId
      facts = 'Confirmed local QA publishing facts.'; dueAt = $dueAt
      sourceManuscriptId = [long]$approvedSource[0].manuscriptId
      sourceManuscriptVersionId = [long]$approvedSource[0].versionId
    }

    $adminWritingAssignments = Invoke-Json -Method GET -Path '/writing-assignments' -Headers $adminHeaders
    $writingAssignment = @($adminWritingAssignments.data | Where-Object {
      [long]$_.projectId -eq $writingProjectId
    } | Select-Object -First 1)
    $availableWriters = Invoke-Json -Method GET -Path '/admin/writers' -Headers $adminHeaders
    $availableWriter = @($availableWriters.data | Where-Object {
      $_.availabilityStatus -eq 'AVAILABLE' -and $_.status -eq 'ACTIVE'
    } | Select-Object -First 1)
    Add-Check -Name 'new onsite-writing order has an available writer assignment path' -Passed (
      $writingAssignment.Count -eq 1 -and $writingAssignment[0].status -eq 'WAITING_MATCH' -and
      $availableWriter.Count -eq 1
    ) -Detail 'the local operator account is an active test writer and the new order begins at matching'
    $offeredWritingAssignment = Invoke-Json -Method POST `
      -Path "/admin/writing-assignments/$($writingAssignment[0].id)/offer" `
      -Headers $adminHeaders -Body @{
        writerProfileId = [long]$availableWriter[0].id
        distanceKm = 0
      }
    Add-Check -Name 'administrator offers the onsite-writing assignment' -Passed (
      $offeredWritingAssignment.data.memberStatus -eq 'OFFERED' -and
      -not ($offeredWritingAssignment.data.PSObject.Properties.Name -contains 'status')
    ) -Detail 'the registered writer seat is offered without claiming that a multi-writer order is fully assigned'
    $acceptedWritingAssignment = Invoke-Json -Method POST `
      -Path "/writing-assignments/$($writingAssignment[0].id)/respond" `
      -Headers $operatorHeaders -Body @{ decision = 'ACCEPT'; note = 'Local-only assignment acceptance.' }
    Add-Check -Name 'registered writer accepts the onsite-writing assignment' -Passed (
      $acceptedWritingAssignment.data.memberStatus -eq 'ACCEPTED' -and
      -not ($acceptedWritingAssignment.data.PSObject.Properties.Name -contains 'status')
    ) -Detail 'the registered writer seat is accepted without claiming that a multi-writer order is fully filled'
    $firstWritingSubmission = Invoke-Json -Method POST `
      -Path "/operator/projects/$writingProjectId/manuscripts" `
      -Headers $operatorHeaders -Body @{
        title = "Local QA onsite writing first draft-$suffix"
        summary = 'Local-only first draft for customer-review regression.'
        content = 'This local-only manuscript verifies the customer review and revision state chain.'
        changeNote = 'First draft'
      }
    Add-Check -Name 'writer submits the first onsite-writing draft' -Passed (
      -not [string]::IsNullOrWhiteSpace([string]$firstWritingSubmission.data.manuscriptId)
    ) -Detail 'the draft enters customer review without creating a media or publishing task'
    $writingAwaitingReview = Invoke-Json -Method GET -Path "/projects/$writingProjectId" -Headers $customerHeaders
    $firstReviewVersion = @($writingAwaitingReview.data.versions | Where-Object {
      $_.status -eq 'CLIENT_REVIEW'
    } | Select-Object -First 1)
    $writingAwaitingReviewOrderPage = Invoke-Json -Method GET `
      -Path '/order-records?serviceType=ONSITE_WRITING&page=1&pageSize=100' `
      -Headers $customerHeaders
    $writingAwaitingReviewOrder = @($writingAwaitingReviewOrderPage.data.items | Where-Object {
      [long]$_.projectId -eq $writingProjectId
    } | Select-Object -First 1)
    Add-Check -Name 'onsite-writing order waits for customer review after delivery' -Passed (
      $firstReviewVersion.Count -eq 1 -and
      $writingAwaitingReview.data.project.status -eq 'CLIENT_REVIEW' -and
      $writingAwaitingReviewOrder.Count -eq 1 -and
      $writingAwaitingReviewOrder[0].status -eq 'WAITING_CONFIRMATION'
    ) -Detail 'project, manuscript and customer order show one consistent review state'
    $duplicateWritingSubmission = Invoke-Http -Method POST `
      -Uri "$ApiBaseUrl/operator/projects/$writingProjectId/manuscripts" `
      -Headers $operatorHeaders -Body @{
        title = "Rejected duplicate onsite writing draft-$suffix"
        content = 'This draft must not replace a version that is already waiting for customer review.'
      }
    $duplicateWritingCode = if ([string]::IsNullOrWhiteSpace($duplicateWritingSubmission.Content)) {
      ''
    } else {
      ($duplicateWritingSubmission.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'writer cannot submit another draft while customer review is pending' -Passed (
      $duplicateWritingSubmission.Status -eq 409 -and
      $duplicateWritingCode -eq 'WRITING_REVIEW_PENDING'
    ) -Detail 'one reviewable version remains authoritative until the customer responds'
    $returnedWritingDraft = Invoke-Json -Method POST `
      -Path "/manuscripts/$($firstWritingSubmission.data.manuscriptId)/review" `
      -Headers $customerHeaders -Body @{
        versionId = [long]$firstReviewVersion[0].id
        decision = 'RETURN'
        comment = 'Local-only revision request.'
      }
    Add-Check -Name 'customer returns the onsite-writing draft for revision' -Passed (
      $returnedWritingDraft.data.status -eq 'CLIENT_RETURNED'
    ) -Detail 'customer review reopens only the independent writing task'
    $writingAfterReturn = Invoke-Json -Method GET -Path "/projects/$writingProjectId" -Headers $customerHeaders
    $writingAssignmentsAfterReturn = Invoke-Json -Method GET -Path '/writing-assignments' -Headers $operatorHeaders
    $reopenedWritingAssignment = @($writingAssignmentsAfterReturn.data | Where-Object {
      [long]$_.projectId -eq $writingProjectId
    } | Select-Object -First 1)
    $writingAfterReturnOrderPage = Invoke-Json -Method GET `
      -Path '/order-records?serviceType=ONSITE_WRITING&page=1&pageSize=100' `
      -Headers $customerHeaders
    $writingAfterReturnOrder = @($writingAfterReturnOrderPage.data.items | Where-Object {
      [long]$_.projectId -eq $writingProjectId
    } | Select-Object -First 1)
    Add-Check -Name 'returned onsite-writing draft restores the execution state' -Passed (
      $writingAfterReturn.data.project.status -eq 'IN_PROGRESS' -and
      $reopenedWritingAssignment.Count -eq 1 -and $reopenedWritingAssignment[0].status -eq 'ACCEPTED' -and
      $writingAfterReturnOrder.Count -eq 1 -and $writingAfterReturnOrder[0].status -eq 'IN_PROGRESS'
    ) -Detail 'project, assignment and order reopen together for the assigned writer'
    $revisedWritingSubmission = Invoke-Json -Method POST `
      -Path "/operator/projects/$writingProjectId/manuscripts" `
      -Headers $operatorHeaders -Body @{
        title = "Local QA onsite writing approved draft-$suffix"
        summary = 'Local-only revised draft for final acceptance regression.'
        content = 'This revised local-only manuscript addresses the recorded customer review request.'
        changeNote = 'Revision completed'
      }
    $writingRevisedProject = Invoke-Json -Method GET -Path "/projects/$writingProjectId" -Headers $customerHeaders
    $revisedReviewVersion = @($writingRevisedProject.data.versions | Where-Object {
      $_.status -eq 'CLIENT_REVIEW'
    } | Select-Object -First 1)
    Add-Check -Name 'writer resubmits one reviewable revision' -Passed (
      [long]$revisedWritingSubmission.data.manuscriptId -eq [long]$firstWritingSubmission.data.manuscriptId -and
      $revisedReviewVersion.Count -eq 1
    ) -Detail 'the revision stays in the same manuscript and customer order'
    $approvedWritingDraft = Invoke-Json -Method POST `
      -Path "/manuscripts/$($revisedWritingSubmission.data.manuscriptId)/review" `
      -Headers $customerHeaders -Body @{
        versionId = [long]$revisedReviewVersion[0].id
        decision = 'APPROVE'
        comment = 'Local-only final acceptance.'
      }
    Add-Check -Name 'customer confirms the onsite-writing final draft' -Passed (
      $approvedWritingDraft.data.status -eq 'CLIENT_APPROVED'
    ) -Detail 'customer approval is the terminal writing acceptance fact'
    $writingFinalProject = Invoke-Json -Method GET -Path "/projects/$writingProjectId" -Headers $customerHeaders
    $writingFinalOrderPage = Invoke-Json -Method GET `
      -Path '/order-records?serviceType=ONSITE_WRITING&page=1&pageSize=100' `
      -Headers $customerHeaders
    $writingFinalOrder = @($writingFinalOrderPage.data.items | Where-Object {
      [long]$_.projectId -eq $writingProjectId
    } | Select-Object -First 1)
    $writingFinalTaskPage = Invoke-Json -Method GET `
      -Path '/task-records?page=1&pageSize=100' -Headers $customerHeaders
    $writingFinalTask = @($writingFinalTaskPage.data.items | Where-Object {
      [long]$_.projectId -eq $writingProjectId -and $_.serviceType -eq 'ONSITE_WRITING'
    } | Select-Object -First 1)
    Add-Check -Name 'onsite-writing project, task and order close together' -Passed (
      $writingFinalProject.data.project.status -eq 'COMPLETED' -and
      $writingFinalOrder.Count -eq 1 -and $writingFinalOrder[0].status -eq 'CLIENT_ACCEPTED' -and
      $writingFinalTask.Count -eq 1 -and $writingFinalTask[0].status -eq 'CLIENT_ACCEPTED'
    ) -Detail 'the customer can query one final accepted state across all three records'
    $reopenAcceptedWriting = Invoke-Http -Method POST `
      -Uri "$ApiBaseUrl/operator/projects/$writingProjectId/manuscripts" `
      -Headers $operatorHeaders -Body @{
        title = "Rejected post-acceptance draft-$suffix"
        content = 'This draft must not reopen a customer-approved writing order.'
      }
    $reopenAcceptedWritingCode = if ([string]::IsNullOrWhiteSpace($reopenAcceptedWriting.Content)) {
      ''
    } else {
      ($reopenAcceptedWriting.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'accepted onsite-writing order cannot receive another draft' -Passed (
      $reopenAcceptedWriting.Status -eq 409 -and
      $reopenAcceptedWritingCode -eq 'WRITING_ORDER_FINALIZED'
    ) -Detail 'the final customer acceptance cannot be overwritten by an operator'

    $adminConference = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $adminHeaders
    $adminConferenceWorkItems = @($adminConference.data.conferenceWorkItems | Sort-Object {
      [int]$_.sortOrder
    })
    Add-Check -Name 'conference execution exposes nine internal work items to authorised staff' -Passed (
      $adminConferenceWorkItems.Count -eq 9 -and
      @($adminConferenceWorkItems | Where-Object { $null -eq $_.id }).Count -eq 0
    ) -Detail 'staff can update the same checklist that the customer sees through a safe projection'
    $conferenceStarted = Invoke-Json -Method PATCH `
      -Path "/operator/projects/$rootProjectId/conference-work-items/$($adminConferenceWorkItems[0].id)" `
      -Headers $adminHeaders -Body @{
        status = 'IN_PROGRESS'
        expectedStatus = $adminConferenceWorkItems[0].status
        note = 'Local-only conference execution regression.'
      }
    Add-Check -Name 'conference begins when its first work item starts' -Passed (
      $conferenceStarted.data.status -eq 'IN_PROGRESS'
    ) -Detail 'the checklist is the execution source of truth'
    $conferenceExecutingProject = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $customerHeaders
    $conferenceExecutingOrderPage = Invoke-Json -Method GET `
      -Path '/order-records?serviceType=NEWS_CONFERENCE&page=1&pageSize=100' `
      -Headers $customerHeaders
    $conferenceExecutingOrder = @($conferenceExecutingOrderPage.data.items | Where-Object {
      [long]$_.projectId -eq $rootProjectId
    } | Select-Object -First 1)
    Add-Check -Name 'conference project and order enter execution together' -Passed (
      $conferenceExecutingProject.data.project.status -eq 'IN_PROGRESS' -and
      $conferenceExecutingProject.data.conference.status -eq 'EXECUTING' -and
      $conferenceExecutingOrder.Count -eq 1 -and $conferenceExecutingOrder[0].status -eq 'EXECUTING'
    ) -Detail 'the customer sees the same execution state in the project and order ledger'
    $conferenceCurrentAdmin = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $adminHeaders
    $conferenceCurrentWorkItems = @($conferenceCurrentAdmin.data.conferenceWorkItems | Sort-Object {
      [int]$_.sortOrder
    })
    foreach ($conferenceWorkItem in $conferenceCurrentWorkItems) {
      Invoke-Json -Method PATCH `
        -Path "/operator/projects/$rootProjectId/conference-work-items/$($conferenceWorkItem.id)" `
        -Headers $adminHeaders -Body @{
          status = 'COMPLETED'
          expectedStatus = $conferenceWorkItem.status
          note = 'Local-only completed conference work item.'
        } | Out-Null
    }
    $conferenceFinalProject = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $customerHeaders
    $conferenceFinalOrderPage = Invoke-Json -Method GET `
      -Path '/order-records?serviceType=NEWS_CONFERENCE&page=1&pageSize=100' `
      -Headers $customerHeaders
    $conferenceFinalOrder = @($conferenceFinalOrderPage.data.items | Where-Object {
      [long]$_.projectId -eq $rootProjectId
    } | Select-Object -First 1)
    $conferenceFinalTaskPage = Invoke-Json -Method GET `
      -Path '/task-records?page=1&pageSize=100' -Headers $customerHeaders
    $conferenceFinalTasks = @($conferenceFinalTaskPage.data.items | Where-Object {
      [long]$_.projectId -eq $rootProjectId -and $_.serviceType -eq 'NEWS_CONFERENCE'
    })
    Add-Check -Name 'conference project, checklist and order close together' -Passed (
      $conferenceFinalProject.data.project.status -eq 'COMPLETED' -and
      $conferenceFinalProject.data.conference.status -eq 'COMPLETED' -and
      @($conferenceFinalProject.data.conferenceWorkItems | Where-Object {
        $_.status -ne 'COMPLETED'
      }).Count -eq 0 -and
      $conferenceFinalOrder.Count -eq 1 -and $conferenceFinalOrder[0].status -eq 'COMPLETED' -and
      $conferenceFinalTasks.Count -eq 9 -and
      @($conferenceFinalTasks | Where-Object { $_.status -ne 'COMPLETED' }).Count -eq 0
    ) -Detail 'all nine completed work items produce one consistent customer-visible final state'
    $reopenCompletedConferenceItem = Invoke-Http -Method PATCH `
      -Uri "$ApiBaseUrl/operator/projects/$rootProjectId/conference-work-items/$($conferenceCurrentWorkItems[0].id)" `
      -Headers $adminHeaders -Body @{
        status = 'IN_PROGRESS'
        expectedStatus = 'COMPLETED'
        note = 'This local-only regression must be rejected.'
      }
    $reopenCompletedConferenceItemCode = if ([string]::IsNullOrWhiteSpace($reopenCompletedConferenceItem.Content)) {
      ''
    } else {
      ($reopenCompletedConferenceItem.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'completed conference work item cannot be reopened' -Passed (
      $reopenCompletedConferenceItem.Status -eq 409 -and
      $reopenCompletedConferenceItemCode -eq 'CONFERENCE_WORK_ITEM_FINALIZED'
    ) -Detail 'the final checklist state is protected from operator-side regression'

    $manualMediaName = "Local QA manual invitation target-$suffix"
    $wrongProjectPlan = Invoke-Http -Method POST -Uri "$ApiBaseUrl/projects/$rootProjectId/publish-plans" -Headers $customerHeaders -Body @{
      planName = "Local QA rejected mixed project-$suffix"
      selections = @(@{
        mediaName = $manualMediaName
        mediaCandidate = @{
          candidateKey = "MANUAL:wrong-project-$suffix"; candidateType = 'MANUAL'
          displayName = $manualMediaName; available = $true
        }
      })
    }
    $wrongProjectCode = if ([string]::IsNullOrWhiteSpace($wrongProjectPlan.Content)) { '' } else { ($wrongProjectPlan.Content | ConvertFrom-Json).code }
    Add-Check -Name 'manual media invitation cannot use a conference project' -Passed (
      $wrongProjectPlan.Status -eq 400 -and $wrongProjectCode -eq 'MEDIA_PR_PROJECT_REQUIRED'
    ) -Detail 'the invitation plan must belong to its independently ordered service project'

    $exclusiveMediaPlan = Invoke-Http -Method POST -Uri "$ApiBaseUrl/projects/$mediaProjectId/publish-plans" -Headers $customerHeaders -Body @{
      planName = "Local QA unavailable exclusive invitation-$suffix"
      exclusiveMediaPr = $true
      lockExpiresAt = [DateTimeOffset]::UtcNow.AddDays(2).ToString('o')
      selections = @(@{
        mediaName = $manualMediaName
        mediaCandidate = @{
          candidateKey = "MANUAL:exclusive-unavailable-$suffix"; candidateType = 'MANUAL'
          displayName = $manualMediaName; available = $true
        }
      })
    }
    $exclusiveMediaCode = if ([string]::IsNullOrWhiteSpace($exclusiveMediaPlan.Content)) { '' } else { ($exclusiveMediaPlan.Content | ConvertFrom-Json).code }
    Add-Check -Name 'online exclusive media invitation is unavailable' -Passed (
      $exclusiveMediaPlan.Status -eq 400 -and $exclusiveMediaCode -eq 'MEDIA_PR_EXCLUSIVE_NOT_AVAILABLE'
    ) -Detail 'current customer flow only saves invitation candidates for project verification'

    $mediaPlanPayload = @{
      planName = "Local QA manual media invitation plan-$suffix"
      objective = 'Local-only workflow verification; no media contact is made.'
      selections = @(@{
        mediaName = $manualMediaName
        note = 'Local-only workflow verification; no media contact is made.'
        mediaCandidate = @{
          candidateKey = "MANUAL:customer-supplement-$suffix"; candidateType = 'MANUAL'
          displayName = $manualMediaName; available = $true
        }
      })
    }
    $mediaPlanIdempotencyKey = "local-qa-media-plan-$suffix"
    $mediaPlanHeaders = @{}
    foreach ($header in $customerHeaders.GetEnumerator()) {
      $mediaPlanHeaders[[string]$header.Key] = [string]$header.Value
    }
    $mediaPlanHeaders['Idempotency-Key'] = $mediaPlanIdempotencyKey
    $missingMediaPlanKey = Invoke-Http -Method POST `
      -Uri "$ApiBaseUrl/projects/$mediaProjectId/publish-plans" `
      -Headers $customerHeaders -SkipPublishPlanIdempotency -Body $mediaPlanPayload
    $missingMediaPlanKeyCode = if ([string]::IsNullOrWhiteSpace($missingMediaPlanKey.Content)) {
      ''
    } else {
      ($missingMediaPlanKey.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'publish plan requires an idempotency key' -Passed (
      $missingMediaPlanKey.Status -eq 400 -and
      $missingMediaPlanKeyCode -eq 'IDEMPOTENCY_KEY_REQUIRED'
    ) -Detail 'a missing request identity cannot create a plan'
    $mediaPlan = Invoke-Json -Method POST -Path "/projects/$mediaProjectId/publish-plans" `
      -Headers $mediaPlanHeaders -Body $mediaPlanPayload
    $retriedMediaPlan = Invoke-Json -Method POST -Path "/projects/$mediaProjectId/publish-plans" `
      -Headers $mediaPlanHeaders -Body $mediaPlanPayload
    Add-Check -Name 'manual media invitation plan needs no execution channel' -Passed (
      $mediaPlan.data.status -eq 'WAITING_CONFIRMATION' -and [long]$mediaPlan.data.itemCount -eq 1 -and
      -not [string]::IsNullOrWhiteSpace([string]$mediaPlan.data.planNo) -and
      -not (Find-ExactForbiddenKey $mediaPlan.data @('id','planId'))
    ) -Detail 'a customer-supplemented target can be saved without a supplier, price, or live media catalogue'
    Add-Check -Name 'publish plan retry returns the original plan' -Passed (
      $retriedMediaPlan.data.planNo -eq $mediaPlan.data.planNo -and
      [long]$retriedMediaPlan.data.itemCount -eq [long]$mediaPlan.data.itemCount
    ) -Detail 'the same customer action cannot create a duplicate plan'
    $reusedMediaPlanPayload = @{
      planName = "Local QA changed media invitation plan-$suffix"
      objective = $mediaPlanPayload.objective
      selections = $mediaPlanPayload.selections
    }
    $reusedMediaPlanKey = Invoke-Http -Method POST `
      -Uri "$ApiBaseUrl/projects/$mediaProjectId/publish-plans" `
      -Headers $mediaPlanHeaders -Body $reusedMediaPlanPayload
    $reusedMediaPlanCode = if ([string]::IsNullOrWhiteSpace($reusedMediaPlanKey.Content)) {
      ''
    } else {
      ($reusedMediaPlanKey.Content | ConvertFrom-Json).code
    }
    Add-Check -Name 'publish plan key cannot be reused for changed content' -Passed (
      $reusedMediaPlanKey.Status -eq 409 -and
      $reusedMediaPlanCode -eq 'IDEMPOTENCY_KEY_REUSED'
    ) -Detail 'one request identity cannot conceal a different plan'
    $mediaPlanNo = [System.Uri]::EscapeDataString([string]$mediaPlan.data.planNo)
    $confirmedMediaPlan = Invoke-Json -Method POST -Path "/publish-plans/$mediaPlanNo/confirm" -Headers $customerHeaders
    $mediaTaskNos = @($confirmedMediaPlan.data.taskNos)
    Add-Check -Name 'confirmed manual invitation plan creates a customer-visible task' -Passed (
      $confirmedMediaPlan.data.status -eq 'CONFIRMED' -and $mediaTaskNos.Count -eq 1 -and
      -not (Find-ExactForbiddenKey $confirmedMediaPlan.data @('id','planId','taskIds'))
    ) -Detail 'task creation follows the customer confirmation step'
    $mediaTaskId = Resolve-AdminPublishTaskId -Headers $adminHeaders -TaskNo $mediaTaskNos[0]
    $adminMediaTask = Invoke-Json -Method GET -Path "/publish-tasks/$mediaTaskId" -Headers $adminHeaders
    Add-Check -Name 'manual invitation task is not marked as sent or externally bound' -Passed (
      $adminMediaTask.data.mediaInvitationStatus -eq 'PENDING' -and $null -eq $adminMediaTask.data.mediaInvitedAt -and
      $adminMediaTask.data.channelName -eq $manualMediaName
    ) -Detail 'only an operator can record an actual invitation after contact occurs'
    $customerMediaTask = Invoke-Json -Method GET -Path "/customer/publish-tasks/$($mediaTaskNos[0])" -Headers $customerHeaders
    Add-Check -Name 'customer manual invitation task exposes only safe invitation progress' -Passed (
      $customerMediaTask.data.mediaInvitationStatus -eq 'PENDING' -and
      $null -eq $customerMediaTask.data.mediaInvitedAt -and
      $null -eq $customerMediaTask.data.mediaRespondedAt -and
      -not (Find-ExactForbiddenKey $customerMediaTask.data @(
        'id','operatorName','executionNote','exceptionReason','supplierId','costPrice',
        'externalReporterId','upstreamReference','responseNote'
      ))
    ) -Detail 'customer sees verifiable milestones without contact notes, supplier data or internal identifiers'
    $customerInvitationUpdate = Invoke-Http -Method PATCH -Uri "$ApiBaseUrl/operator/publish-tasks/$mediaTaskId/media-invitation" -Headers $customerHeaders -Body @{
      status = 'INVITED'; note = 'This must be rejected because the customer cannot record internal outreach.'
    }
    Add-Check -Name 'customer cannot record media contact progress' -Passed ($customerInvitationUpdate.Status -eq 403) -Detail "HTTP $($customerInvitationUpdate.Status)"
    $missingInvitationNote = Invoke-Http -Method PATCH -Uri "$ApiBaseUrl/operator/publish-tasks/$mediaTaskId/media-invitation" -Headers $adminHeaders -Body @{
      status = 'INVITED'; note = ' '
    }
    $missingInvitationNoteCode = if ([string]::IsNullOrWhiteSpace($missingInvitationNote.Content)) { '' } else { ($missingInvitationNote.Content | ConvertFrom-Json).code }
    Add-Check -Name 'media invitation progress requires a factual note' -Passed (
      $missingInvitationNote.Status -eq 400 -and $missingInvitationNoteCode -eq 'MEDIA_INVITATION_NOTE_REQUIRED'
    ) -Detail 'a rejected empty-note update cannot create an invitation fact'
    $mediaTaskAfterMissingNote = Invoke-Json -Method GET -Path "/publish-tasks/$mediaTaskId" -Headers $adminHeaders
    Add-Check -Name 'rejected invitation update does not fabricate contact progress' -Passed (
      $mediaTaskAfterMissingNote.data.mediaInvitationStatus -eq 'PENDING' -and $null -eq $mediaTaskAfterMissingNote.data.mediaInvitedAt
    ) -Detail 'the task remains pending until an authorised operator records an actual contact'
    $exceptionalMediaTask = Invoke-Json -Method PATCH -Path "/operator/publish-tasks/$mediaTaskId" -Headers $adminHeaders -Body @{
      status = 'EXCEPTION'; exceptionReason = 'Local-only task exception regression check; no media contact was made.'
    }
    Add-Check -Name 'manual invitation task exception can be recorded without a manuscript lock' -Passed (
      $exceptionalMediaTask.data.status -eq 'EXCEPTION'
    ) -Detail 'a manual invitation task without a manuscript does not fail while closing a lock'
    $mediaTaskAfterException = Invoke-Json -Method GET -Path "/publish-tasks/$mediaTaskId" -Headers $adminHeaders
    Add-Check -Name 'media task exception does not fabricate a media decline' -Passed (
      $mediaTaskAfterException.data.mediaInvitationStatus -eq 'PENDING' -and $null -eq $mediaTaskAfterException.data.mediaInvitedAt
    ) -Detail 'task exceptions stay separate from verified invitation and response facts'
    $uninvitedMediaResult = Invoke-Http -Method POST -Uri "$ApiBaseUrl/operator/publish-tasks/$mediaTaskId/results" -Headers $adminHeaders -Body @{
      title = "Rejected uninvited media result-$suffix"
      url = "https://example.com/uninvited-media-result-$suffix"
      note = 'This local-only negative check must not create a reporting result before an invitation fact exists.'
    }
    $uninvitedMediaResultCode = if ([string]::IsNullOrWhiteSpace($uninvitedMediaResult.Content)) { '' } else { ($uninvitedMediaResult.Content | ConvertFrom-Json).code }
    Add-Check -Name 'media result requires a recorded invitation' -Passed (
      $uninvitedMediaResult.Status -eq 409 -and $uninvitedMediaResultCode -eq 'MEDIA_INVITATION_REQUIRED'
    ) -Detail 'a pending candidate cannot be converted directly into a reporting result'
    $mediaTaskAfterRejectedResult = Invoke-Json -Method GET -Path "/publish-tasks/$mediaTaskId" -Headers $adminHeaders
    Add-Check -Name 'rejected media result preserves the pending invitation fact' -Passed (
      $mediaTaskAfterRejectedResult.data.mediaInvitationStatus -eq 'PENDING' -and
      $null -eq $mediaTaskAfterRejectedResult.data.mediaInvitedAt
    ) -Detail 'a rejected result cannot fabricate invitation or reporting evidence'
    $invitedMediaTask = Invoke-Json -Method PATCH -Path "/operator/publish-tasks/$mediaTaskId/media-invitation" -Headers $adminHeaders -Body @{
      status = 'INVITED'
      note = 'Automated local QA synthetic invitation milestone; no real media contact occurred.'
    }
    Add-Check -Name 'recorded media invitation starts its execution task' -Passed (
      $invitedMediaTask.data.status -eq 'INVITED' -and $invitedMediaTask.data.taskStatus -eq 'IN_PROGRESS'
    ) -Detail 'the local-only invitation milestone and generic task state advance atomically'
    $customerInvitedMediaTask = Invoke-Json -Method GET -Path "/customer/publish-tasks/$($mediaTaskNos[0])" -Headers $customerHeaders
    Add-Check -Name 'customer can verify an invitation milestone without internal notes' -Passed (
      $customerInvitedMediaTask.data.status -eq 'IN_PROGRESS' -and
      $customerInvitedMediaTask.data.mediaInvitationStatus -eq 'INVITED' -and
      $null -ne $customerInvitedMediaTask.data.mediaInvitedAt -and
      -not (Find-ExactForbiddenKey $customerInvitedMediaTask.data @(
        'id','operatorName','executionNote','exceptionReason','supplierId','costPrice',
        'externalReporterId','upstreamReference','responseNote'
      ))
    ) -Detail 'a customer-safe timestamp proves progress while the communication record remains internal'
    $respondedMediaTask = Invoke-Json -Method PATCH -Path "/operator/publish-tasks/$mediaTaskId/media-invitation" -Headers $adminHeaders -Body @{
      status = 'RESPONDED'
      note = 'Automated local QA synthetic response milestone; no real media response occurred.'
    }
    Add-Check -Name 'media response retains the task in execution' -Passed (
      $respondedMediaTask.data.status -eq 'RESPONDED' -and $respondedMediaTask.data.taskStatus -eq 'IN_PROGRESS'
    ) -Detail 'a response milestone does not claim publication completion'
    $declinedMediaTask = Invoke-Json -Method PATCH -Path "/operator/publish-tasks/$mediaTaskId/media-invitation" -Headers $adminHeaders -Body @{
      status = 'DECLINED'
      note = 'Automated local QA synthetic decline milestone; no real media response occurred.'
    }
    Add-Check -Name 'declined media target closes without a fabricated result' -Passed (
      $declinedMediaTask.data.status -eq 'DECLINED' -and $declinedMediaTask.data.taskStatus -eq 'NOT_PROCEEDING'
    ) -Detail 'decline is a terminal invitation fact distinct from a completed publication'
    $customerDeclinedMediaTask = Invoke-Json -Method GET -Path "/customer/publish-tasks/$($mediaTaskNos[0])" -Headers $customerHeaders
    Add-Check -Name 'customer sees the closed invitation outcome and response timestamp' -Passed (
      $customerDeclinedMediaTask.data.status -eq 'NOT_PROCEEDING' -and
      $customerDeclinedMediaTask.data.mediaInvitationStatus -eq 'DECLINED' -and
      $null -ne $customerDeclinedMediaTask.data.mediaInvitedAt -and
      $null -ne $customerDeclinedMediaTask.data.mediaRespondedAt -and
      -not (Find-ExactForbiddenKey $customerDeclinedMediaTask.data @(
        'id','operatorName','executionNote','exceptionReason','supplierId','costPrice',
        'externalReporterId','upstreamReference','responseNote'
      ))
    ) -Detail 'customer-visible closure is factual and contains no internal communication detail'
    $declinedResultWrite = Invoke-Http -Method POST -Uri "$ApiBaseUrl/operator/publish-tasks/$mediaTaskId/results" -Headers $adminHeaders -Body @{
      title = "Rejected media result-$suffix"
      url = "https://example.com/rejected-media-result-$suffix"
      note = 'This synthetic result must not be accepted for a declined media target.'
    }
    $declinedResultCode = if ([string]::IsNullOrWhiteSpace($declinedResultWrite.Content)) { '' } else { ($declinedResultWrite.Content | ConvertFrom-Json).code }
    Add-Check -Name 'closed media target cannot receive a fabricated publication result' -Passed (
      $declinedResultWrite.Status -eq 409 -and $declinedResultCode -eq 'TASK_NOT_PROCEEDING'
    ) -Detail 'a declined invitation cannot be converted into publication evidence'
    $mediaProjectAfterDecline = Invoke-Json -Method GET -Path "/projects/$mediaProjectId" -Headers $customerHeaders
    $mediaPlansAfterDecline = Invoke-Json -Method GET -Path "/projects/$mediaProjectId/publish-plans" -Headers $customerHeaders
    $closedMediaPlan = @($mediaPlansAfterDecline.data | Where-Object { $_.planNo -eq $mediaPlan.data.planNo } | Select-Object -First 1)
    $mediaIntakeTasks = @($mediaProjectAfterDecline.data.serviceIntakeTasks | Where-Object { $_.serviceType -eq 'MEDIA_PR' })
    Add-Check -Name 'media plan, project and intake close from the invitation outcome' -Passed (
      $mediaProjectAfterDecline.data.project.status -eq 'COMPLETED' -and
      $closedMediaPlan.Count -eq 1 -and $closedMediaPlan[0].status -eq 'COMPLETED' -and
      $mediaIntakeTasks.Count -eq 1 -and $mediaIntakeTasks[0].status -eq 'COMPLETED'
    ) -Detail 'intake, execution plan and project no longer remain falsely pending'
    $pendingMediaTasks = Invoke-Json -Method GET -Path '/publish-tasks?scope=pending&page=1&pageSize=100' -Headers $adminHeaders
    Add-Check -Name 'closed media target leaves the pending work queue' -Passed (
      @($pendingMediaTasks.data.items | Where-Object { $_.taskNo -eq $mediaTaskNos[0] }).Count -eq 0
    ) -Detail 'the terminal invitation outcome is retained in history instead of the active queue'
    $activity = Invoke-Json -Method GET -Path "/projects/$rootProjectId" -Headers $customerHeaders
    $activityServices = @($activity.data.activityProjects | ForEach-Object { $_.requestedService })
    $missingActivityServices = @('NEWS_CONFERENCE','ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING') |
      Where-Object { $activityServices -notcontains $_ }
    Add-Check -Name 'four services retain one activity link' -Passed ($missingActivityServices.Count -eq 0) -Detail 'independent projects are grouped without combined pricing'
    $directProject = Invoke-Json -Method GET -Path "/projects/$directProjectId" -Headers $customerHeaders
    $copiedSourceVersion = @($directProject.data.versions | Where-Object { $_.sourceProjectName -and $_.sourceManuscriptTitle } | Select-Object -First 1)
    Add-Check -Name 'direct project copies the approved source manuscript' -Passed ($copiedSourceVersion.Count -eq 1) -Detail 'new project keeps an auditable customer-safe source label'
    $copiedSourceSafe = -not (Find-ExactForbiddenKey $copiedSourceVersion[0] @('sourceProjectId','sourceManuscriptId','sourceVersionId','supplierId','costPrice','upstreamReference'))
    Add-Check -Name 'copied manuscript exposes labels but not source identifiers' -Passed $copiedSourceSafe -Detail 'customer sees source labels without internal provenance identifiers'
    # A customer-price channel can legitimately be awaiting supplier assignment.  The supplier
    # lifecycle below must instead use a local-demo channel with both an active quote and an
    # explicit internal supplier mapping; otherwise the regression would falsely treat an
    # unassigned control record as a submitted supplier order.
    $directExecutionPricingPage = Invoke-Json -Method GET -Path (
      '/admin/pricing?channelStatus=ACTIVE&quoteState=ACTIVE&page=1&pageSize=100'
    ) -Headers $adminHeaders
    $directExecutionChannel = @($directExecutionPricingPage.data.items | Where-Object {
      $_.channelStatus -eq 'ACTIVE' -and $_.quoteState -eq 'ACTIVE' -and
      $null -ne $_.supplierId -and $null -ne $_.customerPrice
    } | Select-Object -First 1)
    Add-Check -Name 'direct publishing has a supplier-mapped local QA channel' -Passed (
      $directExecutionChannel.Count -eq 1
    ) -Detail 'the local control path has an active customer quote and internal supplier mapping; no external fulfilment is asserted'
    $copiedManuscript = @($directProject.data.manuscripts | Where-Object { $_.approvedVersionId } | Select-Object -First 1)
    Add-Check -Name 'direct publishing project exposes its approved copied manuscript' -Passed ($copiedManuscript.Count -eq 1) -Detail 'only the customer-safe approved manuscript reference is selectable'
    if ($directExecutionChannel.Count -eq 1 -and $copiedManuscript.Count -eq 1) {
      $otherDirectProjectId = Submit-Requirement -Headers $customerHeaders -Payload @{
        title = "Local QA second direct publishing-$suffix"; requestedService = 'DIRECT_PUBLISHING'; relatedProjectId = $rootProjectId
        facts = 'Confirmed local QA publishing facts for manuscript ownership regression.'; dueAt = $dueAt
        sourceManuscriptId = [long]$approvedSource[0].manuscriptId
        sourceManuscriptVersionId = [long]$approvedSource[0].versionId
      }
      $wrongManuscriptPlan = Invoke-Http -Method POST -Uri "$ApiBaseUrl/projects/$otherDirectProjectId/publish-plans" -Headers $customerHeaders -Body @{
        manuscriptId = [long]$copiedManuscript[0].id
        manuscriptVersionId = [long]$copiedManuscript[0].approvedVersionId
        planName = "Local QA rejected cross-project manuscript-$suffix"
        selections = @(@{ channelId = [long]$directExecutionChannel[0].id })
      }
      $wrongManuscriptCode = if ([string]::IsNullOrWhiteSpace($wrongManuscriptPlan.Content)) { '' } else { ($wrongManuscriptPlan.Content | ConvertFrom-Json).code }
      Add-Check -Name 'direct plan cannot use an approved manuscript from another project' -Passed (
        $wrongManuscriptPlan.Status -eq 404 -and $wrongManuscriptCode -eq 'NOT_FOUND'
      ) -Detail 'a customer-owned manuscript remains bound to its originating direct-publishing project'
      $wrongDirectPlan = Invoke-Http -Method POST -Uri "$ApiBaseUrl/projects/$rootProjectId/publish-plans" -Headers $customerHeaders -Body @{
        planName = "Local QA rejected direct project-$suffix"
        selections = @(@{ channelId = [long]$directExecutionChannel[0].id })
      }
      $wrongDirectCode = if ([string]::IsNullOrWhiteSpace($wrongDirectPlan.Content)) { '' } else { ($wrongDirectPlan.Content | ConvertFrom-Json).code }
      Add-Check -Name 'direct publishing cannot use a conference project' -Passed (
        $wrongDirectPlan.Status -eq 400 -and $wrongDirectCode -eq 'DIRECT_PROJECT_REQUIRED'
      ) -Detail 'the direct plan must belong to its independently ordered service project'
      $directPlan = Invoke-Json -Method POST -Path "/projects/$directProjectId/publish-plans" -Headers $customerHeaders -Body @{
        manuscriptId = [long]$copiedManuscript[0].id
        manuscriptVersionId = [long]$copiedManuscript[0].approvedVersionId
        planName = "Local QA direct publishing plan-$suffix"
        objective = 'Local-only workflow verification; no external channel submission is made.'
        selections = @(@{
          channelId = [long]$directExecutionChannel[0].id
          note = 'Local-only workflow verification; channel availability remains subject to project review.'
        })
      }
      Add-Check -Name 'direct publishing plan waits for customer confirmation' -Passed (
        $directPlan.data.status -eq 'WAITING_CONFIRMATION' -and [long]$directPlan.data.itemCount -eq 1 -and
        -not [string]::IsNullOrWhiteSpace([string]$directPlan.data.planNo) -and
        -not (Find-ExactForbiddenKey $directPlan.data @('id','planId'))
      ) -Detail 'saving a direct plan does not create an external submission'
      $directPlanNo = [System.Uri]::EscapeDataString([string]$directPlan.data.planNo)
      $confirmedDirectPlan = Invoke-Json -Method POST -Path "/publish-plans/$directPlanNo/confirm" -Headers $customerHeaders
      $directTaskNos = @($confirmedDirectPlan.data.taskNos)
      Add-Check -Name 'confirmed direct plan creates one customer-visible channel task' -Passed (
        $confirmedDirectPlan.data.status -eq 'CONFIRMED' -and $directTaskNos.Count -eq 1 -and
        -not (Find-ExactForbiddenKey $confirmedDirectPlan.data @('id','planId','taskIds'))
      ) -Detail 'customer confirmation creates a project task, not an external supplier submission'
      $directTaskId = Resolve-AdminPublishTaskId -Headers $adminHeaders -TaskNo $directTaskNos[0]
      $directSupplierOrderPage = Invoke-Json -Method GET -Path (
        "/admin/supplier-orders?keyword=$([System.Uri]::EscapeDataString([string]$directTaskNos[0]))&page=1&pageSize=20"
      ) -Headers $adminHeaders
      $directSupplierOrders = @($directSupplierOrderPage.data.items | Where-Object {
        $_.taskNo -eq $directTaskNos[0]
      })
      Add-Check -Name 'direct publishing task creates one supplier-order control record' -Passed (
        $directSupplierOrders.Count -eq 1 -and
        $null -ne $directSupplierOrders[0].supplierId -and
        $directSupplierOrders[0].status -eq 'PENDING_SUBMISSION' -and
        $directSupplierOrders[0].fulfillmentMode -eq 'UNCONFIRMED'
      ) -Detail 'the internal order remains pending and is not treated as a supplier submission'
      $directSupplierChannelId = [long]$directSupplierOrders[0].channelId
      $directSupplierCandidates = Invoke-Json -Method GET -Path (
        "/admin/suppliers/options?channelId=$directSupplierChannelId"
      ) -Headers $adminHeaders
      $directSupplierMappings = Invoke-Json -Method GET -Path (
        "/admin/supplier-channels?channelId=$directSupplierChannelId&page=1&pageSize=100"
      ) -Headers $adminHeaders
      $candidateSupplierIds = @($directSupplierCandidates.data | ForEach-Object { [long]$_.id })
      $eligibleSupplierIds = @($directSupplierMappings.data.items | Where-Object {
        $_.status -eq 'ACTIVE'
      } | ForEach-Object { [long]$_.supplierId } | Select-Object -Unique)
      Add-Check -Name 'supplier assignment candidates are limited to the order channel' -Passed (
        $directSupplierChannelId -gt 0 -and
        $candidateSupplierIds.Count -gt 0 -and
        $candidateSupplierIds -contains [long]$directSupplierOrders[0].supplierId -and
        $candidateSupplierIds.Count -eq $eligibleSupplierIds.Count -and
        @($candidateSupplierIds | Where-Object { $_ -notin $eligibleSupplierIds }).Count -eq 0
      ) -Detail 'the administrative candidate list is derived only from active mappings for the current direct-publishing channel'
      $directSupplierOrderId = [long]$directSupplierOrders[0].id
      $customerSupplierHistory = Invoke-Http -Method GET `
        -Uri "$ApiBaseUrl/admin/supplier-orders/$directSupplierOrderId/history" -Headers $customerHeaders
      Add-Check -Name 'customer cannot access supplier-order fulfillment history' -Passed (
        $customerSupplierHistory.Status -eq 403
      ) -Detail "HTTP $($customerSupplierHistory.Status)"
      $blockedSupplierResult = Invoke-Http -Method POST `
        -Uri "$ApiBaseUrl/operator/publish-tasks/$directTaskId/results" -Headers $adminHeaders -Body @{
          title = "Local QA rejected unfulfilled supplier result-$suffix"
          url = "https://example.com/unfulfilled-supplier-$suffix"
          note = 'Local-only negative-path check. No external supplier submission is asserted.'
        }
      $blockedSupplierResultCode = if ([string]::IsNullOrWhiteSpace($blockedSupplierResult.Content)) {
        ''
      } else {
        ($blockedSupplierResult.Content | ConvertFrom-Json).code
      }
      Add-Check -Name 'supplier-bound direct task cannot submit a customer result before fulfillment' -Passed (
        $blockedSupplierResult.Status -eq 409 -and
        $blockedSupplierResultCode -eq 'SUPPLIER_FULFILLMENT_REQUIRED'
      ) -Detail 'a result link cannot substitute for a supplier handoff or receipt'
      $supplierEvidenceRef = "qa/local-supplier-handoff-$suffix"
      $supplierOrderSubmitted = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'SUBMITTED'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only manual-handoff regression; not an external supplier submission.'
        }
      $supplierOrderAccepted = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'ACCEPTED'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only supplier-acceptance state regression.'
        }
      $supplierOrderExecuting = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'IN_PROGRESS'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only supplier execution-state regression.'
        }
      $supplierOrderException = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'EXCEPTION'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          exceptionReason = 'Local-only retry-path regression.'
          note = 'Local-only supplier exception-state regression.'
        }
      $invalidSupplierReset = Invoke-Http -Method PATCH `
        -Uri "$ApiBaseUrl/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'PENDING_SUBMISSION'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'This retry must be rejected until current fulfillment fields are cleared.'
        }
      $invalidSupplierResetCode = if ([string]::IsNullOrWhiteSpace($invalidSupplierReset.Content)) {
        ''
      } else {
        ($invalidSupplierReset.Content | ConvertFrom-Json).code
      }
      Add-Check -Name 'supplier retry rejects a stale fulfillment context' -Passed (
        $invalidSupplierReset.Status -eq 400 -and
        $invalidSupplierResetCode -eq 'SUPPLIER_ORDER_PENDING_CONTEXT_INVALID'
      ) -Detail 'a new supplier attempt cannot inherit the prior submission evidence or upstream context'
      $supplierOrderReset = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'PENDING_SUBMISSION'
          fulfillmentMode = 'UNCONFIRMED'
          note = 'Local-only supplier retry reset.'
        }
      $supplierOrderAfterResetPage = Invoke-Json -Method GET -Path (
        "/admin/supplier-orders?keyword=$([System.Uri]::EscapeDataString([string]$directTaskNos[0]))&page=1&pageSize=20"
      ) -Headers $adminHeaders
      $supplierOrderAfterReset = @($supplierOrderAfterResetPage.data.items | Where-Object {
        [long]$_.id -eq $directSupplierOrderId
      } | Select-Object -First 1)
      Add-Check -Name 'supplier retry clears current fulfillment fields before resubmission' -Passed (
        $supplierOrderReset.data.status -eq 'PENDING_SUBMISSION' -and
        $supplierOrderAfterReset.Count -eq 1 -and
        $supplierOrderAfterReset[0].fulfillmentMode -eq 'UNCONFIRMED' -and
        [string]::IsNullOrWhiteSpace([string]$supplierOrderAfterReset[0].externalOrderNo) -and
        [string]::IsNullOrWhiteSpace([string]$supplierOrderAfterReset[0].submissionEvidenceReference)
      ) -Detail 'the current order is clean for a new attempt while the prior trace is retained separately'
      $supplierOrderResubmitted = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'SUBMITTED'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only supplier retry submission.'
        }
      $supplierOrderReaccepted = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'ACCEPTED'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only supplier retry acceptance.'
        }
      $supplierOrderReexecuting = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'IN_PROGRESS'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only supplier retry execution.'
        }
      $supplierOrderCompleted = Invoke-Json -Method PATCH `
        -Path "/admin/supplier-orders/$directSupplierOrderId" -Headers $adminHeaders -Body @{
          supplierId = [long]$directSupplierOrders[0].supplierId
          status = 'COMPLETED'
          fulfillmentMode = 'MANUAL'
          submissionEvidenceReference = $supplierEvidenceRef
          note = 'Local-only supplier completion-state regression.'
        }
      Add-Check -Name 'supplier fulfillment reaches completed only through evidence-backed states' -Passed (
        $supplierOrderSubmitted.data.status -eq 'SUBMITTED' -and
        $supplierOrderAccepted.data.status -eq 'ACCEPTED' -and
        $supplierOrderExecuting.data.status -eq 'IN_PROGRESS' -and
        $supplierOrderException.data.status -eq 'EXCEPTION' -and
        $supplierOrderResubmitted.data.status -eq 'SUBMITTED' -and
        $supplierOrderReaccepted.data.status -eq 'ACCEPTED' -and
        $supplierOrderReexecuting.data.status -eq 'IN_PROGRESS' -and
        $supplierOrderCompleted.data.status -eq 'COMPLETED'
      ) -Detail 'manual evidence is recorded for a local QA workflow without claiming external fulfillment'
      $supplierOrderHistory = Invoke-Json -Method GET `
        -Path "/admin/supplier-orders/$directSupplierOrderId/history" -Headers $adminHeaders
      $supplierHistoryStates = @($supplierOrderHistory.data | ForEach-Object { [string]$_.currentStatus })
      Add-Check -Name 'supplier order history records the evidence-backed state trail' -Passed (
        $supplierHistoryStates -contains 'PENDING_SUBMISSION' -and
        $supplierHistoryStates -contains 'SUBMITTED' -and
        $supplierHistoryStates -contains 'ACCEPTED' -and
        $supplierHistoryStates -contains 'IN_PROGRESS' -and
        $supplierHistoryStates -contains 'EXCEPTION' -and
        $supplierHistoryStates -contains 'COMPLETED' -and
        (($supplierOrderHistory.data | ForEach-Object { [string]$_.note }) -join "`n") -match [regex]::Escape($supplierEvidenceRef) -and
        -not (Find-ExactForbiddenKey $supplierOrderHistory.data @('supplierId','costPrice','credentialValue','apiToken','secret'))
      ) -Detail 'only internal platform administrators can read the trace, and it contains no credentials or pricing fields'
      $directTaskInProgress = Invoke-Json -Method PATCH -Path "/operator/publish-tasks/$directTaskId" -Headers $adminHeaders -Body @{
        status = 'IN_PROGRESS'
        executionNote = 'Local-only execution-state regression; no external supplier submission is asserted.'
      }
      Add-Check -Name 'authorised operator starts the direct publishing task' -Passed (
        $directTaskInProgress.data.status -eq 'IN_PROGRESS'
      ) -Detail 'the task has an explicit accountable execution state before evidence is submitted'
      $resultUrl = "https://example.com/winpress-local-qa-$suffix"
      $submittedResult = Invoke-Json -Method POST -Path "/operator/publish-tasks/$directTaskId/results" -Headers $adminHeaders -Body @{
        title = "Local QA verified publishing result-$suffix"
        url = $resultUrl
        note = 'Local-only verifiable-link regression record.'
      }
      Add-Check -Name 'verified result completes the direct publishing task' -Passed (
        $submittedResult.data.status -eq 'COMPLETED'
      ) -Detail 'completion is produced by an evidence record rather than an arbitrary status edit'
      $acceptedResult = Invoke-Json -Method POST -Path "/publish-tasks/$([System.Uri]::EscapeDataString([string]$directTaskNos[0]))/accept" -Headers $customerHeaders
      Add-Check -Name 'customer accepts the verified publishing result' -Passed (
        $acceptedResult.data.status -eq 'CLIENT_ACCEPTED'
      ) -Detail 'customer acceptance is recorded only after a verified result exists'
      $repeatedAcceptance = Invoke-Json -Method POST -Path "/publish-tasks/$([System.Uri]::EscapeDataString([string]$directTaskNos[0]))/accept" -Headers $customerHeaders
      Add-Check -Name 'repeated customer acceptance is idempotent' -Passed (
        $repeatedAcceptance.data.status -eq 'CLIENT_ACCEPTED'
      ) -Detail 'a retry does not create a second acceptance event or change the final state'
      $reopenAcceptedTask = Invoke-Http -Method PATCH -Uri "$ApiBaseUrl/operator/publish-tasks/$directTaskId" -Headers $adminHeaders -Body @{
        status = 'IN_PROGRESS'
        executionNote = 'This accepted task must remain immutable.'
      }
      $reopenAcceptedCode = if ([string]::IsNullOrWhiteSpace($reopenAcceptedTask.Content)) { '' } else { ($reopenAcceptedTask.Content | ConvertFrom-Json).code }
      Add-Check -Name 'accepted task cannot return to execution' -Passed (
        $reopenAcceptedTask.Status -eq 409 -and $reopenAcceptedCode -eq 'TASK_FINALIZED'
      ) -Detail 'an operator cannot overwrite the customer acceptance fact'
      $duplicateAcceptedResult = Invoke-Http -Method POST -Uri "$ApiBaseUrl/operator/publish-tasks/$directTaskId/results" -Headers $adminHeaders -Body @{
        title = "Rejected duplicate result-$suffix"
        url = "$resultUrl-duplicate"
        note = 'This result must be rejected after customer acceptance.'
      }
      $duplicateAcceptedCode = if ([string]::IsNullOrWhiteSpace($duplicateAcceptedResult.Content)) { '' } else { ($duplicateAcceptedResult.Content | ConvertFrom-Json).code }
      Add-Check -Name 'accepted task cannot receive another result' -Passed (
        $duplicateAcceptedResult.Status -eq 409 -and $duplicateAcceptedCode -eq 'TASK_ALREADY_ACCEPTED'
      ) -Detail 'new evidence cannot reopen or replace a customer-accepted result'
      $customerAcceptedTask = Invoke-Json -Method GET -Path "/customer/publish-tasks/$([System.Uri]::EscapeDataString([string]$directTaskNos[0]))" -Headers $customerHeaders
      Add-Check -Name 'customer task remains accepted after rejected operator writes' -Passed (
        $customerAcceptedTask.data.status -eq 'CLIENT_ACCEPTED' -and
        -not (Find-ForbiddenKey $customerAcceptedTask.data)
      ) -Detail 'the final customer state remains stable and exposes only customer-safe fields'
      $directProjectAfterPlan = Invoke-Json -Method GET -Path "/projects/$directProjectId" -Headers $customerHeaders
      $directPlansAfterAcceptance = Invoke-Json -Method GET -Path "/projects/$directProjectId/publish-plans" -Headers $customerHeaders
      $closedDirectPlan = @($directPlansAfterAcceptance.data | Where-Object { $_.planNo -eq $directPlan.data.planNo } | Select-Object -First 1)
      $directIntakeTasks = @($directProjectAfterPlan.data.serviceIntakeTasks | Where-Object { $_.serviceType -eq 'DIRECT_PUBLISHING' })
      Add-Check -Name 'direct task acceptance closes its plan, project and intake' -Passed (
        $directProjectAfterPlan.data.project.status -eq 'COMPLETED' -and
        $closedDirectPlan.Count -eq 1 -and $closedDirectPlan[0].status -eq 'COMPLETED' -and
        $directIntakeTasks.Count -eq 1 -and $directIntakeTasks[0].status -eq 'COMPLETED'
      ) -Detail 'verified evidence and customer acceptance produce one consistent final workflow state'
      $matchingResults = @($directProjectAfterPlan.data.results | Where-Object { $_.url -eq $resultUrl })
      Add-Check -Name 'accepted task retains one verified result record' -Passed (
        $matchingResults.Count -eq 1 -and $matchingResults[0].status -eq 'VERIFIED'
      ) -Detail 'retry and rejected writes do not duplicate the evidence visible to the customer'
    }
    $records = Invoke-Json -Method GET -Path '/task-records?page=1&pageSize=100' -Headers $customerHeaders
    $recordProjectIds = @($records.data.items | ForEach-Object { [long]$_.projectId })
    $missingTaskProjects = @($rootProjectId, $writingProjectId, $mediaProjectId, $directProjectId) |
      Where-Object { $recordProjectIds -notcontains $_ }
    Add-Check -Name 'four services create task records' -Passed ($missingTaskProjects.Count -eq 0) -Detail 'conference, writing and two intake tasks are traceable'
    $expectedTaskMappings = @(
      [pscustomobject]@{ ProjectId = $rootProjectId; ItemLabel = '新闻发布会' },
      [pscustomobject]@{ ProjectId = $writingProjectId; ItemLabel = '云采写' },
      [pscustomobject]@{ ProjectId = $mediaProjectId; ItemLabel = '媒体邀请' },
      [pscustomobject]@{ ProjectId = $directProjectId; ItemLabel = '直编发稿' }
    )
    $missingTaskMappings = @(
      foreach ($expected in $expectedTaskMappings) {
        $matches = @($records.data.items | Where-Object {
          [long]$_.projectId -eq [long]$expected.ProjectId -and $_.itemLabel -eq $expected.ItemLabel
        })
        if ($matches.Count -eq 0) {
          $expected
        }
      }
    )
    Add-Check -Name 'four services retain separately labelled task records' -Passed ($missingTaskMappings.Count -eq 0) -Detail 'each independently ordered project keeps its own customer-visible task category'
    $orders = Invoke-Json -Method GET -Path '/order-records?page=1&pageSize=100' -Headers $customerHeaders
    $orderServices = @($orders.data.items | ForEach-Object { $_.serviceType })
    $missingOrderServices = @('NEWS_CONFERENCE','ONSITE_WRITING','MEDIA_PR','DIRECT_PUBLISHING') |
      Where-Object { $orderServices -notcontains $_ }
    Add-Check -Name 'four services create order records' -Passed ($missingOrderServices.Count -eq 0) -Detail 'customer order ledger contains all four services'
    $closureOrderRecords = @($orders.data.items | Where-Object {
      @($rootProjectId, $writingProjectId, $mediaProjectId, $directProjectId) -contains [long]$_.projectId
    })
    $expectedOrderMappings = @(
      [pscustomobject]@{ ProjectId = $rootProjectId; ServiceType = 'NEWS_CONFERENCE' },
      [pscustomobject]@{ ProjectId = $writingProjectId; ServiceType = 'ONSITE_WRITING' },
      [pscustomobject]@{ ProjectId = $mediaProjectId; ServiceType = 'MEDIA_PR' },
      [pscustomobject]@{ ProjectId = $directProjectId; ServiceType = 'DIRECT_PUBLISHING' }
    )
    $invalidOrderMappings = @(
      foreach ($expected in $expectedOrderMappings) {
        $matches = @($closureOrderRecords | Where-Object {
          [long]$_.projectId -eq [long]$expected.ProjectId -and $_.serviceType -eq $expected.ServiceType
        })
        if ($matches.Count -ne 1) {
          $expected
        }
      }
    )
    Add-Check -Name 'four services retain separate project order records' -Passed ($invalidOrderMappings.Count -eq 0) -Detail 'each independently ordered project has one correctly typed customer order record'
    $unstableOrderIdentifiers = @(
      foreach ($expected in $expectedOrderMappings) {
        $projectForOrder = Invoke-Json -Method GET -Path "/projects/$($expected.ProjectId)" -Headers $customerHeaders
        $matchingOrders = @($closureOrderRecords | Where-Object {
          [long]$_.projectId -eq [long]$expected.ProjectId -and $_.serviceType -eq $expected.ServiceType
        })
        if (
          $matchingOrders.Count -ne 1 -or
          $matchingOrders[0].recordNo -ne $projectForOrder.data.project.requirementNo
        ) {
          $expected
        }
      }
    )
    Add-Check -Name 'service order identifiers remain tied to the original submission' -Passed (
      $unstableOrderIdentifiers.Count -eq 0
    ) -Detail 'task creation and retry do not replace the public customer order number'
    Add-Check -Name 'closure order records are not combined' -Passed (
      @($closureOrderRecords | Select-Object -ExpandProperty recordNo -Unique).Count -eq 4
    ) -Detail 'the activity link does not create a combined or duplicate customer order'
    $writingOrder = @($closureOrderRecords | Where-Object {
      [long]$_.projectId -eq $writingProjectId -and $_.serviceType -eq 'ONSITE_WRITING'
    } | Select-Object -First 1)
    Add-Check -Name 'onsite writing order keeps the confirmed daily rate' -Passed (
      $writingOrder.Count -eq 1 -and [decimal]$writingOrder[0].amount -eq [decimal]980
    ) -Detail 'one writer for one day is recorded as CNY 980 in the customer ledger'
    $directOrder = @($closureOrderRecords | Where-Object {
      [long]$_.projectId -eq $directProjectId -and $_.serviceType -eq 'DIRECT_PUBLISHING'
    } | Select-Object -First 1)
    Add-Check -Name 'confirmed direct plan records the selected customer service price' -Passed (
      $directOrder.Count -eq 1 -and
      [decimal]$directOrder[0].amount -eq [decimal]$directExecutionChannel[0].customerPrice -and
      $directOrder[0].status -eq 'CLIENT_ACCEPTED'
    ) -Detail 'the local price snapshot and accepted final state remain visible without exposing supplier cost'
    $manualMediaOrder = @($closureOrderRecords | Where-Object {
      [long]$_.projectId -eq $mediaProjectId -and $_.serviceType -eq 'MEDIA_PR'
    } | Select-Object -First 1)
    Add-Check -Name 'manual invitation remains an unquoted customer order record' -Passed (
      $manualMediaOrder.Count -eq 1 -and $null -eq $manualMediaOrder[0].amount -and
      $manualMediaOrder[0].itemDetail -eq $manualMediaName -and
      $manualMediaOrder[0].status -eq 'NOT_PROCEEDING'
    ) -Detail 'the customer can trace the closed target without a fabricated price, supplier or publication claim'
    $unquotedOrders = @($closureOrderRecords | Where-Object {
      $_.serviceType -in @('NEWS_CONFERENCE','MEDIA_PR')
    })
    Add-Check -Name 'manual-quote services do not manufacture a price' -Passed (
      $unquotedOrders.Count -eq 2 -and @($unquotedOrders | Where-Object { $null -ne $_.amount }).Count -eq 0
    ) -Detail 'conference and invitation orders remain pending project confirmation'
  }
} finally {
  foreach ($token in @($sessions.Values) + @($supplementalTokens)) {
    try { Invoke-Http -Method POST -Uri "$ApiBaseUrl/auth/logout" -Headers @{ Authorization = "Bearer $token" } | Out-Null } catch {}
  }
  if ($checks.Count -gt 0) { $checks | Format-Table -AutoSize }
}
