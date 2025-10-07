Write-Host "Current Directory: $(Get-Location)"
$source = "bwapi-data\write"
$destination = "bwapi-data\read"
$files = Get-ChildItem -Path $source -Include *.json,*.csv -Recurse
Write-Host "Found $($files.Count) files to move."
foreach ($file in $files) {
    try {
        Write-Host "Moving: $($file.FullName) -> $destination"
        Move-Item $file.FullName -Destination $destination -Force
    } catch {
        Write-Host "Error moving $($file.FullName): $_"
    }
}
