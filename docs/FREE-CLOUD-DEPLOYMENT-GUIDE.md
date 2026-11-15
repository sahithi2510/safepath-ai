# SafePath AI — 24/7 Free Cloud Deployment Guide

> **Authoritative Production Deployment Documentation**  
> **Target:** 24/7 High-Availability Cloud Server without requiring your local laptop  
> **Cost:** 100% Free Tier Resources Only

---

## Architecture Overview

```
                      Internet
                         │
                         ▼
        ┌───────────────────────────────────┐
        │   Caddy Reverse Proxy (HTTPS)     │  ◄── Automatic Let's Encrypt SSL
        │         Ports 80 & 443            │
        └─────────────────┬─────────────────┘
                          │ Internal Bridge Network
                          ▼
        ┌───────────────────────────────────┐
        │   Spring Boot Backend (:8080)     │  ◄── Auth, A* Routing, Reports, SafeZones
        └──────────────┬─────────────┬──────┘
                       │             │
                       ▼             ▼
       ┌─────────────────────┐  ┌─────────────────────┐
       │ PostgreSQL 15 +     │  │ FastAPI ML Service  │
       │ PostGIS 3.4 (:5432) │  │ (:8001 internal)   │
       │ Volume:             │  │ Model: XGBoost +   │
       │ postgres_data       │  │ TreeSHAP           │
       └─────────────────────┘  └─────────────────────┘
```

---

## Deployment Option 1: 100% Free 1-Click Cloud Deployment (Render Blueprint)

**Render** allows deploying web services and databases directly from GitHub on a 100% free tier with zero server maintenance.

### Step 1: Connect GitHub Repository
1. Go to **[Render.com](https://dashboard.render.com)** and sign up or log in using your GitHub account (`sahithi2510`).
2. Click **New +** in the top right and select **Blueprint**.
3. Select your repository: **`sahithi2510/safepath-ai`** (branch: `main`).

### Step 2: Automatic Provisioning via `render.yaml`
Render reads the `render.yaml` already committed in your repository and will automatically provision:
- **`safepath-db`**: Persistent PostgreSQL database.
- **`safepath-ml`**: Python 3.11 FastAPI service serving XGBoost and TreeSHAP models.
- **`safepath-backend`**: Java 21 Spring Boot core routing engine with automatic HTTPS!

### Step 3: Enable PostGIS Extension
Once the database deploys:
1. In the Render Dashboard, click on **`safepath-db`**.
2. Click **Connect** -> **Web Shell** (or connect via any PostgreSQL client like DBeaver or pgAdmin).
3. Run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
   ```
4. Render will complete Flyway migrations automatically upon backend launch.

### Step 4: Your Permanent Production URL
Render provides a permanent HTTPS domain:
```
https://safepath-backend.onrender.com/api
```
*(Your backend is now running 24/7 on the internet!)*

---

## Deployment Option 2: All-In-One Cloud VPS with Docker Compose & Caddy SSL

If you prefer running the full Docker stack on a virtual private server (VPS):
- **Oracle Cloud Always-Free Tier**: 4 OCPUs, 24 GB RAM (ARM) or 2 AMD micro instances — **Free Forever**.
- **DigitalOcean / Hetzner / AWS Free Tier**: 1-2 vCPU instances.

### Step 1: Launch VPS
1. Create an Ubuntu 22.04 or 24.04 LTS instance.
2. Ensure ingress ports **22 (SSH)**, **80 (HTTP)**, and **443 (HTTPS)** are open in your cloud provider's security list / firewall.

### Step 2: One-Command Automated Provisioning
SSH into your server and run:

```bash
# Clone the repository
git clone https://github.com/sahithi2510/safepath-ai.git
cd safepath-ai

# Execute the automated deployment script
chmod +x scripts/deploy/setup_server.sh
sudo ./scripts/deploy/setup_server.sh
```

### What the Script Automatically Does:
1. Installs Docker and the Docker Compose plugin.
2. Configures the UFW firewall (ports 22, 80, 443).
3. Generates a secure `.env` file with random 256-bit JWT secrets and database passwords using `openssl`.
4. Automatically detects your server's Public IP and configures Caddy.
5. Launches all 4 containers in detached mode:
   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   ```
6. Runs health checks until all services report **healthy**.

### Manual Docker Compose Commands (Reference)
If you want to manage the containers manually:
```bash
# Copy production env
cp .env.production.example .env

# Edit domain or IP
nano .env

# Start stack
docker compose -f docker-compose.prod.yml up -d --build

# View container status
docker compose -f docker-compose.prod.yml ps

# View live backend logs
docker compose -f docker-compose.prod.yml logs -f backend

# Stop stack (data remains safe in volume)
docker compose -f docker-compose.prod.yml down
```

---

## Database Persistence Guarantee

In `docker-compose.prod.yml`, PostgreSQL data is stored on a named volume:
```yaml
volumes:
  - postgres_data:/var/lib/postgresql/data
```
- **Survives Container Restarts**: Stopping containers (`docker compose down`) does **not** erase your database.
- **Survives Server Reboots**: If the VPS restarts, Docker automatically relaunches PostgreSQL with all user accounts, community reports, and road segments intact (`restart: always`).
- **Data Deletion Warning**: The only command that wipes data is `docker compose down -v` (with `-v` flag), which should never be used in production.

---

## Automatic SSL/HTTPS via Caddy Reverse Proxy

Caddy (`docker/caddy/Caddyfile`) acts as the front door for your cloud deployment:
1. **Automatic Certificates**: When you point a domain (e.g. `api.yourdomain.com`) to your server IP, Caddy automatically obtains and renews a free TLS/SSL certificate from Let's Encrypt.
2. **HTTP to HTTPS Redirection**: All traffic hitting port 80 is automatically upgraded to secure HTTPS port 443.
3. **Reverse Proxying**:
   - `https://your-server/api/*` ➔ Proxies directly to Spring Boot `backend:8080`.
   - `https://your-server/swagger-ui/*` ➔ Swagger API documentation.
   - `https://your-server/health` ➔ Direct container health checks.
4. **Isolated ML Service**: Port 8001 (FastAPI) is kept inside the internal Docker bridge network (`safepath-network`) and is **never** exposed to the public internet, preventing unauthorized scraping or interference.

---

## Connecting the Mobile App

Once your server is running online:

### Method A: In-App Dynamic Switching (Recommended)
1. Open the **SafePath AI** app on your phone.
2. On the **Login screen**, tap:
   `⚙️ Server Settings / Change API URL`
   *(Or if already logged in, go to **Profile** ➔ **`🌐 Server & Cloud API Settings`**)*.
3. Tap the **`☁️ Render Cloud`** preset (or type your server URL):
   ```
   https://safepath-backend.onrender.com/api
   ```
   *(or `https://your-vps-ip/api`)*.
4. Tap **Test Connection** (you should see green confirmation: `"Connection successful!"`).
5. Tap **Save & Use URL**.

### Method B: Default `.env` Build Configuration
If compiling a standalone Android APK:
1. In `mobile/.env`, set:
   ```env
   EXPO_PUBLIC_API_URL=https://safepath-backend.onrender.com/api
   ```
2. Build the app with Expo/EAS:
   ```bash
   cd mobile
   npx expo start
   ```
The app will now communicate directly with your 24/7 cloud server without your laptop needing to be on!
