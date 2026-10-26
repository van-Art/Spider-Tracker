$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot 'source'
$html = Get-Content (Join-Path $root 'index.html') -Raw
$queue = [Collections.Generic.Queue[string]]::new()
$seen = [Collections.Generic.HashSet[string]]::new()
foreach ($m in [regex]::Matches($html, '(?:src|href)="(\./[^"?]+\.(?:js|css))"')) { $queue.Enqueue($m.Groups[1].Value) }
$rows = @()
while ($queue.Count) {
    $relative = $queue.Dequeue() -replace '^\./', ''
    if (-not $seen.Add($relative)) { continue }
    $uri = [uri]::new([uri]'https://spideytracker.net/', $relative)
    if ($uri.Host -ne 'spideytracker.net') { throw 'Unexpected origin' }
    $destination = Join-Path $root $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Invoke-WebRequest -Uri $uri.AbsoluteUri -OutFile $destination
    $rows += [pscustomobject]@{url=$uri.AbsoluteUri; path=$relative; sha256=(Get-FileHash $destination -Algorithm SHA256).Hash; bytes=(Get-Item $destination).Length}
    if ($relative.EndsWith('.js')) {
        $text = Get-Content $destination -Raw
        foreach ($m in [regex]::Matches($text, '(?:from\s*|import\s*)["''](\./[^"'']+\.js)["'']|import\(["''](\./[^"'']+\.js)["'']\)')) {
            $part = if ($m.Groups[1].Success) {$m.Groups[1].Value} else {$m.Groups[2].Value}
            $child = [uri]::new($uri, $part)
            $queue.Enqueue($child.AbsolutePath.TrimStart('/'))
        }
    }
}
if ($rows.Count -lt 20) { throw 'Incomplete source capture' }
$rows | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $PSScriptRoot 'evidence/source-manifest.json')
Write-Output "Captured $($rows.Count) public JS/CSS resources"
