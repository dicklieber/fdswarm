$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoDir = (Resolve-Path (Join-Path $ScriptDir '..')).Path

$AppName = 'FdSwarm'
$MainClass = 'fdswarm.FdSwarm'
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

$RuntimeImages = @(
  @{
    Id = 'windows-x64'
    Path = (Join-Path $RepoDir 'release/jdks/windows-x64/runtime')
  },
  @{
    Id = 'windows-arm64'
    Path = (Join-Path $RepoDir 'release/jdks/windows-arm64/runtime')
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

if (-not (Get-Command $Jpackage -ErrorAction SilentlyContinue)) {
  throw "jpackage not found: $Jpackage"
}

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
    '--app-version', $InstallerVersion,
    '--dest', $DestDir,
    '--input', $InputDir,
    '--main-jar', 'fdswarm.jar',
    '--main-class', $MainClass,
    '--runtime-image', $RuntimePath,
    '--add-launcher', "$($AppName)Console=$ConsoleLauncher",
    '--win-menu',
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
