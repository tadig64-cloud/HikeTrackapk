# fix_it_unicode_sanitize_v5.ps1
# HikeTrack — sanitize invalid \u unicode escape sequences in values-it string resources
# - Converts any \uXXXX / \\uXXXX / \u{...} / \\u{...} / \UXXXXXXXX into XML numeric entities (&#x....;)
# - Removes any leftover "\u" sequences by stripping the backslashes (so AAPT2 won't choke)
# - Forces safe fallback values for a few known-problem keys
# - Removes duplicate keys from strings_settings_prefs_hotfix.xml if present
#
# Run from project root:
#   powershell -ExecutionPolicy Bypass -File .\tools_translate\fix_it_unicode_sanitize_v5.ps1
#
$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    # script is expected in <root>\tools_translate\
    return (Split-Path -Parent $PSScriptRoot)
}

function New-Utf8NoBomEncoding {
    return New-Object System.Text.UTF8Encoding($false)
}

function XmlEscape([string]$s) {
    if ($null -eq $s) { return "" }
    $s = $s -replace '&', '&amp;'
    $s = $s -replace '<', '&lt;'
    $s = $s -replace '>', '&gt;'
    return $s
}

function Ensure-StringValue([string]$xml, [string]$name, [string]$value) {
    $escaped = XmlEscape $value
    $pattern = "(?s)<string\s+name=`"$([Regex]::Escape($name))`"([^>]*)>.*?</string>"
    if ($xml -match $pattern) {
        return ([Regex]::Replace($xml, $pattern, "<string name=`"$name`"$1>$escaped</string>"))
    } else {
        # insert before </resources>
        $insert = "    <string name=`"$name`">$escaped</string>`r`n"
        if ($xml -match "(?s)</resources>") {
            return ([Regex]::Replace($xml, "(?s)</resources>", $insert + "</resources>"))
        } else {
            return $xml
        }
    }
}

function Remove-StringByName([string]$xml, [string]$name) {
    $pattern = "(?s)\s*<string\s+name=`"$([Regex]::Escape($name))`"[^>]*>.*?</string>\s*"
    return ([Regex]::Replace($xml, $pattern, "`r`n"))
}

$root = Get-ProjectRoot
$resDir = Join-Path $root "app\src\main\res\values-it"

if (-not (Test-Path $resDir)) {
    Write-Host "❌ values-it folder not found: $resDir"
    exit 1
}

$backupDir = Join-Path $PSScriptRoot ("_it_unicode_v5_backup\" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$utf8NoBom = New-Utf8NoBomEncoding

# Known-problem keys we will force to safe values (ASCII-only to avoid any encoding surprise)
$forced = @{
    "pref_app_language_title"      = "Lingua dell'app"
    "pref_app_language_summary"    = "Scegli la lingua dell'interfaccia."
    "pref_keep_screen_on_title"    = "Schermo sempre acceso"
    "pref_keep_screen_on_summary"  = "Evita che lo schermo si spenga durante l'uso."
}

$forcedSafety = @{
    "ht_safety_storm_during" = "Durante un temporale: allontanati da creste, alberi isolati e punti alti. Scendi in una zona riparata."
    "ht_safety_nav_body"     = "Navigazione: prepara la traccia, controlla cartina e bussola, usa il GPS con prudenza e avvisa un contatto del percorso."
}

$files = Get-ChildItem $resDir -Filter "*.xml" -File

foreach ($f in $files) {
    # backup raw bytes
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    [System.IO.File]::WriteAllBytes((Join-Path $backupDir $f.Name), $bytes)

    # read with BOM detection
    $txt = [System.IO.File]::ReadAllText($f.FullName)

    $before = $txt

    # 1) Convert any \u{...} or \\u{...} etc (one or more backslashes) into XML numeric entity
    $txt = $txt -replace '\\+u\{([0-9A-Fa-f]{1,6})\}', '&#x$1;'

    # 2) Convert any \uXXXX (with one or more backslashes) into XML numeric entity
    $txt = $txt -replace '\\+u([0-9A-Fa-f]{4})', '&#x$1;'

    # 3) Convert any \UXXXXXXXX (with one or more backslashes) into XML numeric entity
    $txt = $txt -replace '\\+U([0-9A-Fa-f]{8})', '&#x$1;'

    # 4) Kill any leftover \u or \U sequences that are invalid (strip backslashes so AAPT won't parse them)
    $txt = $txt -replace '\\+u', 'u'
    $txt = $txt -replace '\\+U', 'U'

    # 5) Force safe values for known crashing keys (in any file they might appear)
    foreach ($k in $forced.Keys) {
        $txt = Ensure-StringValue $txt $k $forced[$k]
    }

    if ($f.Name -eq "strings_safety.xml") {
        foreach ($k in $forcedSafety.Keys) {
            $txt = Ensure-StringValue $txt $k $forcedSafety[$k]
        }
    }

    # 6) If this is the hotfix file, remove duplicates so they only exist once (strings.xml is enough)
    if ($f.Name -ieq "strings_settings_prefs_hotfix.xml") {
        foreach ($k in $forced.Keys) {
            $txt = Remove-StringByName $txt $k
        }
        # if empty, delete the file (prevents future duplicates/conflicts)
        $minimal = ($txt -replace '\s+', '')
        if ($minimal -eq '<resources></resources>') {
            Remove-Item $f.FullName -Force
            Write-Host "REMOVED (empty): $($f.FullName)"
            continue
        }
    }

    if ($txt -ne $before) {
        [System.IO.File]::WriteAllText($f.FullName, $txt, $utf8NoBom)
        Write-Host "PATCHED: $($f.FullName)"
    }
}

# Quick scan: show if anything still contains \u / \\u in values-it
$hits = Select-String -Path (Join-Path $resDir "*.xml") -Pattern '\\u|\\\\u|\\U|\\\\U' -ErrorAction SilentlyContinue
if ($hits) {
    Write-Host ""
    Write-Host "⚠️ Remaining sequences that look like unicode escapes (please paste these lines if build still fails):"
    $hits | Select-Object -First 30 | ForEach-Object { Write-Host (" - " + $_.Path + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
} else {
    Write-Host ""
    Write-Host "✅ No \u-style escape sequences detected in values-it after patch."
}

Write-Host ""
Write-Host "✅ Done."
Write-Host ("Backup saved here: " + $backupDir)
Write-Host ""
Write-Host "Next step: run a clean build:"
Write-Host "  .\gradlew clean assembleDebug"
