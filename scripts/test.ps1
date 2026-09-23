$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'build/core-test'
New-Item -ItemType Directory -Force $out | Out-Null
& javac -encoding UTF-8 -d $out (Join-Path $root 'src/app/revanced/bilibili/clean/SkipEngine.java') (Join-Path $root 'tests/SkipEngineTest.java')
if ($LASTEXITCODE) { throw 'javac failed' }
& java -cp $out SkipEngineTest
if ($LASTEXITCODE) { throw 'Core tests failed' }
