#Requires -Version 7.0
[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$Installer,
  [Parameter(Mandatory)][string]$AssemblyJar
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows -or -not $env:GITHUB_ACTIONS) {
  throw 'Run installer verification only on a disposable Windows GitHub Actions runner.'
}
$Installer = (Resolve-Path -LiteralPath $Installer).Path
$AssemblyJar = (Resolve-Path -LiteralPath $AssemblyJar).Path
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$logs = Join-Path $repo 'out/release/windows-installer-test'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$installDir = Join-Path $env:RUNNER_TEMP 'FdSwarm installer test'
$dataDir = Join-Path $env:LOCALAPPDATA 'FdSwarm'
$sentinel = Join-Path $dataDir 'installer-test-sentinel.txt'
$shortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'FdSwarm/FdSwarm.lnk'
if ((Test-Path -LiteralPath $installDir) -or (Test-Path -LiteralPath $dataDir) -or
    (Test-Path -LiteralPath $shortcut)) {
  throw 'Installer test requires a clean runner without an existing FdSwarm installation or data.'
}
New-Item -ItemType Directory -Path $dataDir | Out-Null
Set-Content -LiteralPath $sentinel -Value 'Keep contest data' -NoNewline

function Assert-ContestData {
  if (-not (Test-Path -LiteralPath $sentinel) -or
      (Get-Content -LiteralPath $sentinel -Raw) -cne 'Keep contest data') {
    throw 'Installation or uninstallation changed user data.'
  }
}

try {
  # Reinstall the same package to exercise the stable AppId and replacement path.
  foreach ($pass in @('install', 'reinstall')) {
    $log = Join-Path $logs "$pass.log"
    $process = Start-Process -FilePath $Installer -ArgumentList @(
      '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-',
      "/DIR=`"$installDir`"", "/LOG=`"$log`""
    ) -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "$pass failed with exit code $($process.ExitCode)" }
    $installedJar = Join-Path $installDir 'lib/fdswarm.jar'
    if ((Get-FileHash -LiteralPath $installedJar).Hash -ne (Get-FileHash -LiteralPath $AssemblyJar).Hash) {
      throw 'Installed JAR differs from the published release JAR.'
    }
    Assert-ContestData
  }
  $java = Join-Path $installDir 'runtime/bin/java.exe'
  & $java -version
  if ($LASTEXITCODE -ne 0) { throw 'Installed Java cannot start.' }
  $modules = @(& $java --list-modules)
  if ($LASTEXITCODE -ne 0 -or -not ($modules -match '^javafx.web@')) {
    throw 'Installed runtime is missing JavaFX WebView.'
  }
  if (-not (Test-Path -LiteralPath $shortcut)) { throw 'Start menu shortcut is missing.' }
  $shell = New-Object -ComObject WScript.Shell
  $link = $shell.CreateShortcut($shortcut)
  if ($link.TargetPath -ine (Join-Path $installDir 'runtime/bin/javaw.exe') -or
      $link.Arguments -cne "-jar `"$(Join-Path $installDir 'lib/fdswarm.jar')`"") {
    throw 'Start menu shortcut does not launch the installed JAR with its bundled Java.'
  }
  Write-Host 'Installer and reinstall verification passed.'
} finally {
  $uninstaller = Join-Path $installDir 'unins000.exe'
  if (Test-Path -LiteralPath $uninstaller) {
    $log = Join-Path $logs 'uninstall.log'
    $process = Start-Process -FilePath $uninstaller -ArgumentList @(
      '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', "/LOG=`"$log`""
    ) -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Uninstall failed with exit code $($process.ExitCode)" }
    if ((Test-Path -LiteralPath (Join-Path $installDir 'lib/fdswarm.jar')) -or
        (Test-Path -LiteralPath $shortcut)) {
      throw 'Uninstall left the application JAR or shortcut installed.'
    }
  }
  Assert-ContestData
  Remove-Item -LiteralPath $sentinel
  Remove-Item -LiteralPath $dataDir
}
