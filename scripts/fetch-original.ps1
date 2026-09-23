$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$local = Join-Path $root 'local'
New-Item -ItemType Directory -Force $local | Out-Null
$target = Join-Path $local 'official.apk'
$expected = 'b9c62efed1452c21a070919a9d937428ab6b7308a4d9e066d282392f50333f31'
if (!(Test-Path $target)) {
    $download = Join-Path $local 'official-download.apk'
    Invoke-WebRequest -Uri 'https://dl.hdslb.com/mobile/latest/android64/iBiliPlayer-bili.apk' -OutFile $download -TimeoutSec 900
    if ((Get-FileHash $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
        throw 'Official latest has changed. Download retained locally for inspection; do not patch it as 9.12.0.'
    }
    Move-Item -LiteralPath $download -Destination $target
}
if ((Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Existing official.apk does not match the inspected original' }
Write-Output 'Verified original 9.12.0 input SHA-256.'
