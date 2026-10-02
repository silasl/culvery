# Makes Culvery's release signing key, one step at a time (4c design D4).
# You type every password into keytool itself: none is passed on a command line, printed, or written to the repo.
$ErrorActionPreference = 'Stop'

$keytool = if ($env:KEYTOOL) { $env:KEYTOOL } else { 'keytool' }
if (-not (Get-Command $keytool -ErrorAction SilentlyContinue)) {
    Write-Host "keytool isn't on PATH. Use the JDK 17 that builds Culvery, e.g.:"
    Write-Host '  $env:KEYTOOL = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"; .\tools\new-release-key.ps1'
    exit 1
}
$alias = 'culvery'
$defaultDir = Join-Path $HOME '.culvery'
$repo = $null
try { $repo = (git -C $PSScriptRoot rev-parse --show-toplevel 2>$null) } catch { $repo = $null }
if ($LASTEXITCODE -ne 0 -or -not $repo) {
    Write-Host "Can't find the Culvery repo from this script's folder, so can't check the key stays outside it."
    Write-Host 'Run the script from inside a Culvery checkout, with git on PATH.'
    exit 1
}

Write-Host 'Culvery release key'
Write-Host ''
Write-Host 'Step 1 of 4: where the key lives.'
Write-Host 'It must be outside the repo, and backed up somewhere safe: if it is lost, Culvery has to be uninstalled from the'
Write-Host 'tablet (losing its setup) and Google needs a new OAuth client.'
$dir = Read-Host "Folder [$defaultDir]"
if (-not $dir) { $dir = $defaultDir }
New-Item -ItemType Directory -Force $dir | Out-Null
$dir = (Resolve-Path $dir).Path
if (($dir.Replace('\', '/').TrimEnd('/') + '/').StartsWith(($repo.TrimEnd('/') + '/'), [StringComparison]::OrdinalIgnoreCase)) {
    Write-Host 'That folder is inside the repo. Choose one outside it.'
    exit 1
}
$store = Join-Path $dir 'culvery-release.jks'
if (Test-Path $store) {
    Write-Host "$store already exists; it is left as it is. Move it away first to make a new key."
    exit 1
}

Write-Host ''
Write-Host 'Step 2 of 4: keytool asks for a keystore password (twice), then a name and organisation (anything, e.g.'
Write-Host '"Culvery"), then asks you to confirm. Choose a long password and keep it with the backup.'
& $keytool -genkeypair -v -keystore $store -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 -validity 10000
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host "Step 3 of 4: add these four lines to $HOME\.gradle\gradle.properties (your own Gradle file, never the repo's),"
Write-Host 'putting the password you just chose in place of <password> on both password lines (a PKCS12 key uses the'
Write-Host "keystore's password):"
Write-Host ''
Write-Host ("culvery.release.storeFile=" + $store.Replace('\', '/'))
Write-Host 'culvery.release.storePassword=<password>'
Write-Host "culvery.release.keyAlias=$alias"
Write-Host 'culvery.release.keyPassword=<password>'
Write-Host ''
Read-Host 'Press Enter once they are saved' | Out-Null

Write-Host ''
Write-Host 'Step 4 of 4: the key''s SHA-1, for the release OAuth client (docs/setup/google-calendar.md, section 4).'
Write-Host "Run this from the repo root and copy the SHA1 line under 'Variant: release':"
Write-Host ''
Write-Host '  .\gradlew.bat :app:signingReport'
Write-Host ''
Write-Host "Then back up $store and its password."
