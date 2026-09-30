# Developer helper only; players install one matching JAR from Releases.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat build '-PallVersions=true' --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw 'Version matrix build failed' }
    & node tests/reader.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Player reader checks failed' }
    & python tests/artifacts.test.py
    if ($LASTEXITCODE -ne 0) { throw 'Artifact checks failed' }
} finally {
    Pop-Location
}
