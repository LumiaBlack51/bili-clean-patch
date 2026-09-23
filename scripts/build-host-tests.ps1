$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$bt = Join-Path $sdk 'build-tools/35.0.0'
$android = Join-Path $sdk 'platforms/android-35/android.jar'
$out = Join-Path $root 'build/host-tests'
New-Item -ItemType Directory -Force "$out/classes", "$out/dex" | Out-Null
$mapping = Get-Content "$root/upstream/integrations/app/build/outputs/mapping/release/mapping.txt" -Raw
function MappingBlock([string]$name) {
    # R8 metadata comments may start at column zero; stop only at the next class header.
    $match = [regex]::Match($mapping,'(?ms)^'+[regex]::Escape($name)+' -> (?<name>[^:]+):\r?\n(?<body>.*?)(?=^[^ #\r\n][^\r\n]* -> [^:]+:|\z)')
    if (!$match.Success) { throw "Missing mapping for $name" }
    return $match
}
$settingsMap = MappingBlock 'app.revanced.bilibili.settings.Settings'
$settingMap = MappingBlock 'app.revanced.bilibili.settings.Setting'
$ads = [regex]::Match($settingsMap.Groups['body'].Value,'BooleanSetting CleanAds -> (\w+)').Groups[1].Value
$value = [regex]::Match($settingMap.Groups['body'].Value,'java.lang.Object value -> (\w+)').Groups[1].Value
if (!$ads -or !$value) { throw 'Missing settings field mappings' }
@"
package app.biliclean.tests;
final class GeneratedNames {
static final String SETTINGS = "$($settingsMap.Groups['name'].Value)";
static final String SETTING = "$($settingMap.Groups['name'].Value)";
static final String ADS = "$ads";
static final String VALUE = "$value";
}
"@ | Set-Content "$out/GeneratedNames.java"
& javac -encoding UTF-8 -source 8 -target 8 -cp $android -d "$out/classes" "$root/tests/android/HostModelTest.java" "$out/GeneratedNames.java"
if ($LASTEXITCODE) { throw 'Test compilation failed' }
& jar cf "$out/classes.jar" -C "$out/classes" .
& "$bt/d8.bat" --lib $android --min-api 24 --output "$out/dex" "$out/classes.jar"
if ($LASTEXITCODE) { throw 'Test DEX failed' }
& "$bt/aapt2.exe" link -o "$out/tests-unsigned.apk" --manifest "$root/tests/android/AndroidManifest.xml" -I $android
if ($LASTEXITCODE) { throw 'Test manifest failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$out/tests-unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$out/dex/classes.dex",'classes.dex') | Out-Null } finally { $zip.Dispose() }
& "$bt/zipalign.exe" -f 4 "$out/tests-unsigned.apk" "$out/host-tests.apk"
& java -cp "$bt/lib/apksigner.jar;$root/local/revanced-cli.jar" com.android.apksigner.ApkSignerTool sign --ks "$root/local/avd-test.keystore" --ks-type BKS --ks-pass pass: --key-pass pass: --ks-key-alias 'ReVanced Key' --provider-class org.bouncycastle.jce.provider.BouncyCastleProvider "$out/host-tests.apk"
if ($LASTEXITCODE) { throw 'Test signing failed' }
Write-Output 'Built separate AVD-only controlled-fixture instrumentation. It is not included in the delivered client.'
