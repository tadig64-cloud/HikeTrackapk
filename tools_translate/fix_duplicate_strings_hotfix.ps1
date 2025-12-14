# Fix duplicate resources between strings.xml and strings_settings_prefs_hotfix.xml
# Usage (from project root):
#   powershell -ExecutionPolicy Bypass -File .\tools_translate\fix_duplicate_strings_hotfix.ps1
#
# What it does:
# - For each res/values* folder:
#   - if strings.xml and strings_settings_prefs_hotfix.xml both exist
#   - remove from the hotfix file any <string name="..."> that already exists in strings.xml
# - Creates a timestamped backup before editing

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Ensure we are in project root
$projectRoot = (Get-Location).Path
[System.IO.Directory]::SetCurrentDirectory($projectRoot)

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupRoot = Join-Path $projectRoot "tools_translate\_dup_resources_backup\$timestamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

$encNoBom = New-Object System.Text.UTF8Encoding($false)

function Save-XmlUtf8NoBom([xml]$xmlDoc, [string]$path, $encoding) {
  $settings = New-Object System.Xml.XmlWriterSettings
  $settings.Encoding = $encoding
  $settings.Indent = $false
  $settings.NewLineChars = "`n"
  $settings.OmitXmlDeclaration = $false
  $writer = [System.Xml.XmlWriter]::Create($path, $settings)
  $xmlDoc.Save($writer)
  $writer.Close()
}

# Find all "values*" resource folders
$resRoot = Join-Path $projectRoot "app\src\main\res"
$valuesDirs = Get-ChildItem -Path $resRoot -Directory | Where-Object { $_.Name -like "values*" }

$changedAny = $false

foreach ($dir in $valuesDirs) {
  $stringsPath = Join-Path $dir.FullName "strings.xml"
  $hotfixPath  = Join-Path $dir.FullName "strings_settings_prefs_hotfix.xml"

  if (-not (Test-Path $stringsPath) -or -not (Test-Path $hotfixPath)) {
    continue
  }

  # Backup hotfix file (keep folder structure)
  $relHotfix = $hotfixPath.Substring($projectRoot.Length).TrimStart("\")
  $backupHotfix = Join-Path $backupRoot $relHotfix
  New-Item -ItemType Directory -Force -Path (Split-Path $backupHotfix) | Out-Null
  Copy-Item -LiteralPath $hotfixPath -Destination $backupHotfix -Force

  $stringsXml = New-Object System.Xml.XmlDocument
  $stringsXml.PreserveWhitespace = $true
  $stringsXml.Load($stringsPath)

  $hotfixXml = New-Object System.Xml.XmlDocument
  $hotfixXml.PreserveWhitespace = $true
  $hotfixXml.Load($hotfixPath)

  $baseNames = @{}
  foreach ($n in $stringsXml.SelectNodes("/resources/string")) {
    $nameAttr = $n.Attributes["name"]
    if ($null -ne $nameAttr -and -not $baseNames.ContainsKey($nameAttr.Value)) {
      $baseNames[$nameAttr.Value] = $true
    }
  }

  $removed = @()
  $hotfixNodes = @($hotfixXml.SelectNodes("/resources/string"))
  foreach ($n in $hotfixNodes) {
    $nameAttr = $n.Attributes["name"]
    if ($null -eq $nameAttr) { continue }
    $key = $nameAttr.Value
    if ($baseNames.ContainsKey($key)) {
      # remove duplicate from hotfix
      [void]$n.ParentNode.RemoveChild($n)
      $removed += $key
    }
  }

  if ($removed.Count -gt 0) {
    Save-XmlUtf8NoBom $hotfixXml $hotfixPath $encNoBom
    $changedAny = $true
    Write-Host ("PATCHED: {0}  (removed duplicates: {1})" -f $hotfixPath, ($removed -join ", "))
  }
}

if ($changedAny) {
  Write-Host ""
  Write-Host "✅ Done."
  Write-Host "Backup saved here: $backupRoot"
  Write-Host ""
  Write-Host "Next step: run a clean build:"
  Write-Host "  .\gradlew clean assembleDebug"
} else {
  Write-Host "No duplicates found between strings.xml and strings_settings_prefs_hotfix.xml."
  Write-Host "Backup folder (created anyway): $backupRoot"
}
