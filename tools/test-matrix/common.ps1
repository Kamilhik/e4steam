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

function New-TestProfileBackupRoot {
    param([Parameter(Mandatory = $true)][string]$PrismRoot)

    $backupRoot = Join-Path $PrismRoot (
        "e4steam-test-profile-backups\" + [DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss"))
    Assert-ChildPath -Parent $PrismRoot -Child $backupRoot
    return $backupRoot
}