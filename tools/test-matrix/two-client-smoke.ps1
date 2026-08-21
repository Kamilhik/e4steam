[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ProfileId,

    [string]$ReleaseRoot = "",

    [string]$OutputDirectory = "",

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ReleaseRoot)) {
    $ReleaseRoot = Join-Path $PSScriptRoot "..\..\release\0.3.0"
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot "results"
}

function Read-SmokeResult {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    while ($true) {
        $answer = (Read-Host "$Prompt [y=passed / n=failed / s=skipped]").Trim().ToLowerInvariant()
        switch ($answer) {
            "y" { return "passed" }
            "yes" { return "passed" }
            "n" { return "failed" }
            "no" { return "failed" }
            "s" { return "skipped" }
            "skip" { return "skipped" }
            default { Write-Host "Enter y, n or s." -ForegroundColor Yellow }
        }
    }
}

$profilesPath = Join-Path $PSScriptRoot "profiles.json"
if (-not (Test-Path -LiteralPath $profilesPath -PathType Leaf)) {
    throw "Missing profile manifest: $profilesPath"
}

$manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $profilesPath | ConvertFrom-Json
$matches = @($manifest.profiles | Where-Object { $_.id -eq $ProfileId })
if ($matches.Count -ne 1) {
    $known = @($manifest.profiles | ForEach-Object { $_.id }) -join ", "
    throw "Unknown or ambiguous profile '$ProfileId'. Known profile IDs: $known"
}
$profile = $matches[0]

$releaseRootPath = [System.IO.Path]::GetFullPath($ReleaseRoot)
$artifactPath = Join-Path $releaseRootPath $profile.artifact
if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
    throw "Missing release artifact for ${ProfileId}: $artifactPath"
}
$artifactHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash.ToLowerInvariant()

$checks = @(
    [pscustomobject]@{ id = "clean_profiles"; prompt = "Both PCs use the selected clean profile and the same JAR SHA-256" },
    [pscustomobject]@{ id = "distinct_accounts"; prompt = "Steam is running under two different signed-in accounts" },
    [pscustomobject]@{ id = "host_open"; prompt = "Host opened a single-player world through e4steam and received a Steam address" },
    [pscustomobject]@{ id = "invite_join"; prompt = "Guest received/accepted the invitation or joined with the generated address" },
    [pscustomobject]@{ id = "world_ready"; prompt = "Guest finished loading terrain and chunks without timeout or endless falling" },
    [pscustomobject]@{ id = "identity_unique"; prompt = "Host and guest have different Minecraft names and UUIDs" },
    [pscustomobject]@{ id = "gameplay"; prompt = "Movement, block interaction and chat work in both directions" },
    [pscustomobject]@{ id = "reconnect"; prompt = "Guest disconnected and reconnected successfully on the first retry" },
    [pscustomobject]@{ id = "shutdown"; prompt = "Closing the world stopped sharing and cleared Spacewar presence" }
)

Write-Host "e4steam two-client Steam smoke" -ForegroundColor Cyan
Write-Host "Profile : $($profile.name)"
Write-Host "Loader  : $($profile.loader) $($profile.loaderVersion)"
Write-Host "Artifact: $($profile.artifact)"
Write-Host "SHA-256 : $artifactHash"
Write-Host ""
Write-Host "Never paste a Steam ticket, join address, token, cookie or SteamID into this record." -ForegroundColor Yellow

if ($DryRun) {
    Write-Host ""
    Write-Host "Dry run: artifact and profile metadata are valid. Checklist:" -ForegroundColor Green
    $checks | ForEach-Object { Write-Host " - $($_.prompt)" }
    return
}

$startedAt = [DateTime]::UtcNow
$recordedChecks = @()
foreach ($check in $checks) {
    $status = Read-SmokeResult -Prompt $check.prompt
    $recordedChecks += [pscustomobject]@{
        id = $check.id
        status = $status
    }
}

$statuses = @($recordedChecks | ForEach-Object { $_.status })
$overall = "passed"
if ($statuses -contains "failed") {
    $overall = "failed"
} elseif ($statuses -contains "skipped") {
    $overall = "incomplete"
}

$record = [ordered]@{
    schemaVersion = 1
    modVersion = $manifest.modVersion
    profileId = $profile.id
    minecraft = $profile.minecraft
    loader = $profile.loader
    loaderVersion = $profile.loaderVersion
    java = $profile.java
    artifact = $profile.artifact
    artifactSha256 = $artifactHash
    startedAtUtc = $startedAt.ToString("o")
    finishedAtUtc = [DateTime]::UtcNow.ToString("o")
    overall = $overall
    checks = $recordedChecks
}

[System.IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$safeTimestamp = [DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss")
$outputPath = Join-Path $OutputDirectory "$safeTimestamp-$ProfileId.json"
$json = $record | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($outputPath),
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host ""
Write-Host "Result: $overall" -ForegroundColor $(if ($overall -eq "passed") { "Green" } elseif ($overall -eq "failed") { "Red" } else { "Yellow" })
Write-Host "Saved sanitized record: $outputPath"
