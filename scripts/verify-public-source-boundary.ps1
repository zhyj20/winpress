[CmdletBinding()]
param(
  [string]$ProjectRoot = '',
  [switch]$RequireApprovedCaseAssets,
  [switch]$ExcludeCaseAssets,
  [string]$CaseAssetApprovalRegistryPath = ''
)

$ErrorActionPreference = 'Stop'

if ($RequireApprovedCaseAssets -and $ExcludeCaseAssets) {
  throw '不能同时要求案例素材授权并将案例素材排除出候选清单。'
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = Join-Path $PSScriptRoot '..'
}
$resolvedProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$gitRoot = (& git -C $resolvedProjectRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitRoot)) {
  throw '当前目录不是可核验的 Git 工作树，无法建立公开源码候选清单。'
}
$gitRoot = $gitRoot.Trim()

function Normalize-RelativePath([string]$Path) {
  return ($Path -replace [regex]::Escape([string][System.IO.Path]::DirectorySeparatorChar), '/')
}

function Is-TextCandidate([string]$RelativePath) {
  $fileName = [System.IO.Path]::GetFileName($RelativePath)
  switch ([System.IO.Path]::GetExtension($RelativePath).ToLowerInvariant()) {
    '.cs' { return $true }
    '.css' { return $true }
    '.csv' { return $true }
    '.env' { return $true }
    '.html' { return $true }
    '.java' { return $true }
    '.js' { return $true }
    '.json' { return $true }
    '.md' { return $true }
    '.mjs' { return $true }
    '.properties' { return $true }
    '.ps1' { return $true }
    '.sql' { return $true }
    '.toml' { return $true }
    '.ts' { return $true }
    '.tsx' { return $true }
    '.txt' { return $true }
    '.vue' { return $true }
    '.xml' { return $true }
    '.yaml' { return $true }
    '.yml' { return $true }
  }
  return $fileName -eq '.gitignore' -or $fileName -eq '.gitattributes' -or $fileName -eq 'Dockerfile' -or $fileName -eq 'Dockerfile.local'
}

function Add-Finding([System.Collections.Generic.List[object]]$Findings, [string]$Rule, [string]$RelativePath) {
  $Findings.Add([pscustomobject]@{ Rule = $Rule; Path = $RelativePath })
}

function Normalize-ApprovalPath([string]$Path) {
  if ([string]::IsNullOrWhiteSpace($Path)) {
    return ''
  }
  return $Path.Trim().Replace('\\', '/').TrimStart('/')
}

function Load-CaseAssetApprovalIndex([string]$RegistryPath) {
  if (-not (Test-Path -LiteralPath $RegistryPath -PathType Leaf)) {
    throw "缺少案例素材授权台账：$RegistryPath"
  }
  try {
    $registry = Get-Content -LiteralPath $RegistryPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
  } catch {
    throw "案例素材授权台账不是有效 JSON：$RegistryPath"
  }
  if ($registry.format -ne 'winpress.case-asset-approval/v1') {
    throw '案例素材授权台账格式必须为 winpress.case-asset-approval/v1。'
  }
  if ($null -eq $registry.assets) {
    throw '案例素材授权台账必须提供 assets 数组。'
  }

  $index = @{}
  foreach ($asset in @($registry.assets)) {
    $path = Normalize-ApprovalPath ([string]$asset.path)
    if ([string]::IsNullOrWhiteSpace($path) -or -not $path.StartsWith('frontend/public/case-media/')) {
      throw '案例素材授权台账中的 path 必须位于 frontend/public/case-media/。'
    }
    if ($index.ContainsKey($path)) {
      throw "案例素材授权台账存在重复路径：$path"
    }
    if ($asset.publicReleaseApproved -ne $true) {
      throw "案例素材授权台账中的公开授权状态不是 true：$path"
    }
    if ([string]::IsNullOrWhiteSpace([string]$asset.approvalReference)) {
      throw "案例素材授权台账缺少 approvalReference：$path"
    }
    $approvedAt = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse([string]$asset.approvedAt, [ref]$approvedAt)) {
      throw "案例素材授权台账缺少有效 approvedAt：$path"
    }
    if (-not [string]::IsNullOrWhiteSpace([string]$asset.expiresAt)) {
      $expiresAt = [DateTimeOffset]::MinValue
      if (-not [DateTimeOffset]::TryParse([string]$asset.expiresAt, [ref]$expiresAt)) {
        throw "案例素材授权台账中的 expiresAt 无效：$path"
      }
      if ($expiresAt -lt [DateTimeOffset]::Now) {
        throw "案例素材授权台账中的授权已过期：$path"
      }
    }
    $index[$path] = $true
  }
  return $index
}

$candidatePaths = @(
  & git -C $gitRoot ls-files --cached --others --exclude-standard |
    ForEach-Object { Normalize-RelativePath $_ } |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Sort-Object -Unique
)
if ($LASTEXITCODE -ne 0) {
  throw '无法读取 Git 公开候选清单。'
}

$forbiddenPathFindings = [System.Collections.Generic.List[object]]::new()
$secretFindings = [System.Collections.Generic.List[object]]::new()
$caseAssetFindings = [System.Collections.Generic.List[object]]::new()

$forbiddenExactPaths = @(
  'docs/TEST-ACCOUNTS.md',
  'database/winpress_full.sql'
)
$forbiddenSegments = @(
  '.git', '.qa', 'artifacts', 'backups', 'coverage', 'dist', 'logs', 'node_modules', 'playwright-report',
  'private-data', 'secrets', 'certs', 'certificates', 'release', 'storage', 'target', 'target-local',
  'test-results', 'tmp', 'tmpWeb'
)
$forbiddenFilePatterns = @(
  '^\.env(?!\.example$)',
  '(^|/)media_channels\.csv$',
  '(^|/)media_quotes\.csv$',
  '(^|/)media-seed-manifest\.json$',
  '\.(dump|err|hprof|log|out|pid|sqlite|zip|7z)$'
)

