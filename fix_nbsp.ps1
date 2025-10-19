param(
    [Parameter(Mandatory=$false)]
    [string]$FilePath = ".\app\src\main\res\values\strings_waypoint_additions.xml"
)

if (-not (Test-Path $FilePath)) {
    Write-Error "File not found: $FilePath"
    exit 1
}

# Read file as raw text, replace all &nbsp; with the numeric XML entity (or you can use \u00A0).
# We use the numeric entity to keep the file purely XML-compliant.
$raw = Get-Content -LiteralPath $FilePath -Raw -Encoding UTF8
$fixed = $raw -replace "&nbsp;", "&#160;" -replace "&#xA0;", "&#160;"

# Write back with UTF8 (no BOM)
[System.IO.File]::WriteAllText((Resolve-Path $FilePath), $fixed, (New-Object System.Text.UTF8Encoding($false)))

Write-Output "✅ Fixed non-breaking spaces in: $FilePath"
