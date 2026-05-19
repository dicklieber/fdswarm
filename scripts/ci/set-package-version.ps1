$ErrorActionPreference = 'Stop'

$version = ''
if ($env:GITHUB_REF_TYPE -eq 'tag') {
  $version = $env:GITHUB_REF_NAME -replace '^v',''
} elseif ($env:DISPATCH_VERSION) {
  $version = $env:DISPATCH_VERSION -replace '^v',''
}

if (-not $version) {
  Write-Host 'No release version requested.'
  exit 0
}

if ($version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
  Write-Error "Release version must be numeric major.minor.patch, got: $version"
  exit 1
}

Write-Host "FDSWARM_VERSION=$version"
if ($env:GITHUB_ENV) {
  "FDSWARM_VERSION=$version" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
}