$privateKeyPattern = '-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----'
$tokenPattern = '(?i)(?:github_pat_[a-z0-9_]{20,}|gh[pousr]_[a-z0-9]{20,}|xox[baprs]-[a-z0-9-]{20,}|akia[0-9a-z]{16})'
$credentialAssignmentPattern = '(?im)^\s*(?:POSTGRES_PASSWORD|REDIS_PASSWORD|WINPRESS_NIUMEDIA_TOKEN|WINPRESS_FEDERATION_SHARED_SECRET|JWT_SECRET|API_KEY)\s*=\s*(?!\$\{|<|CHANGEME|REPLACE|YOUR_|EXAMPLE)[^\s#]{12,}'

$caseAssetApprovalIndex = @{}
$caseAssetCandidates = @($candidatePaths | Where-Object { $_ -like 'frontend/public/case-media/*' })
if ($RequireApprovedCaseAssets -and $caseAssetCandidates.Count -gt 0) {
  if ([string]::IsNullOrWhiteSpace($CaseAssetApprovalRegistryPath)) {
    $CaseAssetApprovalRegistryPath = Join-Path $gitRoot 'docs\CASE-ASSET-APPROVAL-REGISTRY.json'
  }
  $caseAssetApprovalIndex = Load-CaseAssetApprovalIndex $CaseAssetApprovalRegistryPath
}

foreach ($relativePath in $candidatePaths) {
  if ($ExcludeCaseAssets -and $relativePath -like 'frontend/public/case-media/*') {
    # Local case media does not become public-release material merely because it
    # exists in this worktree.  The default source package excludes it until an
    # asset-by-asset authorization workflow is available.
    continue
  }
  $segments = $relativePath -split '/'
  $fileName = [System.IO.Path]::GetFileName($relativePath)
  $fullPath = Join-Path $gitRoot ($relativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))

  if ($forbiddenExactPaths -contains $relativePath) {
    Add-Finding $forbiddenPathFindings 'FORBIDDEN_EXACT_PATH' $relativePath
  }
  if (@($segments | Where-Object { $forbiddenSegments -contains $_ }).Count -gt 0) {
    Add-Finding $forbiddenPathFindings 'FORBIDDEN_DIRECTORY' $relativePath
  }
  if (@($forbiddenFilePatterns | Where-Object { $relativePath -match $_ -or $fileName -match $_ }).Count -gt 0) {
    Add-Finding $forbiddenPathFindings 'FORBIDDEN_FILE_PATTERN' $relativePath
  }

  if ($RequireApprovedCaseAssets -and $relativePath -like 'frontend/public/case-media/*') {
    # Local presence, screenshots and historic URLs are not authorization. The
    # private registry must record a current, per-asset public-release approval.
    if (-not $caseAssetApprovalIndex.ContainsKey($relativePath)) {
      Add-Finding $caseAssetFindings 'CASE_ASSET_APPROVAL_REQUIRED' $relativePath
    }
  }

  if ((Test-Path -LiteralPath $fullPath -PathType Leaf) -and (Is-TextCandidate $relativePath)) {
    $privateKeyFound = $false
    $tokenFound = $false
    $credentialFound = $false
    foreach ($line in (Get-Content -LiteralPath $fullPath -Encoding UTF8)) {
      if (-not $privateKeyFound -and $line -match $privateKeyPattern) {
        Add-Finding $secretFindings 'PRIVATE_KEY_PATTERN' $relativePath
        $privateKeyFound = $true
      }
      if (-not $tokenFound -and $line -match $tokenPattern) {
        # npm lockfile SRI hashes may coincidentally contain a token-shaped substring.
        # They are content-address integrity data, not credentials, and remain covered by npm's own hash validation.
        $isLockfileIntegrity = $relativePath -like '*package-lock.json' -and $line -match '^\s*"integrity"\s*:\s*"sha(?:256|512)-'
        if (-not $isLockfileIntegrity) {
          Add-Finding $secretFindings 'HIGH_CONFIDENCE_TOKEN_PATTERN' $relativePath
          $tokenFound = $true
        }
      }
      if (-not $credentialFound -and $line -match $credentialAssignmentPattern) {
        Add-Finding $secretFindings 'CREDENTIAL_ASSIGNMENT_VALUE' $relativePath
        $credentialFound = $true
      }
    }
  }
}

$allFindings = @($forbiddenPathFindings) + @($secretFindings) + @($caseAssetFindings)
$status = if ($allFindings.Count -eq 0) { 'PASS' } else { 'BLOCKED' }

[pscustomobject]@{
  Status = $status
  CandidateFiles = $candidatePaths.Count
  ForbiddenPathFindings = $forbiddenPathFindings.Count
  SecretFindings = $secretFindings.Count
  CaseAssetApprovalFindings = $caseAssetFindings.Count
  RequiresApprovedCaseAssets = [bool]$RequireApprovedCaseAssets
  ExcludesCaseAssets = [bool]$ExcludeCaseAssets
  ApprovedCaseAssetCandidates = if ($RequireApprovedCaseAssets) {
    $caseAssetCandidates.Count - $caseAssetFindings.Count
  } else {
    0
  }
} | Format-List

if ($allFindings.Count -gt 0) {
  $allFindings | Sort-Object Rule, Path | Format-Table -AutoSize
  exit 1
}



