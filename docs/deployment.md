# SafePath AI — Deployment Guide

> **Last Updated:** 2026-10-08

---

## 1. Local Development (Docker Compose)

### Prerequisites
- Docker Desktop (Windows/macOS) or Docker Engine (Linux)
- Docker Compose v2+
- 4GB RAM minimum available

### Steps

```bash
# 1. Clone repository
git clone https://github.com/YOUR_USERNAME/safepath-ai.git
cd safepath-ai

# 2. Configure environment
cp .env.example .env
# Edit .env — change all CHANGE_ME values

# 3. Start all services
docker-compose up -d

# 4. Verify services are running
docker-compose ps
docker-compose logs backend

# 5. Access services
# Backend API:  http://localhost:8080/api
# Swagger UI:   http://localhost:8080/swagger-ui.html
# ML Service:   http://localhost:8001/docs
# Database:     localhost:5432
```

---

## 2. Manual Development Setup

### Database

```bash
# Requires PostgreSQL 15+ with PostGIS 3+
psql -U postgres
CREATE USER safepath_user WITH PASSWORD 'your_secure_password';
CREATE DATABASE safepath_db OWNER safepath_user;
\c safepath_db
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### Backend

```bash
cd backend

# Set environment variables (or create .env file read by IDE)
export POSTGRES_HOST=localhost
export POSTGRES_DB=safepath_db
export POSTGRES_USER=safepath_user
export POSTGRES_PASSWORD=your_secure_password
export JWT_SECRET=your_long_random_secret

# Run (Flyway migrations run automatically)
./mvnw spring-boot:run
```

### ML Service

```bash
cd ml-service

# Create and activate virtual environment
python -m venv venv
venv\Scripts\activate     # Windows
source venv/bin/activate  # Linux/macOS

# Install dependencies
pip install -r requirements.txt

# Run
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### Mobile

```bash
cd mobile

# Install dependencies
npm install

# Configure environment
# Edit app.config.ts or .env.local with backend URL

# Start development server
npx expo start

# For Android: press 'a' in terminal or scan QR code with Expo Go
```

---

## 3. Android APK Build

> **Note:** Do not treat this section as confirming a working APK exists.
> Update when build actually succeeds.

### Development Build (recommended for testing)

```bash
cd mobile

# Install EAS CLI
npm install -g eas-cli

# Login to Expo account (required for EAS)
eas login

# Configure EAS (first time)
eas build:configure

# Development build (can install on physical device)
eas build --platform android --profile development

# Preview build (release-like, no Expo Go required)
eas build --platform android --profile preview
```

### Local Expo Build (without Expo account)

```bash
cd mobile

# Requires Android SDK and Java 17+
npx expo run:android --variant release
```

### Build Output Location

After successful EAS build: download APK from Expo dashboard or from build URL provided in terminal.

---

## 4. Production Considerations

> **Note:** Production deployment is beyond the scope of the initial project.
> Document here for completeness and future reference.

Minimum production checklist:
- [ ] HTTPS configured with valid TLS certificate
- [ ] JWT secret is strong (256+ bits random)
- [ ] Database password is strong
- [ ] CORS configured for production domain only
- [ ] ML service not exposed publicly
- [ ] Logging configured appropriately (no sensitive data)
- [ ] Backup strategy for PostgreSQL
- [ ] Environment variables injected by deployment system (not .env file)

---

## 5. Environment Variables Reference

See `.env.example` for the complete list.
See `docs/security.md` for security requirements on secret values.

---

*SafePath AI — deployment.md — 2026-10-08*
