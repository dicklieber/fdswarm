param(
  [string]$Tag = '',
  [string]$RuntimesDir = '',
  [string]$WorkDir = '',
  [switch]$KeepWork,
  [switch]$SkipVerifyDocs,
  [switch]$SkipSign,
  [string]$CertificateThumbprint = $env:WINDOWS_CODESIGN_CERT_THUMBPRINT,
  [string]$TimestampServer = 'http://timestamp.digicert.com'
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoDir = (Resolve-Path (Join-Path $ScriptDir '..')).Path

$AppName = 'FdSwarm'
$Vendor = 'FdSwarm'
$MainClass = 'fdswarm.FdSwarm'
$MainJarName = 'fdswarm.jar'
$WinUpgradeUuid = '8F095DE2-D316-43A7-94C3-7702217CAE1D'

if (-not $RuntimesDir) {
  $RuntimesDir = Join-Path $RepoDir 'fdswarm-runtimes'
}
if (-not $WorkDir) {
  $WorkDir = Join-Path $RepoDir 'release/msi-installers'
}

function Fail {
  param([string]$Message)
  throw "publish-msi-installers: $Message"
}

function Require-Command {
  param([string]$CommandName)
  if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
    Fail "required command not found: $CommandName"
  }
}

function Add-WixToPath {
  if ((Get-Command 'candle.exe' -ErrorAction SilentlyContinue) -and
      (Get-Command 'light.exe' -ErrorAction SilentlyContinue)) {
    return
  }

  $CandidateWixBins = @(
    (Join-Path $env:LOCALAPPDATA 'Programs/WiX Toolset v3.14/bin'),
    (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.14/bin'),
    (Join-Path ${env:ProgramFiles(x86)} 'WiX Toolset v3.11/bin'),
    (Join-Path $env:ProgramFiles 'WiX Toolset v3.14/bin'),
    (Join-Path $env:ProgramFiles 'WiX Toolset v3.11/bin')
  ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }

  foreach ($CandidateWixBin in $CandidateWixBins) {
    $Candle = Join-Path $CandidateWixBin 'candle.exe'
    $Light = Join-Path $CandidateWixBin 'light.exe'
    if ((Test-Path -LiteralPath $Candle -PathType Leaf) -and
        (Test-Path -LiteralPath $Light -PathType Leaf)) {
      $env:Path = "$CandidateWixBin;$env:Path"
      Write-Host "WiX: $CandidateWixBin"
      return
    }
  }
}

function Add-SignToolToPath {
  if (Get-Command 'signtool.exe' -ErrorAction SilentlyContinue) {
    return
  }

  $WindowsKitsBin = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10/bin'
  if (-not (Test-Path -LiteralPath $WindowsKitsBin -PathType Container)) {
    return
  }

  $SignTool = Get-ChildItem -LiteralPath $WindowsKitsBin -Recurse -Filter 'signtool.exe' |
    Where-Object { $_.FullName -match '\\x64\\signtool\.exe$' } |
    Sort-Object FullName -Descending |
    Select-Object -First 1

  if ($SignTool) {
    $env:Path = "$($SignTool.DirectoryName);$env:Path"
    Write-Host "signtool: $($SignTool.FullName)"
  }
}

function Resolve-WindowsRuntimeImage {
  param([string]$RuntimeId)

  $RuntimeDir = Join-Path $RuntimesDir $RuntimeId
  if (-not (Test-Path -LiteralPath $RuntimeDir -PathType Container)) {
    Fail "runtime directory not found: $RuntimeDir"
  }

  $CandidateRuntimeImages = @(Get-ChildItem -LiteralPath $RuntimeDir -Directory |
    Where-Object {
      (Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') -PathType Leaf) -and
      (Test-Path -LiteralPath (Join-Path $_.FullName 'release') -PathType Leaf)
    })

  if ($CandidateRuntimeImages.Count -eq 0) {
    Fail "no runtime image found in $RuntimeDir"
  }
  if ($CandidateRuntimeImages.Count -gt 1) {
    $RuntimeImageNames = ($CandidateRuntimeImages | Select-Object -ExpandProperty Name) -join ', '
    Fail "expected one runtime image in $RuntimeDir, found: $RuntimeImageNames"
  }

  return $CandidateRuntimeImages[0].FullName
}

function Resolve-Jpackage {
  param([string]$WindowsX64Runtime)

  if ($env:JPACKAGE) {
    if (-not (Test-Path -LiteralPath $env:JPACKAGE -PathType Leaf)) {
      Fail "JPACKAGE does not exist: $env:JPACKAGE"
    }
    return $env:JPACKAGE
  }

  $RuntimeJpackage = Join-Path $WindowsX64Runtime 'bin/jpackage.exe'
  if (Test-Path -LiteralPath $RuntimeJpackage -PathType Leaf) {
    return $RuntimeJpackage
  }

  $Command = Get-Command 'jpackage.exe' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  $Command = Get-Command 'jpackage' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  Fail 'jpackage not found. Install a JDK or set JPACKAGE.'
}

function Open-Jar {
  param([string]$Path)
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  return [System.IO.Compression.ZipFile]::OpenRead($Path)
}

function Get-JarEntryText {
  param(
    [System.IO.Compression.ZipArchive]$Jar,
    [string]$EntryName
  )

  $Entry = $Jar.GetEntry($EntryName)
  if (-not $Entry) {
    return $null
  }

  $Reader = New-Object System.IO.StreamReader($Entry.Open())
  try {
    return $Reader.ReadToEnd()
  } finally {
    $Reader.Dispose()
  }
}

function Get-ManifestValue {
  param(
    [string]$Manifest,
    [string]$Name
  )

  foreach ($Line in ($Manifest -split "`r?`n")) {
    if ($Line.StartsWith("$Name`: ")) {
      return $Line.Substring($Name.Length + 2).Trim()
    }
  }
  return ''
}

if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
  Fail 'MSI packaging must run on Windows'
}

Set-Location -LiteralPath $RepoDir
Require-Command 'gh'

Add-WixToPath
Require-Command 'candle.exe'
Require-Command 'light.exe'

$RuntimesDir = (Resolve-Path -LiteralPath $RuntimesDir).Path
$WorkDir = (New-Item -ItemType Directory -Force -Path $WorkDir).FullName

Write-Host 'Checking GitHub authentication'
& gh auth status | Out-Null
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}

