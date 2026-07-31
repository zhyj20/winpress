function Get-WinPressLocalDemoCredentials {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [string]$AccountDocument
  )

  if (-not (Test-Path -LiteralPath $AccountDocument -PathType Leaf)) {
    throw 'Local test-account document is missing.'
  }

  $accounts = New-Object System.Collections.Generic.List[object]
  $seenUsernames = @{}
  $casePassword = $null
  $quickCount = 0
  $caseCount = 0

  foreach ($line in Get-Content -LiteralPath $AccountDocument -Encoding UTF8) {
    $trimmed = $line.Trim()
    if (-not $trimmed.StartsWith('|')) {
      foreach ($match in [regex]::Matches($line, '\x60([A-Za-z0-9@!#$%^&*._-]{8,64})\x60')) {
        $candidate = $match.Groups[1].Value
        if ([string]::IsNullOrWhiteSpace($casePassword) -and $candidate.Contains('@') -and -not $candidate.StartsWith('http', [System.StringComparison]::OrdinalIgnoreCase)) {
          $casePassword = $candidate
        }
      }
      continue
    }

    $fields = @($trimmed.Trim('|').Split('|') | ForEach-Object { $_.Trim().Trim([char]96) })
    if ($fields.Count -lt 3 -or @($fields | Where-Object { $_ -match '^-+$' }).Count -gt 0) { continue }
    $emailIndex = -1
    for ($index = 0; $index -lt $fields.Count; $index++) {
      if ($fields[$index] -match '^[^@\s|]+@[^@\s|]+$') {
        $emailIndex = $index
        break
      }
    }
    if ($emailIndex -lt 0) { continue }

    $group = $null
    $password = $null
    if ($fields.Count -eq 4 -and $emailIndex + 1 -lt $fields.Count) {
      $group = 'QUICK_LOGIN'
      $password = $fields[$emailIndex + 1]
    } elseif ($fields.Count -eq 3 -and $emailIndex -eq 1) {
      $group = 'CASE_DEMO'
      $password = $casePassword
    }
    $username = $fields[$emailIndex]
    if ([string]::IsNullOrWhiteSpace($group) -or [string]::IsNullOrWhiteSpace($password) -or $password.Length -lt 8 -or $seenUsernames.ContainsKey($username)) { continue }

    $seenUsernames[$username] = $true
    $accounts.Add([PSCustomObject]@{ Username = $username; Password = $password; Group = $group })
    if ($group -eq 'QUICK_LOGIN') { $quickCount++ }
    if ($group -eq 'CASE_DEMO') { $caseCount++ }
  }

  if ($quickCount -lt 3) {
    throw 'Local test-account document does not provide the three reusable quick-login credentials.'
  }
  if ([string]::IsNullOrWhiteSpace($casePassword) -or $caseCount -lt 1) {
    throw 'Local test-account document does not provide reusable brand-case credentials.'
  }
  return $accounts
}
