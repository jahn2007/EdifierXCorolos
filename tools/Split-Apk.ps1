param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [string]$OutputDir = "apk-parts"
)
$ErrorActionPreference = "Stop"
$source = (Resolve-Path -LiteralPath $InputPath).Path
[void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetFullPath($OutputDir))
$destination = (Resolve-Path -LiteralPath $OutputDir).Path
if (@(Get-ChildItem -LiteralPath $destination -Force).Count -ne 0) {
    throw "Output directory must be empty. Existing files will not be overwritten."
}
$limit = 30 * 1024 * 1024
$buffer = New-Object byte[] $limit
$parts = @()
$stream = [System.IO.File]::OpenRead($source)
try {
    $index = 1
    while ($stream.Position -lt $stream.Length) {
        $count = 0
        while ($count -lt $limit) {
            $read = $stream.Read($buffer, $count, $limit - $count)
            if ($read -eq 0) { break }
            $count += $read
        }
        $name = "official-apk.part{0:D3}" -f $index
        $target = Join-Path $destination $name
        $out = [System.IO.File]::Open($target, [System.IO.FileMode]::CreateNew)
        try { $out.Write($buffer, 0, $count) } finally { $out.Dispose() }
        $parts += @{ name = $name; size = $count; sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() }
        $index++
    }
} finally { $stream.Dispose() }
$manifest = @{ size = (Get-Item -LiteralPath $source).Length; sha256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant(); parts = $parts }
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination "manifest.json") -Encoding UTF8
Write-Output "Created $($parts.Count) private parts under $destination. Do not commit the APK, parts, or manifest to GitHub."
