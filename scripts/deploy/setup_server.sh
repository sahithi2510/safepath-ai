#!/usr/bin/env bash
# ============================================================
# SafePath AI — Automated Cloud VPS Deployment Script
# ============================================================
# Tested on: Ubuntu 22.04 / 24.04 LTS, Debian 12
# Providers: Oracle Cloud Free Tier, DigitalOcean, Hetzner, AWS
#
# Usage:
#   chmod +x scripts/deploy/setup_server.sh
#   ./scripts/deploy/setup_server.sh
# ============================================================

set -e

echo "============================================================"
echo "🚀 SafePath AI — 24/7 Production Server Provisioning"
echo "============================================================"

# 1. Verify root or sudo access
if [ "$EUID" -ne 0 ]; then
  echo "⚠️  Please run as root or with sudo:"
  echo "    sudo ./scripts/deploy/setup_server.sh"
  exit 1
fi

# 2. Update packages and install core utilities
echo "📦 Updating OS package index..."
apt-get update -y
apt-get install -y curl wget git openssl ca-certificates gnupg lsb-release ufw

# 3. Install Docker Engine & Docker Compose Plugin (if not present)
if ! command -v docker &> /dev/null; then
  echo "🐳 Installing Docker Engine..."
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg

  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null

  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable docker
  systemctl start docker
  echo "✅ Docker installed successfully."
else
  echo "✅ Docker is already installed."
fi

# 4. Configure Firewall (UFW)
echo "🛡️  Configuring UFW Firewall (Ports: 22, 80, 443)..."
ufw allow 22/tcp || true
ufw allow 80/tcp || true
ufw allow 443/tcp || true
ufw --force enable || true
echo "✅ Firewall rules active."

# 5. Create or verify production .env
if [ ! -f .env ]; then
  echo "🔑 Generating cryptographically secure .env file..."
  cp .env.production.example .env

  # Detect public IP
  PUBLIC_IP=$(curl -s ifconfig.me || curl -s icanhazip.com || echo "localhost")
  
  # Generate secrets
  RAND_DB_PASS=$(openssl rand -base64 18 | tr -dc 'a-zA-Z0-9' | head -c 20)
  RAND_JWT_SECRET=$(openssl rand -hex 32)

  sed -i "s|your-server-domain-or-ip.com|${PUBLIC_IP}|g" .env
  sed -i "s|generate_a_strong_password_here_min_16_chars|${RAND_DB_PASS}|g" .env
  sed -i "s|generate_a_64_character_hex_string_for_secure_jwt_tokens|${RAND_JWT_SECRET}|g" .env

  echo "✅ Generated .env with Public IP: ${PUBLIC_IP}"
else
  echo "ℹ️  Existing .env detected, preserving existing credentials."
fi

# 6. Launch Docker Compose Production Stack
echo "🚀 Building and starting containers (PostgreSQL + PostGIS, ML, Spring Boot, Caddy HTTPS)..."
docker compose -f docker-compose.prod.yml down || true
docker compose -f docker-compose.prod.yml up -d --build

echo "⏳ Waiting for backend healthcheck to turn healthy (up to 45s)..."
for i in {1..15}; do
  if docker compose -f docker-compose.prod.yml ps | grep -q "healthy"; then
    echo "✅ Containers are healthy!"
    break
  fi
  sleep 3
done

# 7. Print Summary
PUBLIC_IP=$(curl -s ifconfig.me || echo "your-server-ip")
echo ""
echo "============================================================"
echo "🎉 SafePath AI Server is LIVE and running 24/7!"
echo "============================================================"
echo "🌐 API Base URL:       https://${PUBLIC_IP}/api"
echo "📘 Swagger Docs:       https://${PUBLIC_IP}/swagger-ui/index.html"
echo "❤️ Health Check:       https://${PUBLIC_IP}/health"
echo "🗄️ Database:           PostgreSQL 15 + PostGIS (Persistent Volume)"
echo "🤖 ML Engine:          FastAPI XGBoost (Internal Bridge Network)"
echo "🔒 HTTPS:              Caddy Automatic Reverse Proxy"
echo "============================================================"
echo ""
echo "📱 To connect your mobile app:"
echo "1. Open the SafePath AI app on your phone."
echo "2. On the Login screen, tap '⚙️ Server Settings'."
echo "3. Enter: https://${PUBLIC_IP}/api"
echo "4. Tap 'Test Connection' and then 'Save & Use URL'."
echo "============================================================"
