$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$local = Join-Path $root 'local'
$apk = Join-Path $local 'official.apk'
$expected = 'b9c62efed1452c21a070919a9d937428ab6b7308a4d9e066d282392f50333f31'
if ((Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Only the inspected official 9.12.0 APK is allowed for this compatibility experiment'
}
$cli = Join-Path $local 'revanced-cli.jar'
if ((Get-FileHash $cli -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'f2e396c9e631334098daf10e2c31525158180a4887ee99d37192110ffad171a0') { throw 'CLI hash mismatch' }
$bundle = Join-Path $root 'upstream/build/BiliRoamingX-patches-1.23.3.jar'
$integrations = Join-Path $root 'upstream/build/BiliRoamingX-integrations-1.23.3.apk'
$params = @('-Xmx3072m','-jar',$cli,'patch','--exclusive','--merge',$integrations,'--patch-bundle',$bundle,
    '--out',(Join-Path $local 'candidate.apk'),'--keystore',(Join-Path $local 'avd-test.keystore'),
    '--temporary-files-path',(Join-Path $local 'patch-work'),'--signing-levels','1,2,3')
# Explicit selections keep unrelated enhancements outside this experiment.
foreach ($name in @('Integrations','Lib bili','Bili library patch','Main activity patch','Modify modifier',
    'Unlock ProtoBuf','Clean metadata','Json','Clean feed','BiliRoamingX settings entrance',
    'Fix preference manager','Clean player','Block up recommend ads')) {
    $params += @('--include',$name)
}
$params += $apk
$log = Join-Path $local 'patch-output.log'
& java @params 2>&1 | Tee-Object $log
$code = $LASTEXITCODE
if ($code -or (Select-String -Path $log -Pattern 'failed:|Invalid register|Exception' -Quiet)) {
    throw 'Patching failed: candidate is not approved for installation'
}
Write-Output 'Experimental candidate only. Runtime acceptance tests are still required.'
