param(
    [switch]$SkipInstall
)

# Continue instead of Stop: pip/PyInstaller write progress to stderr, which
# PowerShell would otherwise surface as a terminating NativeCommandError.
# Real failures are still detected via $LASTEXITCODE checks below.
$ErrorActionPreference = "Continue"

$clientRoot = $PSScriptRoot
$venvRoot = Join-Path $clientRoot ".venv"
$venvPython = Join-Path $venvRoot "Scripts\python.exe"

if (-not (Test-Path -LiteralPath $venvPython)) {
    $systemPython = Get-Command python -ErrorAction SilentlyContinue
    if ($null -eq $systemPython) {
        throw "Python is required only on the build machine. Install Python 3, then rerun this script."
    }
    & $systemPython.Source -m venv $venvRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create the build virtual environment."
    }
}

if (-not $SkipInstall) {
    & $venvPython -m pip install --requirement (Join-Path $clientRoot "requirements-build.txt")
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install desktop client build dependencies."
    }
}

Push-Location $clientRoot
try {
    & $venvPython -m PyInstaller --noconfirm --clean "WorkLens.spec"
    if ($LASTEXITCODE -ne 0) {
        throw "PyInstaller build failed."
    }
}
finally {
    Pop-Location
}

$executable = Join-Path $clientRoot "dist\WorkLens\WorkLens.exe"
if (-not (Test-Path -LiteralPath $executable)) {
    throw "Build completed without producing the expected executable: $executable"
}

Copy-Item `
    -LiteralPath (Join-Path $clientRoot "config.prod.ini") `
    -Destination (Join-Path $clientRoot "dist\WorkLens\config.ini") `
    -Force

$archive = Join-Path $clientRoot "dist\WorkLens-windows-x64.zip"
Compress-Archive `
    -LiteralPath (Join-Path $clientRoot "dist\WorkLens") `
    -DestinationPath $archive `
    -CompressionLevel Optimal `
    -Force

Write-Host "Built WorkLens desktop client: $executable"
Write-Host "Built release archive: $archive"
