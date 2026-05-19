$ErrorActionPreference = 'Stop'

$wixVersion = '3.14.1'
$wixDir = "$env:RUNNER_TEMP\wix-$wixVersion"
$wixZip = "$env:RUNNER_TEMP\wix314-binaries.zip"

Invoke-WebRequest `
  -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' `
  -OutFile $wixZip

New-Item -ItemType Directory -Force -Path $wixDir | Out-Null
Expand-Archive -Path $wixZip -DestinationPath $wixDir -Force

echo $wixDir | Out-File -FilePath $env:GITHUB_PATH -Encoding utf8 -Append

& "$wixDir\candle.exe" -?
& "$wixDir\light.exe" -?
