$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$local = Join-Path $root 'local'
New-Item -ItemType Directory -Force $local | Out-Null
$cli = Join-Path $local 'revanced-cli.jar'
if (!(Test-Path $cli)) {
    & gh release download v4.6.0.2 --repo zjns/revanced-cli --pattern revanced-cli.jar --dir $local
    if ($LASTEXITCODE) { throw 'CLI download failed' }
}
$expected = 'f2e396c9e631334098daf10e2c31525158180a4887ee99d37192110ffad171a0'
if ((Get-FileHash $cli -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'CLI checksum mismatch' }
& (Join-Path $PSScriptRoot 'prepare.ps1')
