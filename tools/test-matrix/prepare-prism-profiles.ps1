[CmdletBinding()]
param(
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher"),
    [string]$RepositoryRoot = "",
    [switch]$Replace
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}

function Assert-ChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Child
    )

    $parentPath = [IO.Path]::GetFullPath($Parent).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $childPath = [IO.Path]::GetFullPath($Child)
    if (-not $childPath.StartsWith($parentPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside $parentPath`: $childPath"
    }
}

function Get-VerifiedDependency {
    param(
        [Parameter(Mandatory = $true)]$Dependency,
        [Parameter(Mandatory = $true)][string]$CacheRoot
    )

    $destination = Join-Path $CacheRoot ([string]$Dependency.filename)
    Assert-ChildPath -Parent $CacheRoot -Child $destination

    if (Test-Path -LiteralPath $destination) {
        $actual = (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant()
        if ($actual -eq ([string]$Dependency.sha512).ToLowerInvariant()) {
            return $destination
        }

        $quarantine = "$destination.invalid.$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss'))"
        Move-Item -LiteralPath $destination -Destination $quarantine
    }

    $temporary = Join-Path $CacheRoot ("download-" + [IO.Path]::GetRandomFileName())
    Assert-ChildPath -Parent $CacheRoot -Child $temporary
    try {
        Invoke-WebRequest -UseBasicParsing -Uri ([string]$Dependency.url) -OutFile $temporary
        $actual = (Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash.ToLowerInvariant()
        $expected = ([string]$Dependency.sha512).ToLowerInvariant()
        if ($actual -ne $expected) {
            throw "SHA-512 mismatch for $($Dependency.filename): expected $expected, got $actual"
        }
        Move-Item -LiteralPath $temporary -Destination $destination
    }
    finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }

    return $destination
}

function New-ComponentList {
    param([Parameter(Mandatory = $true)]$Profile)

    $components = [Collections.Generic.List[object]]::new()
    $lwjglName = if ([string]$Profile.lwjglUid -eq "org.lwjgl") { "LWJGL" } else { "LWJGL 3" }
    $components.Add([ordered]@{
        cachedName = $lwjglName
        cachedVersion = [string]$Profile.lwjglVersion
        cachedVolatile = $true
        dependencyOnly = $true
        uid = [string]$Profile.lwjglUid
        version = [string]$Profile.lwjglVersion
    })
    $components.Add([ordered]@{
        cachedName = "Minecraft"
        cachedRequires = @([ordered]@{
            suggests = [string]$Profile.lwjglVersion
            uid = [string]$Profile.lwjglUid
        })
        cachedVersion = [string]$Profile.minecraft
        important = $true
        uid = "net.minecraft"
        version = [string]$Profile.minecraft
    })

    if ([string]$Profile.loader -in @("Fabric", "Quilt")) {
        $components.Add([ordered]@{
            cachedName = "Intermediary Mappings"
            cachedRequires = @([ordered]@{
                equals = [string]$Profile.minecraft
                uid = "net.minecraft"
            })
            cachedVersion = [string]$Profile.minecraft
            cachedVolatile = $true
            dependencyOnly = $true
            uid = "net.fabricmc.intermediary"
            version = [string]$Profile.minecraft
        })
    }

    $loaderName = switch ([string]$Profile.loader) {
        "Fabric" { "Fabric Loader" }
        "Quilt" { "Quilt Loader" }
        "NeoForge" { "NeoForge" }
        default { "Forge" }
    }
    $requiresUid = if ([string]$Profile.loader -in @("Fabric", "Quilt")) {
        "net.fabricmc.intermediary"
    }
    else {
        "net.minecraft"
    }
    $requires = if ([string]$Profile.loader -in @("Forge", "NeoForge")) {
        @([ordered]@{ equals = [string]$Profile.minecraft; uid = $requiresUid })
    }
    else {
        @([ordered]@{ uid = $requiresUid })
    }
    $components.Add([ordered]@{
        cachedName = $loaderName
        cachedRequires = $requires
        cachedVersion = [string]$Profile.loaderVersion
        uid = [string]$Profile.loaderUid
        version = [string]$Profile.loaderVersion
    })

    return @($components)
}

$manifestPath = Join-Path $PSScriptRoot "profiles.json"
$releaseRoot = Join-Path $RepositoryRoot "release\0.3.0"
$instancesRoot = Join-Path $PrismRoot "instances"
$dependencyCache = Join-Path $PrismRoot "e4steam-mod-store-0.3.0"

if (-not (Test-Path -LiteralPath $PrismRoot -PathType Container)) {
    throw "Prism Launcher root does not exist: $PrismRoot"
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Profile manifest is missing: $manifestPath"
}
if (-not (Test-Path -LiteralPath $releaseRoot -PathType Container)) {
    throw "Release directory is missing: $releaseRoot"
}

$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ([int]$manifest.schemaVersion -ne 1) {
    throw "Unsupported profile manifest schema: $($manifest.schemaVersion)"
}

New-Item -ItemType Directory -Path $instancesRoot -Force | Out-Null
New-Item -ItemType Directory -Path $dependencyCache -Force | Out-Null

$backupRoot = Join-Path $PrismRoot ("e4steam-test-profile-backups\" + [DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss"))
$created = [Collections.Generic.List[object]]::new()

foreach ($profile in $manifest.profiles) {
    $artifactSource = Join-Path $releaseRoot ([string]$profile.artifact)
    if (-not (Test-Path -LiteralPath $artifactSource -PathType Leaf)) {
        throw "Required e4steam artifact is missing: $artifactSource"
    }

    $instanceRoot = Join-Path $instancesRoot ([string]$profile.id)
    Assert-ChildPath -Parent $instancesRoot -Child $instanceRoot
    if (Test-Path -LiteralPath $instanceRoot) {
        $marker = Join-Path $instanceRoot ".e4steam-test-profile.json"
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
            throw "Refusing to replace a non-managed Prism instance: $instanceRoot"
        }
        if (-not $Replace) {
            Write-Host "Already prepared: $($profile.id)"
            continue
        }

        New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
        $backupDestination = Join-Path $backupRoot ([string]$profile.id)
        Assert-ChildPath -Parent $backupRoot -Child $backupDestination
        Move-Item -LiteralPath $instanceRoot -Destination $backupDestination
    }

    $modsRoot = Join-Path $instanceRoot ".minecraft\mods"
    New-Item -ItemType Directory -Path $modsRoot -Force | Out-Null

    $pack = [ordered]@{
        components = New-ComponentList -Profile $profile
        formatVersion = 1
    }
    $pack | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $instanceRoot "mmc-pack.json") -Encoding UTF8

    $cfg = @(
        "[General]",
        "ConfigVersion=1.3",
        "InstanceType=OneSix",
        "iconKey=default",
        "name=$($profile.name)",
        "AutomaticJava=true",
        "OverrideJavaLocation=false",
        "IgnoreJavaCompatibility=false",
        "JoinServerOnLaunch=false",
        "LogPrePostOutput=true",
        "ManagedPack=false",
        "OverrideCommands=false",
        "OverrideConsole=false",
        "OverrideEnv=false",
        "OverrideJavaArgs=false",
        "OverrideMemory=false",
        "OverrideWindow=false",
        "UseAccountForInstance=false",
        "notes=e4steam 0.3.0 clean test profile; $($profile.artifact); representative Minecraft $($profile.minecraft) for $($profile.family)",
        "totalTimePlayed=0"
    )
    $cfg | Set-Content -LiteralPath (Join-Path $instanceRoot "instance.cfg") -Encoding UTF8

    Copy-Item -LiteralPath $artifactSource -Destination (Join-Path $modsRoot ([string]$profile.artifact))
    $hasFabricApi = $profile.PSObject.Properties.Name -contains "fabricApi"
    if ($hasFabricApi -and $null -ne $profile.fabricApi) {
        $dependency = Get-VerifiedDependency -Dependency $profile.fabricApi -CacheRoot $dependencyCache
        Copy-Item -LiteralPath $dependency -Destination (Join-Path $modsRoot ([string]$profile.fabricApi.filename))
    }

    $markerData = [ordered]@{
        schemaVersion = 1
        profileSet = [string]$manifest.profileSet
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        minecraft = [string]$profile.minecraft
        loader = [string]$profile.loader
        loaderVersion = [string]$profile.loaderVersion
        artifact = [string]$profile.artifact
        artifactSha256 = (Get-FileHash -LiteralPath $artifactSource -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $markerData | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $instanceRoot ".e4steam-test-profile.json") -Encoding UTF8

    $created.Add([pscustomobject]@{
        Instance = [string]$profile.id
        Minecraft = [string]$profile.minecraft
        Loader = [string]$profile.loader
        Artifact = [string]$profile.artifact
        FabricApi = if ($hasFabricApi -and $null -ne $profile.fabricApi) { [string]$profile.fabricApi.filename } else { "-" }
    })
}

$created | Format-Table -AutoSize
Write-Host "Prepared $($created.Count) of $($manifest.profiles.Count) clean Prism test profiles in $instancesRoot"
if (Test-Path -LiteralPath $backupRoot) {
    Write-Host "Previous managed profiles were backed up to $backupRoot"
}
