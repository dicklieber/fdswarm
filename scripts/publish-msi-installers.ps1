param(
  [string]$Tag = '',
  [switch]$Publish,
  [switch]$SkipVerifyDocs,
  [switch]$SkipSign,
  [string]$JarUrl = 'https://github.com/dicklieber/fdswarm/releases/latest/download/fdswarm.jar',
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
$WorkDir = Join-Path $RepoDir 'out/fdswarm/msi-release.dest'
$ArtifactsDir = Join-Path $RepoDir 'release/artifacts'
$WindowsRuntimesDir = Join-Path $RepoDir 'fdswarm-runtimes'

function Fail {
  param([string]$Message)
  throw "release-windows-msi: $Message"
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

function Resolve-WindowsRuntimeImage {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RuntimeId
  )

  $RuntimeDir = Join-Path $WindowsRuntimesDir $RuntimeId
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

  Fail "jpackage not found. Install a JDK or set JPACKAGE."
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

function Open-Jar {
  param([string]$Path)
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $Archive = $null
  try {
    $Archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    if ($Archive.Entries.Count -eq 0) {
      Fail "JAR contains no ZIP entries: $Path"
    }

    return $Archive
  } catch [System.IO.InvalidDataException] {
    if ($Archive) {
      $Archive.Dispose()
    }
    Fail "JAR is not a readable ZIP archive: $Path. $($_.Exception.Message)"
  } catch {
    if ($Archive) {
      $Archive.Dispose()
    }
    throw
  }
}

function Get-ZipStartOffset {
  param([string]$Path)

  $MaxSearchBytes = 1024 * 1024
  $Stream = [System.IO.File]::OpenRead($Path)
  try {
    $BytesToRead = [Math]::Min($Stream.Length, $MaxSearchBytes)
    $Buffer = New-Object byte[] $BytesToRead
    $Read = $Stream.Read($Buffer, 0, $Buffer.Length)

    for ($Index = 0; $Index -le ($Read - 4); $Index++) {
      if ($Buffer[$Index] -eq 0x50 -and
          $Buffer[$Index + 1] -eq 0x4B -and
          $Buffer[$Index + 2] -eq 0x03 -and
          $Buffer[$Index + 3] -eq 0x04) {
        return $Index
      }
    }

    return -1
  } finally {
    $Stream.Dispose()
  }
}

function Normalize-JarForZipArchive {
  param([string]$Path)

  $ZipStartOffset = Get-ZipStartOffset $Path
  if ($ZipStartOffset -lt 0) {
    Fail "downloaded JAR does not contain a ZIP local file header in the first 1 MiB: $Path"
  }

  if ($ZipStartOffset -eq 0) {
    return
  }

  $PlainJarPath = "$Path.plain"
  Remove-Item -LiteralPath $PlainJarPath -Force -ErrorAction SilentlyContinue

  Write-Host "Normalizing executable JAR by removing $ZipStartOffset-byte launcher prefix"

  $Source = [System.IO.File]::OpenRead($Path)
  try {
    $Destination = [System.IO.File]::Create($PlainJarPath)
    try {
      $Source.Position = $ZipStartOffset
      $Source.CopyTo($Destination)
    } finally {
      $Destination.Dispose()
    }
  } finally {
    $Source.Dispose()
  }

  Move-Item -LiteralPath $PlainJarPath -Destination $Path -Force
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

function Save-ReleaseJar {
  param(
    [string]$Url,
    [string]$Destination
  )

  $DestinationDir = Split-Path -Parent $Destination
  New-Item -ItemType Directory -Force -Path $DestinationDir | Out-Null

  Write-Host "Downloading JAR: $Url"
  Invoke-WebRequest -Uri $Url -OutFile $Destination -MaximumRedirection 10

  if (-not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
    Fail "downloaded JAR was not created: $Destination"
  }

  $JarFile = Get-Item -LiteralPath $Destination
  if ($JarFile.Length -le 0) {
    Fail "downloaded JAR is empty: $Destination"
  }

  Normalize-JarForZipArchive $Destination
}

if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
  Fail 'MSI packaging must run on Windows'
}

Add-WixToPath
Require-Command 'candle.exe'
Require-Command 'light.exe'

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

New-Item -ItemType Directory -Force -Path $WorkDir, $ArtifactsDir | Out-Null

$JarPath = Join-Path (Join-Path $WorkDir 'download') $MainJarName
Save-ReleaseJar $JarUrl $JarPath

$Jar = Open-Jar $JarPath
try {
  $Manifest = Get-JarEntryText $Jar 'META-INF/MANIFEST.MF'
  if (-not $Manifest) {
    Fail "JAR does not contain META-INF/MANIFEST.MF: $JarPath"
  }

  $JarVersion = Get-ManifestValue $Manifest 'Implementation-Version'
  if (-not $JarVersion) {
    Fail "JAR manifest does not contain Implementation-Version: $JarPath"
  }
  if ($JarVersion.EndsWith('-SNAPSHOT')) {
    Fail "refusing to package snapshot JAR version: $JarVersion"
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

$InstallerVersion = ($JarVersion -split '-', 2)[0]
if ($InstallerVersion -notmatch '^\d+\.\d+\.\d+$') {
  Fail "MSI app version must be numeric major.minor.patch, got: $InstallerVersion"
}

if (-not $Tag) {
  $Tag = "v$JarVersion"
}

$IconPath = Join-Path $RepoDir 'fdswarm/resources/icons/icon.ico'

Write-Host 'Building Windows MSI installers'
Write-Host "JAR: $JarPath"
Write-Host "JAR URL: $JarUrl"
Write-Host "Version: $JarVersion"
Write-Host "MSI version: $InstallerVersion"
Write-Host "Runtimes: $WindowsRuntimesDir"
Write-Host "jpackage: $Jpackage"

foreach ($RuntimeImage in $RuntimeImages) {
  $RuntimeId = $RuntimeImage.Id
  $RuntimePath = $RuntimeImage.Path
  $RuntimeWorkDir = Join-Path $WorkDir $RuntimeId
  $InputDir = Join-Path $RuntimeWorkDir 'input'
  $DestDir = Join-Path $RuntimeWorkDir 'jpackage'
  $ConsoleLauncher = Join-Path $RuntimeWorkDir 'FdSwarmConsole.properties'
  $ArtifactName = "$AppName-$JarVersion-$RuntimeId.msi"
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

  if ($Publish) {
    Require-Command 'gh'
    Write-Host "Publishing $ArtifactPath to GitHub release $Tag"
    & gh auth status | Out-Null
    if ($LASTEXITCODE -ne 0) {
      exit $LASTEXITCODE
    }
    & gh release view $Tag | Out-Null
    if ($LASTEXITCODE -ne 0) {
      exit $LASTEXITCODE
    }
    & gh release upload $Tag $ArtifactPath --clobber
    if ($LASTEXITCODE -ne 0) {
      exit $LASTEXITCODE
    }
  }

  Write-Host "MSI: $ArtifactPath"
}
