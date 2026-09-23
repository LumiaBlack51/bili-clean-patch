param([Parameter(Mandatory)][string]$InputApk, [Parameter(Mandatory)][string]$OutputApk)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$local = Join-Path $root 'local'
$inputPath = (Resolve-Path $InputApk).Path
$outputPath = [IO.Path]::GetFullPath($OutputApk)
if ($inputPath -eq $outputPath) { throw 'Use a distinct output path' }
$unsigned = Join-Path $local 'native-compat-unsigned.apk'
Copy-Item -LiteralPath $inputPath -Destination $unsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entry = $zip.GetEntry('lib/arm64-v8a/libbili.so')
    if (!$entry) { throw 'Only the inspected arm64 9.12.0 library is supported' }
    $stream = $entry.Open()
    $memory = [IO.MemoryStream]::new()
    try { $stream.CopyTo($memory); $bytes = $memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = [Convert]::ToHexString($sha.ComputeHash($bytes)).ToLowerInvariant() } finally { $sha.Dispose() }
    if ($hash -ne '8e89db7d5a78e06ea97873734521e75ea80c0c925890953ba400bc397a79bf18') { throw 'Native input hash mismatch; refuse offset patch' }
    if ([BitConverter]::ToString($bytes,0x8da0,12) -ne 'FF-83-00-D1-FD-7B-01-A9-FD-43-00-91') { throw 'Native callback prologue mismatch' }
    # This callback only returns, or delays 5–14 seconds then exits on signature mismatch.
    # Return at its entry; do not hook global libc exit or alter request signing / playback.
    [byte[]]$ret = @(0xc0,0x03,0x5f,0xd6)
    [Array]::Copy($ret,0,$bytes,0x8da0,4)
    $entry.Delete()
    $newEntry = $zip.CreateEntry('lib/arm64-v8a/libbili.so',[IO.Compression.CompressionLevel]::NoCompression)
    $write = $newEntry.Open()
    try { $write.Write($bytes,0,$bytes.Length) } finally { $write.Dispose() }
    # The global exit hook is unnecessary with the specific callback disabled.
    $legacy = $zip.GetEntry('lib/arm64-v8a/libbiliroamingx.so')
    if ($legacy) { $legacy.Delete() }
} finally { $zip.Dispose() }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$buildTools = Join-Path $sdk 'build-tools/35.0.0'
& "$buildTools/zipalign.exe" -f -P 16 4 $unsigned $outputPath
if ($LASTEXITCODE) { throw 'Alignment failed' }
$classpath = "$buildTools/lib/apksigner.jar;$local/revanced-cli.jar"
& java -cp $classpath com.android.apksigner.ApkSignerTool sign --ks "$local/avd-test.keystore" --ks-type BKS --ks-pass pass: --key-pass pass: --ks-key-alias 'ReVanced Key' --provider-class org.bouncycastle.jce.provider.BouncyCastleProvider $outputPath
if ($LASTEXITCODE) { throw 'Signing failed' }
& "$buildTools/apksigner.bat" verify --verbose $outputPath
if ($LASTEXITCODE) { throw 'Signature verification failed' }
Get-FileHash $outputPath -Algorithm SHA256
