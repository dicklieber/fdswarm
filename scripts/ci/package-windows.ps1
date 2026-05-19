$ErrorActionPreference = 'Stop'

$assemblyJar = if ($env:FDSWARM_ASSEMBLY_JAR) {
  $env:FDSWARM_ASSEMBLY_JAR
} else {
  'out/fdswarm/assembly.dest/fdswarm.jar'
}

Write-Host 'Packaging Windows MSI'
Write-Host "PWD: $(Get-Location)"
Write-Host "FDSWARM_VERSION: $env:FDSWARM_VERSION"
Write-Host "FDSWARM_BUILD: $env:FDSWARM_BUILD"
Write-Host "FDSWARM_ASSEMBLY_JAR: $assemblyJar"

if (-not (Test-Path -LiteralPath $assemblyJar)) {
  throw "Assembly JAR not found: $assemblyJar"
}

Get-Item -LiteralPath $assemblyJar | Format-List FullName,Length,LastWriteTime

$env:FDSWARM_ASSEMBLY_JAR = $assemblyJar
& .\mill.bat --no-daemon fdswarm.winMsi
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}

Get-ChildItem -Path out/fdswarm/winMsi.dest -Filter '*.msi' -Recurse | Select-Object -ExpandProperty FullName
