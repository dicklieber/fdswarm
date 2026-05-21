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
$WorkDir = Join-Path $RepoDir 'out/fdswarm/launch4j.dest'
$ArtifactsDir = Join-Path $RepoDir 'release/artifacts'
$WindowsRuntimesDir = Join-Path $RepoDir 'runtimes/windows'
$Launch4jVersion = if ($env:LAUNCH4J_VERSION) { $env:LAUNCH4J_VERSION } else { '3.50' }
$Launch4jDownloadUrl = if ($env:LAUNCH4J_DOWNLOAD_URL) {
  $env:LAUNCH4J_DOWNLOAD_URL
} else {
  "https://sourceforge.net/projects/launch4j/files/launch4j-3/$Launch4jVersion/launch4j-$Launch4jVersion-win32.zip/download"
}

function ConvertTo-XmlText {
  param(
    [AllowNull()]
    [string]$Value
  )

  return [System.Security.SecurityElement]::Escape($Value)
}

function Find-Launch4jExecutable {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RootDir
  )

  if (-not (Test-Path -LiteralPath $RootDir -PathType Container)) {
    return $null
  }

  $Candidates = @(
    (Join-Path $RootDir 'launch4jc.exe'),
    (Join-Path $RootDir 'launch4j.exe'),
    (Join-Path $RootDir 'bin/launch4jc.exe'),
    (Join-Path $RootDir 'bin/launch4j.exe')
  )

  foreach ($Candidate in $Candidates) {
    if (Test-Path -LiteralPath $Candidate -PathType Leaf) {
      return (Resolve-Path -LiteralPath $Candidate).Path
    }
  }

  $NestedCandidate = Get-ChildItem -LiteralPath $RootDir -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -eq 'launch4jc.exe' -or $_.Name -eq 'launch4j.exe' } |
    Sort-Object FullName |
    Select-Object -First 1

  if ($NestedCandidate) {
    return $NestedCandidate.FullName
  }

  return $null
}

function Test-ZipFileSignature {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Path
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    return $false
  }

  $Stream = [System.IO.File]::OpenRead($Path)
  try {
    if ($Stream.Length -lt 4) {
      return $false
    }

    $Bytes = New-Object byte[] 4
    [void]$Stream.Read($Bytes, 0, 4)
    return $Bytes[0] -eq 0x50 -and $Bytes[1] -eq 0x4B
  } finally {
    $Stream.Dispose()
  }
}

function Save-Launch4jArchive {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Url,

    [Parameter(Mandatory = $true)]
    [string]$ZipPath
  )

  Invoke-WebRequest -Uri $Url -OutFile $ZipPath
  if (Test-ZipFileSignature $ZipPath) {
    return
  }

  $DownloadPage = Get-Content -LiteralPath $ZipPath -Raw
  $RefreshMatch = [regex]::Match($DownloadPage, 'url=(https://[^"'']+)')
  if (-not $RefreshMatch.Success) {
    throw "Launch4j download did not produce a ZIP archive and no SourceForge mirror URL was found: $Url"
  }

  $MirrorUrl = [System.Net.WebUtility]::HtmlDecode($RefreshMatch.Groups[1].Value)
  Invoke-WebRequest -Uri $MirrorUrl -OutFile $ZipPath
  if (-not (Test-ZipFileSignature $ZipPath)) {
    throw "Launch4j mirror download did not produce a ZIP archive: $MirrorUrl"
  }
}

function Install-LocalLaunch4j {
  $ToolsDir = Join-Path $WorkDir 'tools'
  $InstallDir = Join-Path $ToolsDir "launch4j-$Launch4jVersion"
  $ZipPath = Join-Path $ToolsDir "launch4j-$Launch4jVersion-win32.zip"

  $Existing = Find-Launch4jExecutable $InstallDir
  if ($Existing) {
    return $Existing
  }

  New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

  Write-Host "Launch4j not found; downloading $Launch4jVersion to $ZipPath"
  Save-Launch4jArchive -Url $Launch4jDownloadUrl -ZipPath $ZipPath

  Remove-Item -LiteralPath $InstallDir -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive -LiteralPath $ZipPath -DestinationPath $InstallDir -Force

  $Installed = Find-Launch4jExecutable $InstallDir
  if ($Installed) {
    return $Installed
  }

  throw "Downloaded Launch4j but no launcher executable was found under: $InstallDir"
}

