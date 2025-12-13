param(
    [string]$ValuesDir = ".\app\src\main\res\values"
)

if (!(Test-Path $ValuesDir)) {
    Write-Host "Values directory not found: $ValuesDir"
    exit 1
}

$map = @{}

Get-ChildItem -Path $ValuesDir -Filter *.xml | ForEach-Object {
    $path = $_.FullName
    try {
        [xml]$x = Get-Content -Raw -Encoding UTF8 $path
    } catch {
        Write-Warning "Skip unreadable XML: $path"
        return
    }

    $names = @()

    if ($x.resources) {
        if ($x.resources.string) {
            $x.resources.string | ForEach-Object { if ($_.name) { $names += $_.name } }
        }
        if ($x.resources.'string-array') {
            $x.resources.'string-array' | ForEach-Object { if ($_.name) { $names += $_.name } }
        }
        if ($x.resources.plurals) {
            $x.resources.plurals | ForEach-Object { if ($_.name) { $names += $_.name } }
        }
    }

    foreach ($n in $names) {
        if (-not $map.ContainsKey($n)) { $map[$n] = @() }
        $map[$n] += $path
    }
}

$dups = $map.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 } | Sort-Object { $_.Key }

if ($dups.Count -eq 0) {
    Write-Host "OK: no duplicate string keys found under $ValuesDir"
    exit 0
}

Write-Host "Duplicate keys found:"
foreach ($entry in $dups) {
    Write-Host ("`n{0}" -f $entry.Key)
    $entry.Value | Sort-Object -Unique | ForEach-Object { Write-Host ("  - {0}" -f $_) }
}

exit 0
