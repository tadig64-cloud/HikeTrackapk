<#
Scan-Duplicate-Resources.ps1
Scans a res values folder (default: values-it) and reports duplicate resource keys
(type + name) across XML files in that SAME folder, which is what breaks AAPT/AAPT2.

Example:
  .\Scan-Duplicate-Resources.ps1 -Root "C:\...\HikeTrackapk" -Folder "values-it"
#>

[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)]
  [string]$Root,

  [string]$Folder = "values-it"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Dir = Join-Path $Root ("app\src\main\res\" + $Folder)

Write-Host ""
Write-Host "Scanning: $Dir"
Write-Host ""

if (!(Test-Path $Dir)) { throw "Folder not found: $Dir" }

function Get-XmlDoc([string]$filePath) {
  try {
    $raw = Get-Content -Path $filePath -Raw -Encoding UTF8
    return [xml]$raw
  } catch {
    Write-Warning ("Skipping (invalid XML?): " + $filePath)
    return $null
  }
}

function Make-Key([string]$type, [string]$name) {
  return "$($type)|$name"
}

$wantedTypes = @(
  "string",
  "plurals",
  "string-array",
  "integer-array",
  "array"
)

# key -> list of "file:line?" (we keep just file for simplicity)
$map = @{}

$files = Get-ChildItem -Path $Dir -Filter *.xml -File
foreach ($f in $files) {
  $doc = Get-XmlDoc $f.FullName
  if ($null -eq $doc -or $null -eq $doc.resources) { continue }

  foreach ($n in $doc.resources.ChildNodes) {
    if ($n.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
    if ($wantedTypes -notcontains $n.Name) { continue }
    $nameAttr = $n.Attributes["name"]
    if ($null -eq $nameAttr) { continue }

    $type = $n.Name
    $name = $nameAttr.Value
    if ([string]::IsNullOrWhiteSpace($name)) { continue }

    $key = Make-Key $type $name

    if (-not $map.ContainsKey($key)) { $map[$key] = @() }
    $map[$key] += $f.Name
  }
}

$dups = $map.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 } | Sort-Object Name

if ($dups.Count -eq 0) {
  Write-Host "No duplicates found in $Folder. (Good.)"
  exit 0
}

Write-Host ("Duplicates found: " + $dups.Count)
Write-Host ""

foreach ($d in $dups) {
  $key = $d.Key
  $filesList = ($d.Value | Sort-Object) -join ", "
  Write-Host (" - " + $key + "  ->  " + $filesList)
}

Write-Host ""
Write-Host "Fix idea: keep the resource only once in this folder (values-it)."
Write-Host "If you want, paste the duplicate lines and I tell you exactly which file should keep what."
