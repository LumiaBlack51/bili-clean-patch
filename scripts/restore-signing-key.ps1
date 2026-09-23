param([Parameter(Mandatory)][string]$BackupPath, [Parameter(Mandatory)][string]$RecoveryKeyPath, [Parameter(Mandatory)][string]$OutputKeystore)
$ErrorActionPreference = 'Stop'
if (Test-Path $OutputKeystore) { throw 'Refusing to overwrite an existing keystore' }
$data = Get-Content -LiteralPath $BackupPath -Raw | ConvertFrom-Json
if ($data.format -ne 'bili-clean-patch signing backup v1' -or $data.algorithm -ne 'AES-256-GCM') { throw 'Unknown backup format' }
$key = [IO.File]::ReadAllBytes((Resolve-Path $RecoveryKeyPath))
$cipher = [Convert]::FromBase64String($data.ciphertext)
$plain = [byte[]]::new($cipher.Length)
$aes = [Security.Cryptography.AesGcm]::new($key,16)
try {
    $aes.Decrypt([Convert]::FromBase64String($data.nonce),$cipher,[Convert]::FromBase64String($data.tag),$plain,[Text.Encoding]::UTF8.GetBytes($data.format))
    [IO.File]::WriteAllBytes([IO.Path]::GetFullPath($OutputKeystore),$plain)
} finally { $aes.Dispose(); [Array]::Clear($plain); [Array]::Clear($key) }
Write-Output 'Authenticated keystore backup restored.'
