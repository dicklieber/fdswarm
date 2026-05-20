$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoDir = (Resolve-Path (Join-Path $ScriptDir '..')).Path

$AppName = 'FdSwarm'
$MainClass = 'fdswarm.FdSwarm'
$Vendor = 'FdSwarm'
$AssemblyJar = if ($env:FDSWARM_ASSEMBLY_JAR) {
  $env:FDSWARM_ASSEMBLY_JAR
} else {
  Join-Path $RepoDir 'out/fdswarm/assembly.dest/fdswarm.jar'
}
$InstallerVersion = if ($env:FDSWARM_VERSION) { ($env:FDSWARM_VERSION).TrimStart('v') } else { '0.0.0' }
$BuildNumber = if ($env:FDSWARM_BUILD) { $env:FDSWARM_BUILD } else { '0' }
$Jpackage = if ($env:JPACKAGE) { $env:JPACKAGE } else { 'jpackage' }
$WorkDir = Join-Path $RepoDir 'out/fdswarm/msi.dest'
$ArtifactsDir = Join-Path $RepoDir 'release/artifacts'
$WinUpgradeUuid = '8F095DE2-D316-43A7-94C3-7702217CAE1D'
$WindowsRuntimesDir = Join-Path $RepoDir 'runtimes/windows'

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
    throw "Runtime directory not found: $RuntimeDir"
  }

  $CandidateRuntimeImages = @(Get-ChildItem -LiteralPath $RuntimeDir -Directory |
    Where-Object {
      (Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') -PathType Leaf) -and
      (Test-Path -LiteralPath (Join-Path $_.FullName 'release') -PathType Leaf)
    })

  if ($CandidateRuntimeImages.Count -eq 0) {
    throw "No runtime image found in $RuntimeDir"
  }

  if ($CandidateRuntimeImages.Count -gt 1) {
    $RuntimeImageNames = ($CandidateRuntimeImages | Select-Object -ExpandProperty Name) -join ', '
    throw "Expected one runtime image in $RuntimeDir, found: $RuntimeImageNames"
  }

  return $CandidateRuntimeImages[0].FullName
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

if ($InstallerVersion -notmatch '^\d+\.\d+\.\d+$') {
  throw "FDSWARM_VERSION must be numeric major.minor.patch, got: $InstallerVersion"
}

if ($BuildNumber -notmatch '^\d+$') {
  throw "FDSWARM_BUILD must be numeric, got: $BuildNumber"
}

if (-not (Test-Path -LiteralPath $AssemblyJar -PathType Leaf)) {
  throw "Assembly JAR not found: $AssemblyJar"
}

$MainClassEntry = "$($MainClass.Replace('.', '/')).class"
if (-not (& jar tf $AssemblyJar | Where-Object { $_ -eq $MainClassEntry })) {
  throw "Assembly JAR does not contain main class $MainClass ($MainClassEntry): $AssemblyJar"
}

if (-not (Get-Command $Jpackage -ErrorAction SilentlyContinue)) {
  throw "jpackage not found: $Jpackage"
}

Add-WixToPath

$ArtifactVersion = $InstallerVersion
if ($BuildNumber -ne '0') {
  $ArtifactVersion = "$InstallerVersion-build$BuildNumber"
}

New-Item -ItemType Directory -Force -Path $WorkDir, $ArtifactsDir | Out-Null

Write-Host 'Building MSI installers'
Write-Host "Repo: $RepoDir"
Write-Host "JAR: $AssemblyJar"
Write-Host "Version: $InstallerVersion"
Write-Host "Build: $BuildNumber"
Write-Host "jpackage: $Jpackage"

foreach ($RuntimeImage in $RuntimeImages) {
  $RuntimeId = $RuntimeImage.Id
  $RuntimePath = $RuntimeImage.Path
  $RuntimeWorkDir = Join-Path $WorkDir $RuntimeId
  $InputDir = Join-Path $RuntimeWorkDir 'input'
  $DestDir = Join-Path $RuntimeWorkDir 'jpackage'
  $ConsoleLauncher = Join-Path $RuntimeWorkDir 'FdSwarmConsole.properties'

  if (-not (Test-Path -LiteralPath $RuntimePath -PathType Container)) {
    throw "Runtime image not found: $RuntimePath"
  }

  Remove-Item -LiteralPath $InputDir, $DestDir -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $InputDir, $DestDir | Out-Null
  Copy-Item -LiteralPath $AssemblyJar -Destination (Join-Path $InputDir 'fdswarm.jar') -Force

  @(
    'main-jar=fdswarm.jar'
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
    '--main-jar', 'fdswarm.jar',
    '--main-class', $MainClass,
    '--runtime-image', $RuntimePath,
    '--add-launcher', "$($AppName)Console=$ConsoleLauncher",
    '--verbose',
    '--win-menu',
    '--win-menu-group', $AppName,
    '--win-shortcut',
    '--win-upgrade-uuid', $WinUpgradeUuid
  )

  $IconPath = Join-Path $RepoDir 'fdswarm/resources/icons/icon.ico'
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
    throw "jpackage completed but no MSI was found in $DestDir"
  }

  $Artifact = Join-Path $ArtifactsDir "$AppName-$ArtifactVersion-$RuntimeId.msi"
  Move-Item -LiteralPath $ProducedMsi.FullName -Destination $Artifact -Force
  Write-Host "MSI: $Artifact"
}
