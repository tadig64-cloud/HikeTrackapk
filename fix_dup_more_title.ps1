param(
    [string]$HelpFilePath = ".\app\src\main\res\values\strings_help.xml"
)

if (!(Test-Path $HelpFilePath)) {
    Write-Host "File not found: $HelpFilePath"
    exit 1
}

# Load XML with UTF-8
[xml]$doc = Get-Content -Raw -Encoding UTF8 $HelpFilePath

# Collect nodes named 'more_title' under <resources>
$nodes = @()
if ($doc.resources) {
    if ($doc.resources.string) {
        $nodes += @($doc.resources.string | Where-Object { $_.name -eq "more_title" })
    }
    if ($doc.resources.'string-array') {
        $nodes += @($doc.resources.'string-array' | Where-Object { $_.name -eq "more_title" })
    }
    if ($doc.resources.plurals) {
        $nodes += @($doc.resources.plurals | Where-Object { $_.name -eq "more_title" })
    }
}

if ($nodes.Count -eq 0) {
    Write-Host "OK: no <string name='more_title'> in $HelpFilePath"
    exit 0
}

foreach ($n in $nodes) {
    [void]$n.ParentNode.RemoveChild($n)
    Write-Host "Removed duplicate 'more_title' from $HelpFilePath"
}

# Pretty save as UTF-8 (no BOM)
$settings = New-Object System.Xml.XmlWriterSettings
$settings.Indent = $true
$settings.OmitXmlDeclaration = $false
$settings.Encoding = New-Object System.Text.UTF8Encoding($false)
$writer = [System.Xml.XmlWriter]::Create($HelpFilePath, $settings)
$doc.Save($writer)
$writer.Close()

Write-Host "Saved $HelpFilePath (UTF-8)"
exit 0
