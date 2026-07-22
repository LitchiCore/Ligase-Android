[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $env:USERPROFILE '.artemis-touchkit')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$keytoolCommand = Get-Command keytool.exe -ErrorAction SilentlyContinue
if ($null -eq $keytoolCommand -and $env:JAVA_HOME) {
    $javaHomeKeytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
    if (Test-Path -LiteralPath $javaHomeKeytool) {
        $keytoolCommand = Get-Item -LiteralPath $javaHomeKeytool
    }
}
if ($null -eq $keytoolCommand) {
    throw 'keytool.exe was not found. Install a JDK or set JAVA_HOME, then retry.'
}

$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$keystorePath = Join-Path $resolvedOutputDirectory 'artemis-touchkit-release.p12'
$propertiesPath = Join-Path $resolvedOutputDirectory 'signing.properties'

if ((Test-Path -LiteralPath $keystorePath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw "Signing files already exist in $resolvedOutputDirectory. Refusing to overwrite them."
}

New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force | Out-Null

$passwordBytes = New-Object byte[] 32
$randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($passwordBytes)
}
finally {
    $randomNumberGenerator.Dispose()
}
$password = [BitConverter]::ToString($passwordBytes).Replace('-', '').ToLowerInvariant()
$alias = 'artemis-touchkit'

& $keytoolCommand.Source `
    -genkeypair `
    -noprompt `
    -keystore $keystorePath `
    -storetype PKCS12 `
    -storepass $password `
    -keypass $password `
    -alias $alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname 'CN=Artemis TouchKit, OU=LitchiCore, O=LitchiCore, C=CN'
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE"
}

$escapedKeystorePath = $keystorePath.Replace('\', '\\')
$properties = @(
    "storeFile=$escapedKeystorePath"
    "storePassword=$password"
    "keyAlias=$alias"
    "keyPassword=$password"
) -join [Environment]::NewLine
[System.IO.File]::WriteAllText(
    $propertiesPath,
    $properties + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

if ($env:OS -eq 'Windows_NT') {
    $currentIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $resolvedOutputDirectory '/inheritance:r' '/grant:r' "${currentIdentity}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to restrict access to $resolvedOutputDirectory"
    }
}

Write-Output 'TouchKit release signing files were created.'
Write-Output "Directory: $resolvedOutputDirectory"
Write-Output 'Back up the entire directory. Losing it prevents future APK upgrades.'
Write-Output 'The signing password was intentionally not printed.'
