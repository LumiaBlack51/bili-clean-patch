param([Parameter(Mandatory)][string]$Name, [string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This script only permits emulator serials' }
if ($Name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Invalid evidence name' }
$root = Split-Path $PSScriptRoot -Parent
$remote = "/sdcard/$Name.png"
& adb -s $Serial shell screencap -p $remote
if ($LASTEXITCODE) { throw 'Capture failed' }
& adb -s $Serial pull $remote (Join-Path $root "evidence/$Name.png")
if ($LASTEXITCODE) { throw 'Pull failed' }
[ordered]@{capturedAt=(Get-Date -Format o);serial=$Serial;name=$Name;description='Actual emulator screenshot, not a simulated UI'} |
    ConvertTo-Json | Set-Content (Join-Path $root "evidence/$Name.metadata.json")
