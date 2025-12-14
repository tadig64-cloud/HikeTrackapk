# HikeTrack - Italian strings hardfix (v3)
# Fixes "Invalid unicode escape sequence" in values-it for specific keys,
# and prevents duplicate resources by removing those keys from strings_settings_prefs_hotfix.xml.
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\tools_translate\fix_it_strings_hardfix_v3.ps1

$ErrorActionPreference = "Stop"

function Get-ProjectRootPath {
    # Script lives in <project>\tools_translate\ ; project root is parent folder
    $root = Resolve-Path (Join-Path $PSScriptRoot "..")
    return $root.Path
}

function New-BackupFolder([string]$projectRoot) {
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $backupRoot = Join-Path $projectRoot ("tools_translate\_it_hardfix_backup\" + $stamp)
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    return $backupRoot
}

function Backup-File([string]$projectRoot, [string]$backupRoot, [string]$absPath) {
    if (-not (Test-Path -LiteralPath $absPath)) { return }
    $rel = $absPath.Substring($projectRoot.Length).TrimStart('\','/')
    $dest = Join-Path $backupRoot $rel
    $destDir = Split-Path -Parent $dest
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    Copy-Item -LiteralPath $absPath -Destination $dest -Force
}

function Load-XmlPreserve([string]$path) {
    $xml = New-Object System.Xml.XmlDocument
    $xml.PreserveWhitespace = $true
    $xml.Load($path)
    return $xml
}

function Save-XmlUtf8NoBom([xml]$xmlDoc, [string]$path) {
    $encNoBom = New-Object System.Text.UTF8Encoding($false)
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Encoding = $encNoBom
    $settings.Indent = $false
    $settings.NewLineChars = "`n"
    $settings.OmitXmlDeclaration = $false
    $writer = [System.Xml.XmlWriter]::Create($path, $settings)
    $xmlDoc.Save($writer)
    $writer.Close()
}

function Ensure-String([xml]$xmlDoc, [string]$name, [string]$value) {
    $resources = $xmlDoc.SelectSingleNode("/resources")
    if ($null -eq $resources) { throw "No <resources> root" }

    $node = $xmlDoc.SelectSingleNode("/resources/string[@name='$name']")
    if ($null -eq $node) {
        $node = $xmlDoc.CreateElement("string")
        $attr = $xmlDoc.CreateAttribute("name")
        $attr.Value = $name
        [void]$node.Attributes.Append($attr)
        [void]$resources.AppendChild($node)
    }
    # Use InnerText so XML escapes happen correctly (and no invalid \u escapes).
    $node.InnerText = $value
}

function Remove-String([xml]$xmlDoc, [string]$name) {
    $nodes = $xmlDoc.SelectNodes("/resources/string[@name='$name']")
    if ($nodes -ne $null) {
        foreach ($n in @($nodes)) {
            [void]$n.ParentNode.RemoveChild($n)
        }
    }
}

function Remove-Duplicate-Strings([xml]$xmlDoc) {
    # If same <string name="x"> appears multiple times in the same file, keep the first and remove others.
    $seen = @{}
    $nodes = $xmlDoc.SelectNodes("/resources/string[@name]")
    foreach ($n in @($nodes)) {
        $k = $n.Attributes["name"].Value
        if ($seen.ContainsKey($k)) {
            [void]$n.ParentNode.RemoveChild($n)
        } else {
            $seen[$k] = $true
        }
    }
}

# ---- Main ----
$projectRoot = Get-ProjectRootPath
$backupRoot = New-BackupFolder $projectRoot

$itStringsPath = Join-Path $projectRoot "app\src\main\res\values-it\strings.xml"
$itSafetyPath  = Join-Path $projectRoot "app\src\main\res\values-it\strings_safety.xml"
$itHotfixPath  = Join-Path $projectRoot "app\src\main\res\values-it\strings_settings_prefs_hotfix.xml"

# Backup
Backup-File $projectRoot $backupRoot $itStringsPath
Backup-File $projectRoot $backupRoot $itSafetyPath
Backup-File $projectRoot $backupRoot $itHotfixPath

# Patch strings.xml (Italian)
if (Test-Path -LiteralPath $itStringsPath) {
    $xml = Load-XmlPreserve $itStringsPath

    Ensure-String $xml "pref_app_language_title"   "Lingua dell'app"
    Ensure-String $xml "pref_app_language_summary" "Scegli la lingua dell'interfaccia."
    Ensure-String $xml "pref_keep_screen_on_title" "Mantieni lo schermo acceso"
    Ensure-String $xml "pref_keep_screen_on_summary" "Impedisce allo schermo di spegnersi durante l'uso."

    Remove-Duplicate-Strings $xml
    Save-XmlUtf8NoBom $xml $itStringsPath

    Write-Host "PATCHED: app\src\main\res\values-it\strings.xml"
} else {
    Write-Host "SKIP (missing): app\src\main\res\values-it\strings.xml"
}

# Patch strings_safety.xml (Italian)
if (Test-Path -LiteralPath $itSafetyPath) {
    $xml = Load-XmlPreserve $itSafetyPath

    Ensure-String $xml "ht_safety_storm_during" "Durante un temporale: evita creste, alberi isolati e l'acqua. Spostati in un luogo più basso e sicuro."
    Ensure-String $xml "ht_safety_nav_body"     "Navigazione: pianifica il percorso, porta una mappa offline e controlla i punti di riferimento. Se ti perdi, fermati e rivaluta."

    Remove-Duplicate-Strings $xml
    Save-XmlUtf8NoBom $xml $itSafetyPath

    Write-Host "PATCHED: app\src\main\res\values-it\strings_safety.xml"
} else {
    Write-Host "SKIP (missing): app\src\main\res\values-it\strings_safety.xml"
}

# Remove duplicates & move keys out of hotfix file (Italian)
if (Test-Path -LiteralPath $itHotfixPath) {
    $xml = Load-XmlPreserve $itHotfixPath

    # These must live in strings.xml to avoid duplicates & to keep one source of truth.
    Remove-String $xml "pref_app_language_title"
    Remove-String $xml "pref_app_language_summary"
    Remove-String $xml "pref_keep_screen_on_title"
    Remove-String $xml "pref_keep_screen_on_summary"

    Remove-Duplicate-Strings $xml
    Save-XmlUtf8NoBom $xml $itHotfixPath

    Write-Host "PATCHED: app\src\main\res\values-it\strings_settings_prefs_hotfix.xml"
} else {
    Write-Host "SKIP (missing): app\src\main\res\values-it\strings_settings_prefs_hotfix.xml"
}

Write-Host ""
Write-Host "✅ Done."
Write-Host ("Backup saved here: " + $backupRoot)
Write-Host ""
Write-Host "Next step: run a clean build:"
Write-Host "  .\gradlew clean assembleDebug"
