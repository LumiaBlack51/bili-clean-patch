$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'bootstrap.ps1')
& (Join-Path $PSScriptRoot 'test.ps1')
Push-Location (Join-Path $root 'upstream')
try {
    & ./gradlew.bat dist --no-daemon --console=plain --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8'
    if ($LASTEXITCODE) { throw 'Build failed; do not publish APK' }
} finally { Pop-Location }
