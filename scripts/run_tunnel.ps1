Write-Host "Starting persistent localtunnel on port 8080..."
while ($true) {
    npx -y localtunnel --port 8080
    Start-Sleep -Seconds 2
}
