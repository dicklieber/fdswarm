param(
  [string]$Tag = '',
  [string]$RuntimesDir = '',
  [string]$WorkDir = '',
  [string]$Iscc = '',
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
$AppId = '{{8F095DE2-D316-43A7-94C3-7702217CAE1D}'

if (-not $RuntimesDir) {
  $RuntimesDir = Join-Path $RepoDir 'fdswarm-runtimes'
}
if (-not $WorkDir) {
  $WorkDir = Join-Path $RepoDir 'release/inno-installers'
}

function Fail {
  param([string]$Message)
  throw "publish-inno-installers: $Message"
}

function Require-Command {
  param([string]$CommandName)
  if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
    Fail "required command not found: $CommandName"
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

function Resolve-InnoSetupCompiler {
  if ($Iscc) {
    if (-not (Test-Path -LiteralPath $Iscc -PathType Leaf)) {
      Fail "ISCC does not exist: $Iscc"
    }
    return (Resolve-Path -LiteralPath $Iscc).Path
  }

  if ($env:ISCC) {
    if (-not (Test-Path -LiteralPath $env:ISCC -PathType Leaf)) {
      Fail "ISCC does not exist: $env:ISCC"
    }
    return (Resolve-Path -LiteralPath $env:ISCC).Path
  }

  $Command = Get-Command 'iscc.exe' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  $Command = Get-Command 'iscc' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  $Candidates = @(
    @(
      (Join-Path $env:LOCALAPPDATA 'Programs/Inno Setup 6/ISCC.exe'),
      (Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6/ISCC.exe'),
      (Join-Path $env:ProgramFiles 'Inno Setup 6/ISCC.exe')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
  )

  if ($Candidates.Count -gt 0) {
    return $Candidates[0]
  }

  Fail 'ISCC not found. Install Inno Setup 6 or pass -Iscc.'
}

function Resolve-WindowsRuntimeImage {
  param(
    [string]$RuntimeId,
    [string]$ExpectedMachine
  )

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

  $RuntimeImage = $CandidateRuntimeImages[0].FullName
  $JavaExe = Join-Path $RuntimeImage 'bin/java.exe'
  $ActualMachine = Get-PeMachine $JavaExe
  if ($ActualMachine -ne $ExpectedMachine) {
    Fail "$RuntimeId runtime java.exe must be $ExpectedMachine, got $ActualMachine`: $JavaExe"
  }

  return $RuntimeImage
}

function Get-PeMachine {
  param([string]$Path)

  $Bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path))
  if ($Bytes.Length -lt 0x40 -or $Bytes[0] -ne 0x4d -or $Bytes[1] -ne 0x5a) {
    Fail "not a PE executable: $Path"
  }

  $PeOffset = [BitConverter]::ToInt32($Bytes, 0x3c)
  $Machine = [BitConverter]::ToUInt16($Bytes, $PeOffset + 4)

  switch ($Machine) {
    0x8664 { return 'x64' }
    0xAA64 { return 'arm64' }
    0x14c { return 'x86' }
    default { return ('0x{0:X4}' -f $Machine) }
  }
}

function Resolve-JarTool {
  if ($env:JAR) {
    if (-not (Test-Path -LiteralPath $env:JAR -PathType Leaf)) {
      Fail "JAR does not exist: $env:JAR"
    }
    return $env:JAR
  }

  $Command = Get-Command 'jar.exe' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  $Command = Get-Command 'jar' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  Fail 'jar not found. Install a JDK or set JAR.'
}

function Get-JarEntries {
  param(
    [string]$JarTool,
    [string]$Path
  )

  $Entries = & $JarTool tf $Path
  if ($LASTEXITCODE -ne 0) {
    Fail "could not list JAR entries: $Path"
  }

  $EntrySet = New-Object 'System.Collections.Generic.HashSet[string]'
  foreach ($Entry in $Entries) {
    [void]$EntrySet.Add($Entry)
  }
  return ,$EntrySet
}

function Get-JarEntryText {
  param(
    [string]$JarTool,
    [string]$Path,
    [string]$EntryName
  )

  $TempDir = Join-Path ([System.IO.Path]::GetTempPath()) "fdswarm-jar-$([System.Guid]::NewGuid().ToString('N'))"
  New-Item -ItemType Directory -Force -Path $TempDir | Out-Null
  Push-Location -LiteralPath $TempDir
  try {
    & $JarTool xf $Path $EntryName
    if ($LASTEXITCODE -ne 0) {
      Fail "could not extract $EntryName from JAR: $Path"
    }

    $EntryPath = Join-Path $TempDir $EntryName
    if (-not (Test-Path -LiteralPath $EntryPath -PathType Leaf)) {
      return $null
    }

    return Get-Content -LiteralPath $EntryPath -Raw
  } finally {
    Pop-Location
    Remove-Item -LiteralPath $TempDir -Recurse -Force -ErrorAction SilentlyContinue
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

function Copy-DirectoryContents {
  param(
    [string]$Source,
    [string]$Destination
  )

  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  foreach ($Item in (Get-ChildItem -LiteralPath $Source -Force)) {
    Copy-Item -LiteralPath $Item.FullName -Destination $Destination -Recurse -Force
  }
}

function Stage-ApplicationConf {
  param([string]$StageDir)

  $Candidates = @(
    (Join-Path $RepoDir 'application.conf'),
    (Join-Path $RepoDir 'fdswarm/resources/application.conf')
  )

  foreach ($Candidate in $Candidates) {
    if (Test-Path -LiteralPath $Candidate -PathType Leaf) {
      $ConfDir = Join-Path $StageDir 'conf'
      New-Item -ItemType Directory -Force -Path $ConfDir | Out-Null
      Copy-Item -LiteralPath $Candidate -Destination (Join-Path $ConfDir 'application.conf') -Force
      return
    }
  }
}

function Write-WindowsLaunchers {
  param(
    [string]$BinDir,
    [string]$RuntimeId
  )

  $Launcher = Join-Path $BinDir 'fdswarm.bat'
  $ConsoleLauncher = Join-Path $BinDir 'fdswarm-console.bat'

  @(
    '@echo off'
    'setlocal'
    'set "APP_HOME=%~dp0.."'
    'pushd "%APP_HOME%"'
    'start "" "runtime\bin\javaw.exe" -jar "lib\fdswarm.jar" %*'
    'popd'
    'endlocal'
  ) | Set-Content -LiteralPath $Launcher -Encoding ASCII

  @(
    '@echo off'
    'setlocal'
    'set "APP_HOME=%~dp0.."'
    'pushd "%APP_HOME%"'
    '"runtime\bin\java.exe" -jar "lib\fdswarm.jar" %*'
    'popd'
    'endlocal'
  ) | Set-Content -LiteralPath $ConsoleLauncher -Encoding ASCII

  @(
    "@echo off"
    "echo This FdSwarm package is for $RuntimeId."
  ) | Set-Content -LiteralPath (Join-Path $BinDir 'runtime-platform.txt') -Encoding ASCII
}

function Write-InnoScript {
  param(
    [string]$ScriptPath,
    [string]$StageDir,
    [string]$ArtifactsDir,
    [string]$Version,
    [string]$RuntimeId,
    [string]$InnoArchitecture,
    [string]$ArtifactBaseName
  )

  $EscapedStage = $StageDir -replace '\\', '\\'
  $EscapedArtifacts = $ArtifactsDir -replace '\\', '\\'
  $IconPath = Join-Path $RepoDir 'fdswarm/resources/icons/icon.ico'
  $SetupIconLine = ''
  if (Test-Path -LiteralPath $IconPath -PathType Leaf) {
    $EscapedIcon = $IconPath -replace '\\', '\\'
    $SetupIconLine = "SetupIconFile=$EscapedIcon"
  }

  $Lines = @(
    '#define AppName "FdSwarm"'
    "#define AppVersion `"$Version`""
    "#define SourceDir `"$EscapedStage`""
    ''
    '[Setup]'
    "AppId=$AppId"
    'AppName={#AppName}'
    'AppVersion={#AppVersion}'
    "AppPublisher=$Vendor"
    'DefaultDirName={autopf}\FdSwarm'
    'DefaultGroupName=FdSwarm'
    'DisableProgramGroupPage=yes'
    'DisableWelcomePage=no'
    "OutputDir=$EscapedArtifacts"
    "OutputBaseFilename=$ArtifactBaseName"
    'Compression=lzma2'
    'SolidCompression=yes'
    "ArchitecturesAllowed=$InnoArchitecture"
    "ArchitecturesInstallIn64BitMode=$InnoArchitecture"
    'PrivilegesRequired=admin'
    'UninstallDisplayName=FdSwarm'
    'WizardStyle=modern'
  )

  if ($SetupIconLine) {
    $Lines += $SetupIconLine
  }

  $Lines += @(
    ''
    '[Files]'
    'Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion'
    ''
    '[Icons]'
    'Name: "{group}\FdSwarm"; Filename: "{app}\bin\fdswarm.bat"; WorkingDir: "{app}"'
    'Name: "{group}\FdSwarm Console"; Filename: "{app}\bin\fdswarm-console.bat"; WorkingDir: "{app}"'
    'Name: "{autodesktop}\FdSwarm"; Filename: "{app}\bin\fdswarm.bat"; WorkingDir: "{app}"; Tasks: desktopicon'
    ''
    '[Tasks]'
    'Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: unchecked'
    ''
    '[Run]'
    'Filename: "{app}\bin\fdswarm.bat"; Description: "Launch FdSwarm"; Flags: nowait postinstall skipifsilent'
  )

  $Lines | Set-Content -LiteralPath $ScriptPath -Encoding UTF8
}

function Build-InnoInstaller {
  param(
    [hashtable]$Runtime,
    [string]$Version
  )

  $RuntimeId = $Runtime.Id
  $RuntimePath = $Runtime.Path
  $RuntimeWorkDir = Join-Path $StageRoot $RuntimeId
  $StageDir = Join-Path $RuntimeWorkDir 'FdSwarm'
  $ScriptPath = Join-Path $RuntimeWorkDir "$RuntimeId.iss"
  $ArtifactBaseName = "$AppName-$Version-$RuntimeId-setup"
  $ArtifactPath = Join-Path $ArtifactsDir "$ArtifactBaseName.exe"

  Remove-Item -LiteralPath $RuntimeWorkDir, $ArtifactPath -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path (Join-Path $StageDir 'bin'), (Join-Path $StageDir 'lib'), (Join-Path $StageDir 'runtime') | Out-Null

  Copy-Item -LiteralPath $JarPath -Destination (Join-Path $StageDir 'lib/fdswarm.jar') -Force
  Copy-DirectoryContents -Source $RuntimePath -Destination (Join-Path $StageDir 'runtime')
  Stage-ApplicationConf $StageDir
  Write-WindowsLaunchers -BinDir (Join-Path $StageDir 'bin') -RuntimeId $RuntimeId

  Write-InnoScript `
    -ScriptPath $ScriptPath `
    -StageDir $StageDir `
    -ArtifactsDir $ArtifactsDir `
    -Version $Version `
    -RuntimeId $RuntimeId `
    -InnoArchitecture $Runtime.InnoArchitecture `
    -ArtifactBaseName $ArtifactBaseName

  Write-Host ''
  Write-Host "Packaging $RuntimeId from $RuntimePath"
  & $InnoCompiler $ScriptPath
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }

  if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) {
    Fail "Inno Setup completed but no installer was found: $ArtifactPath"
  }

  if (-not $SkipSign -and $CertificateThumbprint) {
    Add-SignToolToPath
    Require-Command 'signtool.exe'

    Write-Host "Signing installer with certificate thumbprint $CertificateThumbprint"
    & signtool.exe sign /sha1 $CertificateThumbprint /fd SHA256 /tr $TimestampServer /td SHA256 $ArtifactPath
    if ($LASTEXITCODE -ne 0) {
      exit $LASTEXITCODE
    }
  } elseif (-not $SkipSign) {
    Write-Host 'No Windows code-signing certificate thumbprint provided; installer will be unsigned.'
  }

  Write-Host "Built $ArtifactPath"
}

if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
  Fail 'Inno packaging must run on Windows'
}

Set-Location -LiteralPath $RepoDir
Require-Command 'gh'
$JarTool = Resolve-JarTool
$InnoCompiler = Resolve-InnoSetupCompiler

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

$JarEntries = Get-JarEntries $JarTool $JarPath

$Manifest = Get-JarEntryText $JarTool $JarPath 'META-INF/MANIFEST.MF'
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
if (-not $JarEntries.Contains($MainClassEntry)) {
  Fail "JAR does not contain main class $MainClass ($MainClassEntry): $JarPath"
}

if (-not $SkipVerifyDocs -and -not $JarEntries.Contains('FDSwarmDocs/index.html')) {
  Fail "JAR does not contain FDSwarmDocs/index.html: $JarPath"
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
    Path = (Resolve-WindowsRuntimeImage 'windows-x64' 'x64')
    InnoArchitecture = 'x64compatible'
  },
  @{
    Id = 'windows-arm64'
    Path = (Resolve-WindowsRuntimeImage 'windows-arm64' 'arm64')
    InnoArchitecture = 'arm64'
  }
)

Write-Host 'Building Windows Inno Setup installers'
Write-Host "JAR: $JarPath"
Write-Host "Version: $Version"
Write-Host "Runtimes: $RuntimesDir"
Write-Host "ISCC: $InnoCompiler"

foreach ($RuntimeImage in $RuntimeImages) {
  Build-InnoInstaller -Runtime $RuntimeImage -Version $Version
}

Write-Host "Uploading Inno installers to GitHub release $ReleaseTag..."
foreach ($Artifact in (Get-ChildItem -LiteralPath $ArtifactsDir -Filter '*.exe')) {
  & gh release upload $ReleaseTag $($Artifact.FullName) --clobber
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
  Write-Host "Uploaded $($Artifact.Name)"
}

if (-not $KeepWork) {
  Remove-Item -LiteralPath $StageRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Published Inno installers for $ReleaseTag"
