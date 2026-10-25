# Packages reviewed files only. Does not access Docker, SSH or any server.
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$deliveryDir = Join-Path $repoRoot '.tools/delivery'
$null = New-Item -ItemType Directory -Force -Path $deliveryDir
$bundlePath = Join-Path $deliveryDir 'elbracht-smp-read-only.zip'
$files = @(
    'agent/Dockerfile',
    'agent/target/smp-host-agent-0.1.0-SNAPSHOT.jar',
    '.dockerignore',
    'infra/docker-01/container/compose.yml',
    'infra/docker-01/container/proxy.Dockerfile',
    'infra/docker-01/container/docker-read-only.conf',
    'infra/docker-01/container/.dockerignore',
    'infra/docker-01/container/.env.example',
    'infra/docker-01/container/README.md'
)
foreach ($relative in $files) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $relative) -PathType Leaf)) {
        throw "Missing package input: $relative. Build the agent first."
    }
}
Add-Type -AssemblyName System.IO.Compression
$stream = [System.IO.File]::Open($bundlePath, [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
$checksums = [System.Collections.Generic.List[string]]::new()
try {
    foreach ($relative in $files) {
        $source = Join-Path $repoRoot $relative
        $entry = $archive.CreateEntry($relative)
        $entryStream = $entry.Open()
        $sourceStream = [System.IO.File]::OpenRead($source)
        try { $sourceStream.CopyTo($entryStream) }
        finally { $sourceStream.Dispose(); $entryStream.Dispose() }
        $checksums.Add("$((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant())  $relative")
    }
    $manifest = $archive.CreateEntry('SHA256SUMS').Open()
    $writer = [System.IO.StreamWriter]::new($manifest, [System.Text.UTF8Encoding]::new($false))
    try { $writer.NewLine = "`n"; foreach ($line in $checksums) { $writer.WriteLine($line) } }
    finally { $writer.Dispose() }
} finally { $archive.Dispose(); $stream.Dispose() }
Get-FileHash -LiteralPath $bundlePath -Algorithm SHA256 | Select-Object Path, Hash