if (-not $Tag) {
  $Tag = (& gh release list --limit 1 --json tagName --jq '.[0].tagName')
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
  $Tag = $Tag.Trim()
}
if (-not $Tag) {
  Fail 'could not determine latest GitHub release tag'
}

$DownloadDir = Join-Path $WorkDir 'download'
$ArtifactsDir = Join-Path $WorkDir 'artifacts'
$StageRoot = Join-Path $WorkDir 'stage'
$JarPath = Join-Path $DownloadDir $MainJarName

Remove-Item -LiteralPath $DownloadDir, $ArtifactsDir, $StageRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $DownloadDir, $ArtifactsDir, $StageRoot | Out-Null

Write-Host "Downloading $MainJarName from GitHub release $Tag..."
& gh release download $Tag --pattern $MainJarName --dir $DownloadDir --clobber
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
  Fail "downloaded release did not contain $MainJarName"
}

$Jar = Open-Jar $JarPath
try {
  $Manifest = Get-JarEntryText $Jar 'META-INF/MANIFEST.MF'
  if (-not $Manifest) {
    Fail "JAR does not contain META-INF/MANIFEST.MF: $JarPath"
  }

  $Version = Get-ManifestValue $Manifest 'Implementation-Version'
  if (-not $Version) {
    Fail "JAR manifest does not contain Implementation-Version: $JarPath"
  }
  if ($Version.EndsWith('-SNAPSHOT')) {
    Fail "refusing to publish snapshot version: $Version"
  }

  $MainClassEntry = "$($MainClass.Replace('.', '/')).class"
  if (-not $Jar.GetEntry($MainClassEntry)) {
    Fail "JAR does not contain main class $MainClass ($MainClassEntry): $JarPath"
  }

  if (-not $SkipVerifyDocs -and -not $Jar.GetEntry('FDSwarmDocs/index.html')) {
    Fail "JAR does not contain FDSwarmDocs/index.html: $JarPath"
  }
} finally {
  $Jar.Dispose()
}

$InstallerVersion = ($Version -split '-', 2)[0]
if ($InstallerVersion -notmatch '^\d+\.\d+\.\d+$') {
  Fail "MSI app version must be numeric major.minor.patch, got: $InstallerVersion"
}

$ReleaseTag = "v$Version"
Write-Host "Using release version $Version"

