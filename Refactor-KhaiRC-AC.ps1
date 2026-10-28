# Refactor-KhaiRC-AC.ps1
# Relocates Daikin/Toshiba source & unit tests while preserving original logic.
# Run from project root or specify -ProjectRoot <path>.
# -Preview prints planned moves and makes no changes.
param(
    [string]$ProjectRoot = $PSScriptRoot,
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd([char]'\', [char]'/')

$basePackage = 'com.khaiit.kremote.feature.ac'
$mainRel = 'feature/ac/src/main/java/com/khaiit/kremote/feature/ac'
$testRel = 'feature/ac/src/test/java/com/khaiit/kremote/feature/ac'
$mainPath = Join-Path $ProjectRoot $mainRel
$testPath = Join-Path $ProjectRoot $testRel
$appMain = Join-Path $ProjectRoot 'app/src/main/java/com/khaiit/kremote/MainActivity.kt'
$appHome = Join-Path $ProjectRoot 'app/src/main/java/com/khaiit/kremote/HomeScreen.kt'

if (-not (Test-Path -LiteralPath $mainPath -PathType Container)) {
    throw "Cannot find AC source folder: $mainPath"
}
foreach ($appFile in @($appMain, $appHome)) {
    if (-not (Test-Path -LiteralPath $appFile -PathType Leaf)) {
        throw "Cannot find app file: $appFile"
    }
}

$brands = @(
    @{ prefix = 'Daikin'; folder = 'daikin' },
    @{ prefix = 'Toshiba'; folder = 'toshiba' }
)
$roots = @($mainPath, $testPath)
$moves = New-Object System.Collections.ArrayList
foreach ($srcRoot in $roots) {
    if (-not (Test-Path -LiteralPath $srcRoot -PathType Container)) { continue }
    foreach ($brand in $brands) {
        $fromFiles = @(Get-ChildItem -LiteralPath $srcRoot -File -Filter ($brand.prefix + '*.kt'))
        $toDir = Join-Path $srcRoot $brand.folder
        foreach ($file in $fromFiles) {
            $dest = Join-Path $toDir $file.Name
            if (Test-Path -LiteralPath $dest) {
                throw "Destination already exists; refusing to overwrite: $dest"
            }
            [void]$moves.Add(@{ Source = $file.FullName; Destination = $dest; Brand = $brand.folder })
        }
    }
}

foreach ($brand in $brands) {
    $sourceCount = @($moves | Where-Object { $_.Brand -eq $brand.folder -and $_.Source.StartsWith($mainPath) }).Count
    $alreadyMoved = @(Get-ChildItem -Path (Join-Path $mainPath $brand.folder) -Filter '*.kt' -File -ErrorAction SilentlyContinue).Count
    if ($sourceCount -eq 0 -and $alreadyMoved -eq 0) {
        throw "Missing $($brand.prefix) source files. Make sure the Toshiba integration is installed before running this script."
    }
}

Write-Host "Project: $ProjectRoot"
Write-Host "Files to move: $($moves.Count)"
foreach ($move in $moves) {
    Write-Host "MOVE $($move.Source.Substring($ProjectRoot.Length + 1)) -> $($move.Destination.Substring($ProjectRoot.Length + 1))"
}
if ($Preview) {
    Write-Host 'Preview only; no files changed.'
    exit 0
}

# Back up Kotlin sources that could be affected, outside the Git working tree.
$sourceRoots = @('app/src', 'feature/ac/src', 'feature/fan/src', 'feature/wol/src', 'core/ir/src', 'core/network/src')
$kotlinFiles = New-Object System.Collections.ArrayList
foreach ($rel in $sourceRoots) {
    $path = Join-Path $ProjectRoot $rel
    if (Test-Path -LiteralPath $path -PathType Container) {
        foreach ($f in (Get-ChildItem -LiteralPath $path -Recurse -File -Filter '*.kt' | Where-Object { $_.FullName -notmatch '[\\/](build|\\.gradle)[\\/]' })) {
            [void]$kotlinFiles.Add($f.FullName)
        }
    }
}
$parent = Split-Path -Parent $ProjectRoot
$backup = Join-Path $parent ('KhaiRC_AC_backup_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
foreach ($path in $kotlinFiles) {
    $rel = $path.Substring($ProjectRoot.Length).TrimStart([char]'\', [char]'/')
    $target = Join-Path $backup $rel
    $targetDir = Split-Path -Parent $target
    [void](New-Item -ItemType Directory -Path $targetDir -Force)
    Copy-Item -LiteralPath $path -Destination $target
}
Write-Host "Backup: $backup"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
foreach ($move in $moves) {
    $targetDir = Split-Path -Parent $move.Destination
    [void](New-Item -ItemType Directory -Path $targetDir -Force)
    Move-Item -LiteralPath $move.Source -Destination $move.Destination
}

# Update package declarations of brand files in their destination folders.
foreach ($srcRoot in $roots) {
    foreach ($brand in $brands) {
        $dir = Join-Path $srcRoot $brand.folder
        if (-not (Test-Path -LiteralPath $dir -PathType Container)) { continue }
        foreach ($f in (Get-ChildItem -LiteralPath $dir -File -Filter '*.kt')) {
            $before = [System.IO.File]::ReadAllText($f.FullName)
            $targetPackage = "$basePackage.$($brand.folder)"
            $after = [regex]::Replace(
                $before,
                '(?m)^([ \t]*package[ \t]+)com\.khaiit\.kremote\.feature\.ac[ \t]*$',
                ('$1' + $targetPackage)
            )
            if ($after -ne $before) {
                [System.IO.File]::WriteAllText($f.FullName, $after, $utf8NoBom)
            }
            if ($after -notmatch ('(?m)^\s*package\s+' + [regex]::Escape($targetPackage) + '[ \t]*$')) {
                throw "Unexpected package declaration in $($f.FullName)"
            }
        }
    }
}

# Update all fully qualified Daikin/Toshiba references in application & feature sources.
$changedImports = 0
foreach ($rel in $sourceRoots) {
    $path = Join-Path $ProjectRoot $rel
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { continue }
    foreach ($file in (Get-ChildItem -LiteralPath $path -Recurse -File -Filter '*.kt' | Where-Object { $_.FullName -notmatch '[\\/](build|\\.gradle)[\\/]' })) {
        $old = [System.IO.File]::ReadAllText($file.FullName)
        $new = $old
        $new = [regex]::Replace($new, 'com\.khaiit\.kremote\.feature\.ac\.(Daikin[A-Za-z0-9_]*)', 'com.khaiit.kremote.feature.ac.daikin.$1')
        $new = [regex]::Replace($new, 'com\.khaiit\.kremote\.feature\.ac\.(Toshiba[A-Za-z0-9_]*)', 'com.khaiit.kremote.feature.ac.toshiba.$1')
        if ($new -cne $old) {
            [System.IO.File]::WriteAllText($file.FullName, $new, $utf8NoBom)
            $changedImports++
        }
    }
}

$badImports = @()
foreach ($rel in $sourceRoots) {
    $path = Join-Path $ProjectRoot $rel
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { continue }
    $badImports += @(Get-ChildItem -LiteralPath $path -Recurse -File -Filter '*.kt' |
        Select-String -Pattern 'com\.khaiit\.kremote\.feature\.ac\.(Daikin|Toshiba)')
}
if ($badImports.Count -gt 0) {
    throw 'Found stale Daikin/Toshiba fully qualified references. Inspect the files and backup.'
}
Write-Host "Refactor complete. Kotlin files with import/reference updates: $changedImports"
Write-Host 'Run: .\gradlew.bat :feature:ac:testDebugUnitTest'
Write-Host 'Then build the app in Android Studio.'
