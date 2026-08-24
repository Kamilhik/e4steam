[CmdletBinding()]
param(
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher"),
    [string]$ManifestPath = "",
    [string]$ReleaseRoot = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $PSScriptRoot "profiles.json"
}

if (-not (Test-Path -LiteralPath $PrismRoot -PathType Container)) {
    throw "Prism Launcher root does not exist: $PrismRoot"
}
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    throw "Profile manifest does not exist: $ManifestPath"
}
if (-not [string]::IsNullOrWhiteSpace($ReleaseRoot) -and
        -not (Test-Path -LiteralPath $ReleaseRoot -PathType Container)) {
    throw "Release root does not exist: $ReleaseRoot"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([int]$manifest.schemaVersion -ne 1) {
    throw "Unsupported profile manifest schema: $($manifest.schemaVersion)"
}

$instancesRoot = Join-Path $PrismRoot "instances"
$failures = [Collections.Generic.List[string]]::new()
$expectedIds = @($manifest.profiles | ForEach-Object { [string]$_.id })

foreach ($profile in $manifest.profiles) {
    $id = [string]$profile.id
    $instanceRoot = Join-Path $instancesRoot $id
    $modsRoot = Join-Path $instanceRoot "minecraft\mods"
    if (-not (Test-Path -LiteralPath $instanceRoot -PathType Container)) {
        $failures.Add("missing profile: $id")
        continue
    }

    $markerPath = Join-Path $instanceRoot ".e4steam-test-profile.json"
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        $failures.Add("missing managed marker: $id")
        continue
    }
    $marker = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([string]$marker.profileSet -ne [string]$manifest.profileSet) {
        $failures.Add("wrong profile set marker: $id")
    }
    if ([string]$marker.artifact -ne [string]$profile.artifact) {
        $failures.Add("wrong artifact marker: $id")
    }

    $artifactPath = Join-Path $modsRoot ([string]$profile.artifact)
    if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        $failures.Add("missing e4steam artifact: $id")
    }
    else {
        $actualArtifactHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualArtifactHash -ne ([string]$marker.artifactSha256).ToLowerInvariant()) {
            $failures.Add("artifact hash differs from marker: $id")
        }
        if (-not [string]::IsNullOrWhiteSpace($ReleaseRoot)) {
            $releaseArtifact = Join-Path $ReleaseRoot ([string]$profile.artifact)
            if (-not (Test-Path -LiteralPath $releaseArtifact -PathType Leaf)) {
                $failures.Add("release artifact is missing: $id")
            }
            elseif ($actualArtifactHash -ne
                    (Get-FileHash -LiteralPath $releaseArtifact -Algorithm SHA256).Hash.ToLowerInvariant()) {
                $failures.Add("artifact hash differs from release: $id")
            }
        }
    }

    $e4steamJars = @(Get-ChildItem -LiteralPath $modsRoot -Filter "e4steam-*.jar" -File)
    if ($e4steamJars.Count -ne 1) {
        $failures.Add("expected one e4steam JAR, found $($e4steamJars.Count): $id")
    }

    $hasFabricApi = $profile.PSObject.Properties.Name -contains "fabricApi" -and
            $null -ne $profile.fabricApi
    $fabricApiJars = @(Get-ChildItem -LiteralPath $modsRoot -Filter "fabric-api-*.jar" -File)
    $expectedFabricApiCount = if ($hasFabricApi) { 1 } else { 0 }
    if ($fabricApiJars.Count -ne $expectedFabricApiCount) {
        $failures.Add("wrong Fabric API count ($($fabricApiJars.Count)): $id")
    }
    elseif ($hasFabricApi) {
        if ($fabricApiJars[0].Name -ne [string]$profile.fabricApi.filename) {
            $failures.Add("wrong Fabric API file: $id")
        }
        $actualApiHash = (Get-FileHash -LiteralPath $fabricApiJars[0].FullName -Algorithm SHA512).Hash.ToLowerInvariant()
        if ($actualApiHash -ne ([string]$profile.fabricApi.sha512).ToLowerInvariant()) {
            $failures.Add("Fabric API hash mismatch: $id")
        }
    }

    $packPath = Join-Path $instanceRoot "mmc-pack.json"
    if (-not (Test-Path -LiteralPath $packPath -PathType Leaf)) {
        $failures.Add("missing mmc-pack.json: $id")
        continue
    }
    $pack = Get-Content -LiteralPath $packPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $packBytes = [IO.File]::ReadAllBytes($packPath)
    if ($packBytes.Length -ge 3 -and $packBytes[0] -eq 0xEF -and
            $packBytes[1] -eq 0xBB -and $packBytes[2] -eq 0xBF) {
        $failures.Add("mmc-pack.json has a UTF-8 BOM: $id")
    }
    $minecraft = @($pack.components | Where-Object uid -eq "net.minecraft")
    $loader = @($pack.components | Where-Object uid -eq ([string]$profile.loaderUid))
    if ($minecraft.Count -ne 1 -or [string]$minecraft[0].version -ne [string]$profile.minecraft) {
        $failures.Add("wrong Minecraft component: $id")
    }
    if ($loader.Count -ne 1 -or [string]$loader[0].version -ne [string]$profile.loaderVersion) {
        $failures.Add("wrong loader component: $id")
    }
    elseif (-not ($loader[0].cachedRequires -is [Array])) {
        $failures.Add("loader cachedRequires must be a JSON array: $id")
    }
}

$managedProfiles = @(Get-ChildItem -LiteralPath $instancesRoot -Directory |
    Where-Object Name -Like "e4steam-030-test-*")
$unexpectedProfiles = @($managedProfiles | Where-Object Name -NotIn $expectedIds)
foreach ($profile in $unexpectedProfiles) {
    $failures.Add("unexpected managed profile: $($profile.Name)")
}

$result = [ordered]@{
    profileSet = [string]$manifest.profileSet
    expected = @($manifest.profiles).Count
    managed = $managedProfiles.Count
    verified = @($manifest.profiles).Count - $failures.Count
    failureCount = $failures.Count
    failures = @($failures)
}
$result | ConvertTo-Json -Depth 4 -Compress

if ($failures.Count -ne 0) {
    throw "Prism profile verification failed with $($failures.Count) issue(s)"
}
