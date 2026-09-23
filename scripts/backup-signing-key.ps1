param([string]$Keystore, [string]$BackupPath, [string]$RecoveryKeyPath)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$Keystore) { $Keystore = Join-Path $root 'local/avd-test.keystore' }
if (!$BackupPath) { $BackupPath = Join-Path $root 'local/signing-key.encrypted.json' }
if (!$RecoveryKeyPath) { $RecoveryKeyPath = Join-Path $root 'local/signing-recovery-key.bin' }
if (!(Test-Path $RecoveryKeyPath)) {
    $recovery = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    [IO.File]::WriteAllBytes($RecoveryKeyPath, $recovery)
} else { $recovery = [IO.File]::ReadAllBytes($RecoveryKeyPath) }
if ($recovery.Length -ne 32) { throw 'Recovery key must be 32 bytes' }
$plain = [IO.File]::ReadAllBytes((Resolve-Path $Keystore))
$nonce = [Security.Cryptography.RandomNumberGenerator]::GetBytes(12)
$cipher = [byte[]]::new($plain.Length)
$tag = [byte[]]::new(16)
$aad = [Text.Encoding]::UTF8.GetBytes('bili-clean-patch signing backup v1')
$aes = [Security.Cryptography.AesGcm]::new($recovery,16)
try {
    $aes.Encrypt($nonce,$plain,$cipher,$tag,$aad)
    $verify = [byte[]]::new($plain.Length)
    $aes.Decrypt($nonce,$cipher,$tag,$verify,$aad)
    if (![System.Linq.Enumerable]::SequenceEqual[byte]($plain,$verify)) { throw 'Backup verification failed' }
} finally { $aes.Dispose() }
[ordered]@{
    format='bili-clean-patch signing backup v1'; algorithm='AES-256-GCM'; createdAt=(Get-Date -Format o)
    keystoreType='BKS'; alias='ReVanced Key'; keystorePassword=''; entryPassword=''
    nonce=[Convert]::ToBase64String($nonce); tag=[Convert]::ToBase64String($tag)
    ciphertext=[Convert]::ToBase64String($cipher)
} | ConvertTo-Json | Set-Content $BackupPath
[Array]::Clear($plain); [Array]::Clear($verify); [Array]::Clear($recovery)
Write-Output 'Encrypted backup verified. Keep the recovery key local and separately backed up; never upload it beside the encrypted file.'
