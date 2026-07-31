function Resolve-WinPressMediaSeedInputs {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot,
    [string]$MediaChannelsCsv,
    [string]$MediaQuotesCsv
  )

  function Resolve-WinPressMediaSeedPath {
    param(
      [string[]]$Candidates,
      [string]$ExpectedHeader,
      [string]$Label
    )

    foreach ($candidate in $Candidates) {
      if ([string]::IsNullOrWhiteSpace($candidate)) {
        continue
      }
      $resolvedCandidate = $candidate
      if (-not [System.IO.Path]::IsPathRooted($resolvedCandidate)) {
        $resolvedCandidate = Join-Path $ProjectRoot $resolvedCandidate
      }
      if (-not (Test-Path -LiteralPath $resolvedCandidate -PathType Leaf)) {
        continue
      }
      $header = @(Get-Content -LiteralPath $resolvedCandidate -Encoding UTF8 -TotalCount 1)[0]
      if ([string]::IsNullOrWhiteSpace($header) -or @($header.Split(',')) -notcontains $ExpectedHeader) {
        throw "$Label input is missing the required $ExpectedHeader header."
      }
      return (Resolve-Path -LiteralPath $resolvedCandidate).Path
    }
    throw "$Label input was not found. Provide an approved local input through the corresponding parameter or environment variable."
  }

  $databaseRoot = Join-Path $ProjectRoot 'database'
  $channelsPath = Resolve-WinPressMediaSeedPath -Candidates @(
    $MediaChannelsCsv,
    $env:WINPRESS_MEDIA_CHANNELS_CSV,
    (Join-Path $databaseRoot 'media_channels.csv'),
    (Join-Path $databaseRoot 'media_channels.example.csv')
  ) -ExpectedHeader 'channel_no' -Label 'Media-channel CSV'
  $quotesPath = Resolve-WinPressMediaSeedPath -Candidates @(
    $MediaQuotesCsv,
    $env:WINPRESS_MEDIA_QUOTES_CSV,
    (Join-Path $databaseRoot 'media_quotes.csv'),
    (Join-Path $databaseRoot 'media_quotes.example.csv')
  ) -ExpectedHeader 'quote_no' -Label 'Media-quote CSV'

  $channelCount = @(
    Import-Csv -LiteralPath $channelsPath -Encoding UTF8 |
      ForEach-Object { $_.channel_no } |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
      Sort-Object -Unique
  ).Count
  $quoteCount = @(
    Import-Csv -LiteralPath $quotesPath -Encoding UTF8 |
      ForEach-Object { $_.quote_no } |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
      Sort-Object -Unique
  ).Count

  if (($channelCount -eq 0) -xor ($quoteCount -eq 0)) {
    throw 'Media channel and quote inputs must either both contain records or both be headers-only samples.'
  }

  [PSCustomObject]@{
    ChannelsPath = $channelsPath
    QuotesPath = $quotesPath
    ChannelCount = $channelCount
    QuoteCount = $quoteCount
    Mode = if ($channelCount -eq 0) { 'PUBLIC_HEADERS_ONLY' } else { 'LOCAL_MEDIA_INPUT' }
  }
}
