param(
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath
)
$ErrorActionPreference = 'Stop'
$repository = 'yakirp88/YPNuvioTV'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install GitHub CLI from https://cli.github.com/ and run gh auth login first.'
}
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw 'Pass the downloaded original Nuvio-YP-debug-signing.keystore file.'
}
$encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeystorePath)))
try {
    # Send directly through stdin. Never print the key, save a public copy or commit it.
    $encoded | gh secret set YP_DEBUG_KEYSTORE_BASE64 --repo $repository
    if ($LASTEXITCODE -ne 0) { throw 'GitHub rejected the secret update.' }
    Write-Host 'Signing secret saved. Push a source change to start the next build.'
} finally {
    $encoded = $null
}
