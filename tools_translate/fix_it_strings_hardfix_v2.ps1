<# 
fix_it_strings_hardfix_v2.ps1
Fixes common "Invalid unicode escape sequence in string" issues in Italian resources (values-it),
and removes duplicate <string> resources across values-it xml files (prefers strings.xml over hotfix files).

How to run (from project root):
  powershell -ExecutionPolicy Bypass -File .\tools_translate\fix_it_strings_hardfix_v2.ps1

Then:
  .\gradlew clean assembleDebug
#>

$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
  $scriptDir = Split-Path -Parent $PSCommandPath
  return (Split-Path -Parent $scriptDir)
}

function Backup-Files([string[]]$files, [string]$backupDir) {
  New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
  foreach ($f in $files) {
    if (Test-Path $f) {
      $rel = $f.Replace((Get-ProjectRoot() + "\"), "")
      $dst = Join-Path $backupDir $rel
      $dstDir = Split-Path -Parent $dst
      New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
      Copy-Item -Force $f $dst
    }
  }
}

function Convert-UnicodeEscapes([string]$s) {
  # Convert both \uXXXX and \\uXXXX into actual characters.
  $s = [regex]::Replace($s, '\\\\u([0-9a-fA-F]{4})', {
    param($m)
    [char][int]::Parse($m.Groups[1].Value, [System.Globalization.NumberStyles]::HexNumber)
  })
  $s = [regex]::Replace($s, '(?<!\\)\\u([0-9a-fA-F]{4})', {
    param($m)
    [char][int]::Parse($m.Groups[1].Value, [System.Globalization.NumberStyles]::HexNumber)
  })
  return $s
}

function Fix-InvalidBackslashEscapes([string]$s) {
  # 1) Convert valid unicode escapes to chars
  $s = Convert-UnicodeEscapes $s

  # 2) Fix broken \u sequences that are NOT followed by exactly 4 hex digits
  $s = [regex]::Replace($s, '\\u(?![0-9a-fA-F]{4})', 'u')

  # 3) Any remaining "\" that is not starting a valid escape sequence -> escape the backslash itself
  # Allowed escapes in Android string resources: \n, \t, \', \", \\ and unicode \uXXXX
  $s = [regex]::Replace($s, '\\(?![nt''"\\]|u[0-9a-fA-F]{4})', '\\\\')

  return $s
}

function Replace-Or-InsertString([string]$content, [string]$name, [string]$value) {
  $pattern = "<string\s+name=`"$([regex]::Escape($name))`"[^>]*>.*?</string>"
  $replacement = "<string name=`"$name`">$value</string>"

  if ([regex]::IsMatch($content, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    return [regex]::Replace($content, $pattern, $replacement, [System.Text.RegularExpressions.RegexOptions]::Singleline)
  }

  # Insert before </resources>
  $insert = "  " + $replacement + "`r`n"
  if ($content -match "</resources>") {
    return ($content -replace "</resources>", ($insert + "</resources>"))
  }

  return $content
}

function Remove-StringResource([string]$content, [string]$name) {
  $pattern = "`r?`n?\s*<string\s+name=`"$([regex]::Escape($name))`"[^>]*>.*?</string>\s*"
  return [regex]::Replace($content, $pattern, "`r`n", [System.Text.RegularExpressions.RegexOptions]::Singleline)
}

function Get-StringNames([string]$content) {
  $matches = [regex]::Matches($content, '<string\s+name="([^"]+)"', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
  $names = @()
  foreach ($m in $matches) { $names += $m.Groups[1].Value }
  return $names
}

$root = Get-ProjectRoot
$itDir = Join-Path $root "app\src\main\res\values-it"

if (!(Test-Path $itDir)) {
  Write-Host "❌ Folder not found: $itDir"
  exit 1
}

$xmlFiles = Get-ChildItem -Path $itDir -Filter "*.xml" -File | Select-Object -ExpandProperty FullName
if ($xmlFiles.Count -eq 0) {
  Write-Host "❌ No xml files found in: $itDir"
  exit 1
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupDir = Join-Path $root ("tools_translate\_it_hardfix_backup\" + $stamp)
Backup-Files $xmlFiles $backupDir

Write-Host "🧰 Backup created: $backupDir"

# Read all files
$contents = @{}
foreach ($f in $xmlFiles) {
  $contents[$f] = Get-Content -Raw -LiteralPath $f
}

# 1) Fix invalid escape sequences across ALL values-it xml files
foreach ($f in $xmlFiles) {
  $before = $contents[$f]
  $after  = Fix-InvalidBackslashEscapes $before
  if ($after -ne $before) {
    $contents[$f] = $after
    Write-Host "PATCHED escapes: $($f.Replace($root+'\',''))"
  }
}

# 2) Remove duplicates inside values-it (prefer strings.xml, and prefer non-hotfix files)
# Build map: name -> list of files
$nameToFiles = @{}
foreach ($f in $xmlFiles) {
  foreach ($n in (Get-StringNames $contents[$f])) {
    if (-not $nameToFiles.ContainsKey($n)) { $nameToFiles[$n] = New-Object System.Collections.ArrayList }
    [void]$nameToFiles[$n].Add($f)
  }
}

function Score-File([string]$file) {
  # lower score = keep preferred
  $fn = [System.IO.Path]::GetFileName($file).ToLowerInvariant()
  if ($fn -eq "strings.xml") { return 0 }
  if ($fn -like "*hotfix*") { return 50 }
  return 10
}

$duplicates = $nameToFiles.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 }
foreach ($kv in $duplicates) {
  $name = $kv.Key
  $files = $kv.Value | Sort-Object { Score-File $_ }

  $keep = $files[0]
  $removeFrom = $files | Select-Object -Skip 1

  foreach ($rf in $removeFrom) {
    $before = $contents[$rf]
    $after  = Remove-StringResource $before $name
    if ($after -ne $before) {
      $contents[$rf] = $after
      Write-Host "REMOVED duplicate '$name' from: $($rf.Replace($root+'\','')) (kept in $([System.IO.Path]::GetFileName($keep)))"
    }
  }
}

# 3) Deterministic safe replacements for the specific problematic keys (Italian)
$stringsXml = Join-Path $itDir "strings.xml"
if ($contents.ContainsKey($stringsXml)) {
  $c = $contents[$stringsXml]
  $c = Replace-Or-InsertString $c "pref_app_language_title"  "Lingua dell’app"
  $c = Replace-Or-InsertString $c "pref_app_language_summary" "Scegli la lingua da usare nell’app."
  $contents[$stringsXml] = $c
  Write-Host "UPDATED keys in: values-it/strings.xml"
}

$hotfixXml = Join-Path $itDir "strings_settings_prefs_hotfix.xml"
if ($contents.ContainsKey($hotfixXml)) {
  $c = $contents[$hotfixXml]
  # Make sure language strings are NOT duplicated here
  $c = Remove-StringResource $c "pref_app_language_title"
  $c = Remove-StringResource $c "pref_app_language_summary"
  # Provide safe keep-screen-on strings
  $c = Replace-Or-InsertString $c "pref_keep_screen_on_title"  "Mantieni lo schermo acceso"
  $c = Replace-Or-InsertString $c "pref_keep_screen_on_summary" "Evita che lo schermo si spenga mentre usi l’app."
  $contents[$hotfixXml] = $c
  Write-Host "UPDATED keys in: values-it/strings_settings_prefs_hotfix.xml"
}

$safetyXml = Join-Path $itDir "strings_safety.xml"
if ($contents.ContainsKey($safetyXml)) {
  $c = $contents[$safetyXml]
  $c = Replace-Or-InsertString $c "ht_safety_storm_during" "Durante un temporale:\n• Allontanati da creste, cime e punti esposti.\n• Evita alberi isolati, strutture metalliche e corsi d’acqua.\n• Se possibile, ripara in un edificio o in un’auto.\n• In mancanza di riparo, accovacciati su materiale isolante con i piedi uniti."
  $c = Replace-Or-InsertString $c "ht_safety_nav_body" "Navigazione e orientamento:\n• Scarica la mappa offline e verifica la traccia prima di partire.\n• Controlla spesso posizione e punti di riferimento (incroci, passi, rifugi).\n• Porta una power bank e, se possibile, una bussola come backup.\n• Se ti perdi: fermati, torna all’ultimo punto certo e avvisa qualcuno."
  $contents[$safetyXml] = $c
  Write-Host "UPDATED keys in: values-it/strings_safety.xml"
}

# Write back
foreach ($f in $xmlFiles) {
  $text = $contents[$f]
  # Normalize line endings to CRLF (PowerShell-friendly). Android doesn't care.
  $text = $text -replace "`r?`n", "`r`n"
  Set-Content -LiteralPath $f -Value $text -Encoding UTF8
}

Write-Host ""
Write-Host "✅ Done."
Write-Host "Backup saved here: $backupDir"
Write-Host ""
Write-Host "Next step:"
Write-Host "  .\gradlew clean assembleDebug"
