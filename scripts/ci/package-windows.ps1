#Requires -Version 7.0
[CmdletBinding()]
param(
  [string]$AssemblyJar = $env:FDSWARM_ASSEMBLY_JAR,
  [string]$RuntimeDirectory = $env:FDSWARM_RUNTIME_WINDOWS_X64,
  [string]$ExpectedVersion,
  [string]$Iscc = $env:ISCC
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw 'Windows installer packaging must run on Windows.' }
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $AssemblyJar) { $AssemblyJar = Join-Path $repo 'release/fdswarm.jar' }
$AssemblyJar = (Resolve-Path -LiteralPath $AssemblyJar).Path

if (-not $Iscc) {
  $command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
  if ($command) { $Iscc = $command.Source }
  else { $Iscc = Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6/ISCC.exe' }
}
if (-not (Test-Path -LiteralPath $Iscc -PathType Leaf)) {
  throw 'Install Inno Setup 6 or set ISCC to the full path of ISCC.exe.'
}

# Use Java's JAR reader for Mill's executable assemblies. .NET ZipArchive
# rejects the release JAR's central-directory metadata.
$jarTool = if ($env:JAVA_HOME_21_X64) {
  Join-Path $env:JAVA_HOME_21_X64 'bin/jar.exe'
} else {
  (Get-Command jar -ErrorAction Stop).Source
}
$entries = @(& $jarTool tf $AssemblyJar)
if ($LASTEXITCODE -ne 0) { throw 'Cannot list release JAR entries.' }
foreach ($entry in @('META-INF/MANIFEST.MF', 'fdswarm/FdSwarm.class', 'FDSwarmDocs/index.html')) {
  if ($entries -cnotcontains $entry) { throw "JAR is missing $entry" }
}

# version.txt is restored to SNAPSHOT after release; the JAR is authoritative.
$inspectionDir = Join-Path ([IO.Path]::GetTempPath()) "fdswarm-jar-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $inspectionDir | Out-Null
try {
  Push-Location -LiteralPath $inspectionDir
  try {
    & $jarTool xf $AssemblyJar META-INF/MANIFEST.MF FDSwarmDocs/index.html
    if ($LASTEXITCODE -ne 0) { throw 'Cannot extract release manifest and documentation.' }
  } finally { Pop-Location }
  $manifestText = (Get-Content -LiteralPath (Join-Path $inspectionDir 'META-INF/MANIFEST.MF') -Raw) -replace "\r?\n ", ''
  if ($manifestText -notmatch '(?m)^Implementation-Version: ([0-9]+\.[0-9]+\.[0-9]+(?:-[0-9]+)?)\r?$') {
    throw 'The JAR must contain a non-SNAPSHOT Implementation-Version (X.Y.Z or X.Y.Z-build).'
  }
  $version = $Matches[1]
  if ($ExpectedVersion -and $version -cne $ExpectedVersion) {
    throw "JAR version '$version' does not match requested release '$ExpectedVersion'."
  }
  if ((Get-Item -LiteralPath (Join-Path $inspectionDir 'FDSwarmDocs/index.html')).Length -eq 0) {
    throw 'JAR contains empty embedded documentation.'
  }
} finally { Remove-Item -LiteralPath $inspectionDir -Recurse -Force }

$fileVersion = $version.Replace('-', '.')
if ($fileVersion.Split('.').Count -eq 3) { $fileVersion += '.0' }
foreach ($part in $fileVersion.Split('.')) {
  if ([bigint]$part -gt 65535) { throw "Version component exceeds the Windows file-version limit: $version" }
}

$work = Join-Path $repo 'out/release/windows-inno'
$stage = Join-Path $work 'stage'
$output = Join-Path $repo 'out/release/artifacts'
New-Item -ItemType Directory -Force -Path $work, $output | Out-Null

if (-not $RuntimeDirectory) {
  # Keep JavaFX (including WebView) and all runtime modules; no jlink or WiX needed.
  $uri = 'https://api.bell-sw.com/v1/liberica/releases?version-modifier=latest&version-feature=21&bitness=64&os=windows&arch=x86&package-type=zip&bundle-type=jdk-full&output=json'
  $releases = @(Invoke-RestMethod -Uri $uri | ForEach-Object { $_ })
  if ($releases.Count -ne 1) { throw "Expected one Liberica JDK 21 Full release, found $($releases.Count)." }
  $runtime = $releases[0]
  if (-not $runtime.downloadUrl.StartsWith('https://')) { throw 'Runtime download must use HTTPS.' }
  Write-Host "Bundling Liberica JDK $($runtime.version) Full, Windows x64"
  $archive = Join-Path $work 'runtime.zip'
  Invoke-WebRequest -Uri $runtime.downloadUrl -OutFile $archive
  # BellSoft's discovery API supplies the archive SHA-1 digest.
  if ($runtime.sha1 -notmatch '^[0-9a-fA-F]{40}$' -or
      (Get-FileHash -LiteralPath $archive -Algorithm SHA1).Hash -ine $runtime.sha1) {
    throw 'Liberica runtime archive checksum does not match BellSoft metadata.'
  }
  $unpacked = Join-Path $work 'downloaded-runtime'
  if (Test-Path -LiteralPath $unpacked) { Remove-Item -LiteralPath $unpacked -Recurse -Force }
  Expand-Archive -LiteralPath $archive -DestinationPath $unpacked
  $candidates = @(Get-ChildItem -LiteralPath $unpacked -Filter java.exe -File -Recurse |
    Where-Object { $_.Directory.Name -eq 'bin' })
  if ($candidates.Count -ne 1) { throw 'Cannot identify the downloaded Java runtime.' }
  $RuntimeDirectory = $candidates[0].Directory.Parent.FullName
}
$RuntimeDirectory = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$java = Join-Path $RuntimeDirectory 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw "No bin/java.exe in $RuntimeDirectory" }
$settings = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $settings -notmatch 'os.arch\s*=\s*amd64' -or
    $settings -notmatch 'java.version\s*=\s*21\.') {
  throw 'Installer requires a working Windows x64 Java 21 runtime.'
}
$modules = @(& $java --list-modules)
if ($LASTEXITCODE -ne 0) { throw 'Cannot list runtime modules.' }
foreach ($module in @('javafx.controls', 'javafx.fxml', 'javafx.swing', 'javafx.web', 'jdk.httpserver')) {
  if (-not ($modules -match "^$([regex]::Escape($module))@")) { throw "Runtime is missing $module" }
}
if (-not (Test-Path -LiteralPath (Join-Path $RuntimeDirectory 'bin/javaw.exe'))) {
  throw 'Runtime is missing javaw.exe.'
}

try {
  if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
  New-Item -ItemType Directory -Force -Path (Join-Path $stage 'lib') | Out-Null
  Copy-Item -LiteralPath $AssemblyJar -Destination (Join-Path $stage 'lib/fdswarm.jar')
  Copy-Item -LiteralPath $RuntimeDirectory -Destination (Join-Path $stage 'runtime') -Recurse
  Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination $stage
  $installer = Join-Path $output "FdSwarm-$version-windows-x64-setup.exe"
  # A failed rebuild must not leave an older installer available for publication.
  if (Test-Path -LiteralPath $installer) { Remove-Item -LiteralPath $installer }
  & $Iscc "/DAppVersion=$version" "/DFileVersion=$fileVersion" "/DStageDir=$stage" "/O$output" (Join-Path $repo 'packaging/windows/fdswarm.iss')
  if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed with exit code $LASTEXITCODE" }
  if (-not (Test-Path -LiteralPath $installer)) { throw "Installer not found: $installer" }
  $hash = (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant()
  "$hash  $([IO.Path]::GetFileName($installer))" | Set-Content -LiteralPath "$installer.sha256" -Encoding ascii
  Write-Host "Installer: $installer"
} finally {
  if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
}