function Resolve-Launch4jCommand {
  if ($env:LAUNCH4J) {
    if (Test-Path -LiteralPath $env:LAUNCH4J -PathType Leaf) {
      return (Resolve-Path -LiteralPath $env:LAUNCH4J).Path
    }

    if (Get-Command $env:LAUNCH4J -ErrorAction SilentlyContinue) {
      return $env:LAUNCH4J
    }

    throw "LAUNCH4J is set but was not found: $env:LAUNCH4J"
  }

  if ($env:LAUNCH4J_HOME) {
    $HomeExecutable = Find-Launch4jExecutable $env:LAUNCH4J_HOME
    if ($HomeExecutable) {
      return $HomeExecutable
    }

    throw "LAUNCH4J_HOME is set but no Launch4j executable was found under: $env:LAUNCH4J_HOME"
  }

  $Command = Get-Command 'launch4jc.exe' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  $Command = Get-Command 'launch4jc' -ErrorAction SilentlyContinue
  if ($Command) {
    return $Command.Source
  }

  $CandidateDirs = @(
    (Join-Path $env:LOCALAPPDATA 'Programs/Launch4j'),
    (Join-Path $env:ProgramFiles 'Launch4j'),
    (Join-Path ${env:ProgramFiles(x86)} 'Launch4j')
  ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }

  foreach ($CandidateDir in $CandidateDirs) {
    $CandidateExecutable = Find-Launch4jExecutable $CandidateDir
    if ($CandidateExecutable) {
      return $CandidateExecutable
    }
  }

  return Install-LocalLaunch4j
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

function Write-Launch4jConfig {
  param(
    [Parameter(Mandatory = $true)]
    [string]$ConfigPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputExe,

    [Parameter(Mandatory = $true)]
    [string]$JarPath,

    [Parameter(Mandatory = $true)]
    [ValidateSet('gui', 'console')]
    [string]$HeaderType,

    [string]$IconPath
  )

  $IconElement = ''
  if ($IconPath -and (Test-Path -LiteralPath $IconPath -PathType Leaf)) {
    $IconElement = "  <icon>$(ConvertTo-XmlText $IconPath)</icon>`r`n"
  }

  $VersionParts = $InstallerVersion.Split('.')
  $MajorVersion = [int]$VersionParts[0]
  $MinorVersion = [int]$VersionParts[1]
  $PatchVersion = [int]$VersionParts[2]
  $FileVersion = "$MajorVersion.$MinorVersion.$PatchVersion.$BuildNumber"

  $Config = @"
<launch4jConfig>
  <dontWrapJar>false</dontWrapJar>
  <headerType>$(ConvertTo-XmlText $HeaderType)</headerType>
  <jar>$(ConvertTo-XmlText $JarPath)</jar>
  <classPath>
    <mainClass>$(ConvertTo-XmlText $MainClass)</mainClass>
  </classPath>
  <outfile>$(ConvertTo-XmlText $OutputExe)</outfile>
  <errTitle>$(ConvertTo-XmlText $AppName)</errTitle>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>https://adoptium.net/</downloadUrl>
  <supportUrl>https://github.com/dicklieber/fdswarm</supportUrl>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
$IconElement  <jre>
    <path>runtime</path>
    <requiresJdk>false</requiresJdk>
    <requires64Bit>false</requires64Bit>
    <minVersion>21</minVersion>
  </jre>
  <versionInfo>
    <fileVersion>$(ConvertTo-XmlText $FileVersion)</fileVersion>
    <txtFileVersion>$(ConvertTo-XmlText $ArtifactVersion)</txtFileVersion>
    <fileDescription>$(ConvertTo-XmlText $AppName)</fileDescription>
    <copyright>$(ConvertTo-XmlText $Vendor)</copyright>
    <productVersion>$(ConvertTo-XmlText $FileVersion)</productVersion>
    <txtProductVersion>$(ConvertTo-XmlText $ArtifactVersion)</txtProductVersion>
    <productName>$(ConvertTo-XmlText $AppName)</productName>
    <companyName>$(ConvertTo-XmlText $Vendor)</companyName>
    <internalName>$(ConvertTo-XmlText $AppName)</internalName>
    <originalFilename>$(ConvertTo-XmlText (Split-Path -Leaf $OutputExe))</originalFilename>
  </versionInfo>
</launch4jConfig>
"@

  Set-Content -LiteralPath $ConfigPath -Value $Config -Encoding UTF8
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
$AssemblyJar = (Resolve-Path -LiteralPath $AssemblyJar).Path

$MainClassEntry = "$($MainClass.Replace('.', '/')).class"
if (-not (& jar tf $AssemblyJar | Where-Object { $_ -eq $MainClassEntry })) {
  throw "Assembly JAR does not contain main class $MainClass ($MainClassEntry): $AssemblyJar"
}

$Launch4j = Resolve-Launch4jCommand

$ArtifactVersion = $InstallerVersion
if ($BuildNumber -ne '0') {
  $ArtifactVersion = "$InstallerVersion-build$BuildNumber"
}

New-Item -ItemType Directory -Force -Path $WorkDir, $ArtifactsDir | Out-Null

Write-Host 'Building Launch4j installers'
Write-Host "Repo: $RepoDir"
Write-Host "JAR: $AssemblyJar"
Write-Host "Version: $InstallerVersion"
Write-Host "Build: $BuildNumber"
Write-Host "Launch4j: $Launch4j"

foreach ($RuntimeImage in $RuntimeImages) {
  $RuntimeId = $RuntimeImage.Id
  $RuntimePath = $RuntimeImage.Path
  $RuntimeWorkDir = Join-Path $WorkDir $RuntimeId
  $StageRoot = Join-Path $RuntimeWorkDir 'stage'
  $StageDir = Join-Path $StageRoot $AppName
  $RuntimeDest = Join-Path $StageDir 'runtime'
  $GuiExe = Join-Path $StageDir "$AppName.exe"
  $ConsoleExe = Join-Path $StageDir "$($AppName)Console.exe"
  $GuiConfig = Join-Path $RuntimeWorkDir "$AppName.xml"
  $ConsoleConfig = Join-Path $RuntimeWorkDir "$($AppName)Console.xml"
  $ZipPath = Join-Path $RuntimeWorkDir "$AppName-$ArtifactVersion-$RuntimeId-launch4j.zip"

  if (-not (Test-Path -LiteralPath $RuntimePath -PathType Container)) {
    throw "Runtime image not found: $RuntimePath"
  }

  Remove-Item -LiteralPath $StageRoot, $GuiConfig, $ConsoleConfig, $ZipPath -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
  Copy-Item -LiteralPath $RuntimePath -Destination $RuntimeDest -Recurse -Force

  $IconPath = Join-Path $RepoDir 'fdswarm/resources/icons/icon.ico'
  $IconArg = if (Test-Path -LiteralPath $IconPath -PathType Leaf) { $IconPath } else { $null }

  Write-Launch4jConfig -ConfigPath $GuiConfig -OutputExe $GuiExe -JarPath $AssemblyJar -HeaderType 'gui' -IconPath $IconArg
  Write-Launch4jConfig -ConfigPath $ConsoleConfig -OutputExe $ConsoleExe -JarPath $AssemblyJar -HeaderType 'console' -IconPath $IconArg

  Write-Host ''
  Write-Host "Packaging $RuntimeId from $RuntimePath"
  & $Launch4j $GuiConfig
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }

  & $Launch4j $ConsoleConfig
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }

  Compress-Archive -LiteralPath $StageDir -DestinationPath $ZipPath -Force

  $Artifact = Join-Path $ArtifactsDir "$AppName-$ArtifactVersion-$RuntimeId-launch4j.zip"
  Move-Item -LiteralPath $ZipPath -Destination $Artifact -Force
  Write-Host "Launch4j: $Artifact"
}