& gh release view $ReleaseTag 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
  Write-Host "GitHub release exists: $ReleaseTag"
} else {
  & gh release create $ReleaseTag --title $ReleaseTag --generate-notes
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
  Write-Host "Created GitHub release: $ReleaseTag"
}

$RuntimeImages = @(
  @{
    Id = 'windows-x64'
    Path = (Resolve-WindowsRuntimeImage 'windows-x64')
  },
  @{
    Id = 'windows-arm64'
    Path = (Resolve-WindowsRuntimeImage 'windows-arm64')
  }
)

$Jpackage = Resolve-Jpackage $RuntimeImages[0].Path
$IconPath = Join-Path $RepoDir 'fdswarm/resources/icons/icon.ico'

Write-Host 'Building Windows MSI installers'
Write-Host "JAR: $JarPath"
Write-Host "Version: $Version"
Write-Host "MSI version: $InstallerVersion"
Write-Host "Runtimes: $RuntimesDir"
Write-Host "jpackage: $Jpackage"

foreach ($RuntimeImage in $RuntimeImages) {
  $RuntimeId = $RuntimeImage.Id
  $RuntimePath = $RuntimeImage.Path
  $RuntimeWorkDir = Join-Path $StageRoot $RuntimeId
  $InputDir = Join-Path $RuntimeWorkDir 'input'
  $DestDir = Join-Path $RuntimeWorkDir 'jpackage'
  $ConsoleLauncher = Join-Path $RuntimeWorkDir 'FdSwarmConsole.properties'
  $ArtifactName = "$AppName-$Version-$RuntimeId.msi"
  $ArtifactPath = Join-Path $ArtifactsDir $ArtifactName

  Remove-Item -LiteralPath $InputDir, $DestDir -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $InputDir, $DestDir | Out-Null
  Copy-Item -LiteralPath $JarPath -Destination (Join-Path $InputDir $MainJarName) -Force

  @(
    "main-jar=$MainJarName"
    "main-class=$MainClass"
    'win-console=true'
  ) | Set-Content -LiteralPath $ConsoleLauncher -Encoding ASCII

  $JpackageArgs = @(
    '--type', 'msi',
    '--name', $AppName,
    '--vendor', $Vendor,
    '--app-version', $InstallerVersion,
    '--dest', $DestDir,
    '--input', $InputDir,
    '--main-jar', $MainJarName,
    '--main-class', $MainClass,
    '--runtime-image', $RuntimePath,
    '--add-launcher', "$($AppName)Console=$ConsoleLauncher",
    '--win-menu',
    '--win-menu-group', $AppName,
    '--win-shortcut',
    '--win-upgrade-uuid', $WinUpgradeUuid,
    '--verbose'
  )

  if (Test-Path -LiteralPath $IconPath -PathType Leaf) {
    $JpackageArgs += @('--icon', $IconPath)
  }

  Write-Host ''
  Write-Host "Packaging $RuntimeId from $RuntimePath"
  & $Jpackage @JpackageArgs
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }

  $ProducedMsi = Get-ChildItem -LiteralPath $DestDir -Filter '*.msi' |
    Sort-Object Name |
    Select-Object -Last 1

  if (-not $ProducedMsi) {
    Fail "jpackage completed but no MSI was found in $DestDir"
  }

  Move-Item -LiteralPath $ProducedMsi.FullName -Destination $ArtifactPath -Force

  if (-not $SkipSign -and $CertificateThumbprint) {
    Add-SignToolToPath
    Require-Command 'signtool.exe'

    Write-Host "Signing MSI with certificate thumbprint $CertificateThumbprint"
    & signtool.exe sign /sha1 $CertificateThumbprint /fd SHA256 /tr $TimestampServer /td SHA256 $ArtifactPath
    if ($LASTEXITCODE -ne 0) {
      exit $LASTEXITCODE
    }
  } elseif (-not $SkipSign) {
    Write-Host 'No Windows code-signing certificate thumbprint provided; MSI will be unsigned.'
  }

  Write-Host "Built $ArtifactPath"
}

Write-Host "Uploading MSI installers to GitHub release $ReleaseTag..."
foreach ($Artifact in (Get-ChildItem -LiteralPath $ArtifactsDir -Filter '*.msi')) {
  & gh release upload $ReleaseTag $($Artifact.FullName) --clobber
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
  Write-Host "Uploaded $($Artifact.Name)"
}

if (-not $KeepWork) {
  Remove-Item -LiteralPath $StageRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Published MSI installers for $ReleaseTag"
