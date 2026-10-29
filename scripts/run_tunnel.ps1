Write-Host "Starting persistent localhost.run SSH tunnel on port 8080..."
while ($true) {
    ssh -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -R 80:localhost:8080 nokey@localhost.run
    Write-Host "Tunnel disconnected. Reconnecting in 3 seconds..."
    Start-Sleep -Seconds 3
}
