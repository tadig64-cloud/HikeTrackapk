<# 
HikeTrackapk - Fix invalid unicode escape sequences in Italian string resources (values-it)

How to run (from project root):
  powershell -ExecutionPolicy Bypass -File .\tools_translate\fix_it_invalid_unicode.ps1

What it does:
- Creates a timestamped backup of the edited files
- Patches a few known keys with safe Italian text
- Scans ALL values-it XML files and fixes invalid backslash escape sequences:
    - converts \u{...} to real Unicode characters
    - escapes invalid backslashes so AAPT won't fail
#>

$ErrorActionPreference = "Stop"

# --- locate project root ---
# If this script is stored in: <projectRoot>\tools_translate\fix_it_invalid_unicode.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path (Join-Path $projectRoot "app\src\main\res"))) {
  throw "Can't find app\src\main\res from projectRoot: $projectRoot. Run from the HikeTrackapk project, and keep this script inside tools_translate."
}

# Force also the .NET current directory (important for some APIs)
[System.IO.Directory]::SetCurrentDirectory($projectRoot)

# --- backup folder ---
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupRoot = Join-Path $projectRoot "tools_translate\_it_invalid_unicode_backup\$timestamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

# UTF-8 no BOM
$encNoBom = New-Object System.Text.UTF8Encoding($false)

function Save-XmlUtf8NoBom([xml]$xmlDoc, [string]$path, $encoding) {
  $settings = New-Object System.Xml.XmlWriterSettings
  $settings.Encoding = $encoding
  $settings.Indent = $true
  $settings.NewLineChars = "`n"
  $settings.OmitXmlDeclaration = $false
  $writer = [System.Xml.XmlWriter]::Create($path, $settings)
  $xmlDoc.Save($writer)
  $writer.Close()
}

function Ensure-XmlFile([string]$absPath) {
  if (-not (Test-Path $absPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $absPath) | Out-Null
    # create a minimal resources file
    @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>
"@ | Set-Content -LiteralPath $absPath -Encoding UTF8
  }
}

function Backup-File([string]$absPath) {
  $rel = $absPath.Substring($projectRoot.Length).TrimStart('\','/')
  $dest = Join-Path $backupRoot $rel
  New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
  Copy-Item -LiteralPath $absPath -Destination $dest -Force
}

function Get-Or-CreateStringNode([xml]$xmlDoc, [string]$name) {
  $node = $xmlDoc.SelectSingleNode("//string[@name='$name']")
  if ($null -eq $node) {
    $resources = $xmlDoc.SelectSingleNode("/resources")
    if ($null -eq $resources) {
      throw "No <resources> root"
    }
    $node = $xmlDoc.CreateElement("string")
    $attr = $xmlDoc.CreateAttribute("name")
    $attr.Value = $name
    $node.Attributes.Append($attr) | Out-Null
    $resources.AppendChild($node) | Out-Null
  }
  return $node
}

function Fix-AndroidStringEscapes([string]$s) {
  if ($null -eq $s) { return $s }

  # 1) Convert modern \u{...} escapes into real Unicode chars
  #    Example: "\u{2019}" -> "’"
  $s = [regex]::Replace($s, "\\u\{([0-9a-fA-F]{4,6})\}", {
    param($m)
    $hex = $m.Groups[1].Value
    $code = [Convert]::ToInt32($hex, 16)
    return [char]::ConvertFromUtf32($code)
  })

  # 2) If we still have "\u" but NOT followed by 4 hex digits, escape the backslash so it's treated literally
  $s = [regex]::Replace($s, "\\u(?![0-9a-fA-F]{4})", "\\\\u")

  # 3) Escape any backslash that is not part of a supported Android escape sequence: \n \t \r \' \" \\ \uXXXX
  $s = [regex]::Replace($s, "\\(?![ntr'""\\u])", "\\\\")

  return $s
}

# --- Patch known keys with safe Italian text (no weird escapes) ---
$patchMap = @{
  "app\src\main\res\values-it\strings_safety.xml" = @{
    "ht_safety_storm_during" = "Durante un temporale: evita creste, alberi isolati e acqua. Spostati in un luogo più basso e sicuro."
    "ht_safety_nav_body"     = "Navigazione: pianifica il percorso, porta una mappa offline e tieni d'occhio i riferimenti. Se ti perdi, fermati e rivaluta."
  }
  "app\src\main\res\values-it\strings_settings_prefs_hotfix.xml" = @{
    "pref_app_language_summary"   = "Scegli la lingua dell'interfaccia."
    "pref_keep_screen_on_summary" = "Mantiene lo schermo acceso durante l'escursione."
  }
  "app\src\main\res\values-it\strings.xml" = @{
    "pref_app_language_title"     = "Lingua dell'app"
  }
}

foreach ($rel in $patchMap.Keys) {
  $abs = Join-Path $projectRoot $rel
  Ensure-XmlFile $abs
  Backup-File $abs

  $xml = New-Object System.Xml.XmlDocument
  $xml.PreserveWhitespace = $true
  $xml.Load($abs)

  foreach ($key in $patchMap[$rel].Keys) {
    $val = $patchMap[$rel][$key]
    $node = Get-Or-CreateStringNode $xml $key
    $node.InnerText = $val
  }

  Save-XmlUtf8NoBom $xml $abs $encNoBom
  Write-Host "PATCHED: $rel"
}

# --- Scan & auto-fix ALL Italian strings (values-it) ---
$itDir = Join-Path $projectRoot "app\src\main\res\values-it"
if (Test-Path $itDir) {
  $xmlFiles = Get-ChildItem -LiteralPath $itDir -Filter "*.xml" -File
  foreach ($f in $xmlFiles) {
    $abs = $f.FullName
    $xml = New-Object System.Xml.XmlDocument
    $xml.PreserveWhitespace = $true
    $xml.Load($abs)

    $changed = $false
    $stringNodes = $xml.SelectNodes("//string")
    foreach ($node in $stringNodes) {
      $old = $node.InnerText
      $new = Fix-AndroidStringEscapes $old
      if ($new -ne $old) {
        $node.InnerText = $new
        $changed = $true
      }
    }

    if ($changed) {
      Backup-File $abs
      Save-XmlUtf8NoBom $xml $abs $encNoBom
      Write-Host "AUTO-FIXED: $($f.Name)"
    }
  }
}

Write-Host ""
Write-Host "✅ Done."
Write-Host "Backup saved here: $backupRoot"
Write-Host ""
Write-Host "Next step: run a clean build:"
Write-Host "  .\gradlew clean assembleDebug"
