$dest = "$env:APPDATA/scbw/bots/Infested Artosis/AI"
Remove-Item "$dest/*.jar" -Force -ErrorAction SilentlyContinue
Copy-Item (Get-Item "$PSScriptRoot/../target/*-jar-with-dependencies.jar") -Destination $dest
