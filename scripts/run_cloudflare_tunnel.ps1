Write-Host "Starting Cloudflare Public Tunnel on port 8080..."
.\scripts\cloudflared.exe tunnel --protocol http2 --url http://localhost:8080
