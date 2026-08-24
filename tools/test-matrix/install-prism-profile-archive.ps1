[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ArchivePath,
    [Parameter(Mandatory = $true)][string]$ExpectedSha256,
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher"),
    [string]$ManifestPath = "",
    [switch]$Replace
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $PSScriptRoot "profiles.json"
}

function Assert-ChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Child
    )

    $parentPath = [IO.Path]::GetFullPath($Parent).TrimEnd('\', '/') +
            [IO.Path]::DirectorySeparatorChar
    $childPath = [IO.Path]::GetFullPath($Child)
    if (-not $childPath.StartsWith($parentPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside $parentPath`: $childPath"
    }
}

if (-not (Test-Path -LiteralPath $PrismRoot -PathType Container)) {
    throw "Prism Launcher root does not exist: $PrismRoot"
}
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "Profile manifest does not exist: $ManifestPath"
}
if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
    throw "Profile archive does not exist: $ArchivePath"
}

$expectedHash = $ExpectedSha256.Trim().ToLowerInvariant()
if ($expectedHash -notmatch "^[0-9a-f]{64}$") {
    throw "ExpectedSha256 must be a 64-character hexadecimal SHA-256"
}
$actualHash = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $expectedHash) {
    throw "Profile archive SHA-256 mismatch"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([int]$manifest.schemaVersion -ne 1) {
    throw "Unsupported profile manifest schema: $($manifest.schemaVersion)"
}
$expectedIds = @($manifest.profiles | ForEach-Object { [string]$_.id })
if ($expectedIds.Count -eq 0 -or @($expectedIds | Sort-Object -Unique).Count -ne $expectedIds.Count) {
    throw "Profile manifest IDs are empty or duplicated"
}

$instancesRoot = Join-Path $PrismRoot "instances"
$stageRoot = Join-Path $env:TEMP ("e4steam-prism-stage-" + [Guid]::NewGuid().ToString("N"))
Assert-ChildPath -Parent $env:TEMP -Child $stageRoot
New-Item -ItemType Directory -Path $instancesRoot -Force | Out-Null
New-Item -ItemType Directory -Path $stageRoot | Out-Null

$backupRoot = $null
try {
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $stageRoot
    $stagedProfiles = @(Get-ChildItem -LiteralPath $stageRoot -Directory)
    $stagedIds = @($stagedProfiles.Name)
    $missing = @($expectedIds | Where-Object { $_ -notin $stagedIds })
    $unexpected = @($stagedIds | Where-Object { $_ -notin $expectedIds })
    if ($missing.Count -ne 0 -or $unexpected.Count -ne 0) {
        throw "Profile archive contents differ from manifest; missing=$missing unexpected=$unexpected"
    }

    $existing = [Collections.Generic.List[string]]::new()
    foreach ($id in $expectedIds) {
        $source = Join-Path $stageRoot $id
        $destination = Join-Path $instancesRoot $id
        Assert-ChildPath -Parent $stageRoot -Child $source
        Assert-ChildPath -Parent $instancesRoot -Child $destination

        $sourceMarker = Join-Path $source ".e4steam-test-profile.json"
        if (-not (Test-Path -LiteralPath $sourceMarker -PathType Leaf)) {
            throw "Staged profile is not managed by e4steam: $id"
        }
        $sourceMarkerData = Get-Content -LiteralPath $sourceMarker -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([string]$sourceMarkerData.profileSet -ne [string]$manifest.profileSet) {
            throw "Staged profile belongs to another profile set: $id"
        }

        if (Test-Path -LiteralPath $destination) {
            $destinationMarker = Join-Path $destination ".e4steam-test-profile.json"
            if (-not (Test-Path -LiteralPath $destinationMarker -PathType Leaf)) {
                throw "Refusing to replace a non-managed Prism instance: $destination"
            }
            if (-not $Replace) {
                throw "Managed Prism instance already exists; pass -Replace: $destination"
            }
            $existing.Add($id)
        }
    }

    if ($existing.Count -ne 0) {
        $backupRoot = Join-Path $PrismRoot (
            "e4steam-test-profile-backups\" + [DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss"))
        Assert-ChildPath -Parent $PrismRoot -Child $backupRoot
        New-Item -ItemType Directory -Path $backupRoot | Out-Null
        foreach ($id in $existing) {
            Move-Item -LiteralPath (Join-Path $instancesRoot $id) -Destination (Join-Path $backupRoot $id)
        }
    }

    foreach ($id in $expectedIds) {
        Move-Item -LiteralPath (Join-Path $stageRoot $id) -Destination (Join-Path $instancesRoot $id)
    }

    [ordered]@{
        installed = $expectedIds.Count
        replaced = $existing.Count
        backup = if ($null -ne $backupRoot) { $backupRoot } else { "" }
        archiveSha256 = $actualHash
    } | ConvertTo-Json -Compress
}
finally {
    if (Test-Path -LiteralPath $stageRoot) {
        Assert-ChildPath -Parent $env:TEMP -Child $stageRoot
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}
