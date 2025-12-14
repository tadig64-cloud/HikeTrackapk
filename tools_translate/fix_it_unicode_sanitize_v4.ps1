param()

$ErrorActionPreference = "Stop"

function Write-Utf8NoBom([string]$path, [string]$text) {
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
}

function Remove-StringByName([string]$xml, [string]$name) {
  $n = [regex]::Escape($name)
  $pattern = "(?s)\r?\n?\s*<string\s+name=`"$n`"[^>]*>.*?</string>\s*"
  if ([regex]::IsMatch($xml, $pattern)) {
    return [regex]::Replace($xml, $pattern, "`r`n", 1)
  }
  return $xml
}

function Upsert-String([string]$xml, [string]$name, [string]$value) {
  $n = [regex]::Escape($name)
  $pattern = "(?s)<string\s+name=`"$n`"[^>]*>.*?</string>"
  $replacement = "<string name=""$name"">$value</string>"
  if ([regex]::IsMatch($xml, $pattern)) {
    return [regex]::Replace($xml, $pattern, $replacement, 1)
  }

  # Insert before </resources>
  if ($xml -match "</resources>") {
    return ($xml -replace "</resources>", "  $replacement`r`n</resources>")
  }

  # Fallback append
  return ($xml + "`r`n$replacement`r`n")
}

function Convert-And-Escape-UnicodeEscapes([string]$txt) {
  # 1) Convert valid \uXXXX sequences into XML numeric entities: &#xXXXX;
  $txt = [regex]::Replace($txt, "\\u([0-9a-fA-F]{4})", { param($m) "&#x$($m.Groups[1].Value);" })

  # 2) Any remaining \u (invalid sequences) -> make it literal by escaping the backslash: \\u
  # This prevents AAPT from interpreting it as an escape.
  $txt = [regex]::Replace($txt, "\\u", "\\\\u")

  return $txt
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$valuesIt = Join-Path $projectRoot "app\src\main\res\values-it"

if (!(Test-Path $valuesIt)) {
  throw "Folder not found: $valuesIt`nRun this from inside your HikeTrackapk project (tools_translate folder must be under project root)."
}

$backupDir = Join-Path $PSScriptRoot ("_it_unicode_v4_backup\" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$files = Get-ChildItem $valuesIt -Filter "*.xml" -File

foreach ($file in $files) {
  $backupPath = Join-Path $backupDir $file.Name
  Copy-Item $file.FullName $backupPath -Force

  $txt = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
  $before = $txt

  # Global sanitize for \u escapes (common culprit from machine translations)
  $txt = Convert-And-Escape-UnicodeEscapes $txt

  # Ensure prefs strings are defined ONLY once (we keep them in strings.xml)
  if ($file.Name -ieq "strings_settings_prefs_hotfix.xml") {
    $txt = Remove-StringByName $txt "pref_app_language_title"
    $txt = Remove-StringByName $txt "pref_app_language_summary"
    $txt = Remove-StringByName $txt "pref_keep_screen_on_title"
    $txt = Remove-StringByName $txt "pref_keep_screen_on_summary"
  }

  # Force-safe Italian values for the strings that keep failing
  if ($file.Name -ieq "strings.xml") {
    $txt = Upsert-String $txt "pref_app_language_title" "Lingua dell&apos;app"
    $txt = Upsert-String $txt "pref_app_language_summary" "Scegli la lingua dell&apos;interfaccia."
    $txt = Upsert-String $txt "pref_keep_screen_on_title" "Mantieni schermo acceso"
    $txt = Upsert-String $txt "pref_keep_screen_on_summary" "Mantieni lo schermo acceso mentre usi HikeTrack."
  }

  if ($file.Name -ieq "strings_safety.xml") {
    # Keep text very simple (no exotic punctuation) to avoid any escaping edge case
    $txt = Upsert-String $txt "ht_safety_storm_during" "Durante un temporale, evita creste e zone esposte. Se senti tuoni, scendi e cerca riparo."
    # Also sanitize the other one that was failing earlier (nav body)
    $txt = Upsert-String $txt "ht_safety_nav_body" "Apri un&apos;app di navigazione e invia la tua posizione se hai bisogno di aiuto."
  }

  if ($txt -ne $before) {
    Write-Utf8NoBom $file.FullName $txt
    $rel = $file.FullName.Substring($projectRoot.Length + 1)
    Write-Host "PATCHED: $rel"
  }
}

Write-Host ""
Write-Host "✅ Done."
Write-Host "Backup saved here: $backupDir"
Write-Host ""
Write-Host "Next step: run a clean build:"
Write-Host "  .\gradlew clean assembleDebug"
