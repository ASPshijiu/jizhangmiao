param(
    [string]$Message = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$branch = (git branch --show-current).Trim()
if ([string]::IsNullOrWhiteSpace($branch)) {
    throw "Unable to detect current branch."
}

if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = "chore: auto publish $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
}

Write-Host "Running local verification and packaging..."
& .\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed."
}

Write-Host "Collecting Git changes..."
git add -A -- ":!/.vscode/settings.json"
if ($LASTEXITCODE -ne 0) {
    throw "Git add failed."
}

git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating commit..."
    git commit -m $Message
    if ($LASTEXITCODE -ne 0) {
        throw "Git commit failed."
    }
} else {
    Write-Host "No staged changes detected. Skipping commit."
}

Write-Host "Pushing to GitHub branch $branch ..."
git push origin $branch
if ($LASTEXITCODE -ne 0) {
    throw "Git push failed."
}

Write-Host "Push completed. GitHub Actions will build and publish a new prerelease."
