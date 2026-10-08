# SafePath AI

> **An Intelligent AI-Powered Women Safety Route Planner**
> Final-Year Software Engineering Project

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.11+-blue)](https://python.org)
[![React Native](https://img.shields.io/badge/React%20Native-Expo-black)](https://expo.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-PostGIS-316192)](https://postgis.net)

---

## Table of Contents

- [Project Overview](#project-overview)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Environment Setup](#environment-setup)
- [Database Setup](#database-setup)
- [Backend Setup](#backend-setup)
- [ML Service Setup](#ml-service-setup)
- [Mobile App Setup](#mobile-app-setup)
- [Docker Setup](#docker-setup)
- [Running the Project](#running-the-project)
- [Running Tests](#running-tests)
- [Datasets](#datasets)
- [Data Quality & Limitations](#data-quality--limitations)
- [Academic Contributions](#academic-contributions)
- [Build Instructions (Android APK)](#build-instructions-android-apk)
- [Documentation](#documentation)
- [Known Issues](#known-issues)
- [License](#license)

---

## Project Overview

**SafePath AI** recommends safer walking routes for women by combining:

- Historical crime data with spatial and temporal analysis
- Machine learning risk prediction (Random Forest + XGBoost)
- Safety-aware A\* routing algorithm
- Real-time contextual factors (weather, time-of-day, community reports)
- Explainable AI (SHAP) for transparent route explanations
- Community-contributed safety reports
- Nearby safe zones (police stations, hospitals, pharmacies)
- Emergency/SOS functionality

The system provides **three route options**:
| Mode | Priority |
|------|----------|
| **FASTEST** | Travel time |
| **BALANCED** | Balance of time and safety |
| **SAFEST** | Safety (minimized risk) |

> **Academic Note:** This project is a serious final-year software engineering project built on real data principles. It explicitly documents data limitations, model assumptions, and implementation choices. No data is fabricated. No metrics are invented.

---

## Key Features

### Route Planning
- Three-mode route comparison (Fastest / Balanced / Safest)
- Safety-aware A\* graph search
- Road-segment risk prediction via ML
- SHAP-based safety explanations

### Safety Intelligence
- Crime data integration with location precision tracking
- Spatial/temporal feature engineering
- KDE-based crime density features
- Time-of-day and day-of-week risk patterns
- Weather context integration (Open-Meteo)

### Community & Social
- Community safety reports (harassment, poor lighting, etc.)
- Verified report system (PENDING → VERIFIED | REJECTED)
- Nearby safe zones on map

### Emergency
- SOS interface with current location
- Emergency contact management
- Nearby police stations / hospitals

### User
- Registration & JWT authentication
- Personalized route preferences (Standard / Solo Traveller / Tourist / Elderly)
- Profile management

---

## System Architecture

```
React Native (Expo)          — Mobile UI, Maps, User Interaction
       |
       | HTTPS REST API
       v
Spring Boot Backend          — Auth, Routing, Reports, Safe Zones, A* Engine
       |
       +---------------------------+
       |                           |
       v                           v
PostgreSQL + PostGIS           FastAPI ML Service
(Spatial Data, Crime,          (Risk Prediction, SHAP,
 Road Segments, Users)          Feature Preprocessing)
                                   |
                                   v
                            XGBoost / Random Forest
```

See [`docs/architecture.md`](docs/architecture.md) for full detail.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Mobile | React Native, Expo, TypeScript, Expo Router |
| Maps | React Native Maps, OpenStreetMap, Nominatim |
| Backend | Java 21, Spring Boot 3, Maven |
| Security | Spring Security, JWT, BCrypt |
| Database | PostgreSQL 15+, PostGIS 3+, Flyway |
| ML | Python 3.11, FastAPI, XGBoost, scikit-learn, SHAP |
| Geospatial | GeoPandas, Shapely, PyProj, NetworkX |
| Weather | Open-Meteo (free, no key required) |
| DevOps | Docker, Docker Compose, GitHub Actions |

---

## Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Java JDK | 21 | OpenJDK recommended |
| Maven | 3.9+ | Or use the bundled wrapper |
| Python | 3.11+ | For ML service and scripts |
| Node.js | 18+ LTS | For mobile app |
| npm | 9+ | Or yarn |
| Expo CLI | Latest | `npm install -g expo-cli` |
| PostgreSQL | 15+ | With PostGIS 3+ extension |
| Docker | 24+ | Optional but recommended |
| Docker Compose | 2.x | Optional |
| Git | 2.x | |

---

## Project Structure

```
SafePath-AI/
├── mobile/               React Native Expo app
├── backend/              Spring Boot backend
├── ml-service/           FastAPI ML inference service
├── database/             Flyway SQL migrations
├── datasets/
│   ├── raw/              Original unmodified datasets
│   ├── processed/        Cleaned/feature-engineered data
│   └── README.md         Dataset documentation
├── models/               Trained model artifacts (excluded from Git)
├── scripts/              Data processing & training scripts
├── tests/                Integration/e2e tests
├── docker/               Docker configuration
├── docs/                 Project documentation
├── .github/workflows/    CI/CD workflows
├── .env.example          Environment variable template
├── .gitignore
├── docker-compose.yml
├── AGENTS.md             Development rules & constraints
└── README.md
```

---

## Environment Setup

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/safepath-ai.git
cd safepath-ai

# 2. Copy environment template
cp .env.example .env

# 3. Edit .env with your values
# NEVER commit .env to Git
```

See [`.env.example`](.env.example) for all required variables.

---

## Database Setup

```bash
# Using Docker (recommended)
docker-compose up -d postgres

# Manual setup
psql -U postgres -c "CREATE USER safepath_user WITH PASSWORD 'your_password';"
psql -U postgres -c "CREATE DATABASE safepath_db OWNER safepath_user;"
psql -U safepath_user -d safepath_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"
psql -U safepath_user -d safepath_db -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;"

# Flyway migrations run automatically on Spring Boot startup
```

See [`docs/database.md`](docs/database.md) for schema details.

---

## Backend Setup

```bash
cd backend

# Copy environment (or set variables in .env)
# Run
./mvnw spring-boot:run

# Or build JAR
./mvnw clean package -DskipTests
java -jar target/safepath-backend-*.jar
```

Backend runs on: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## ML Service Setup

```bash
cd ml-service

# Create virtual environment
python -m venv venv
source venv/bin/activate      # Linux/macOS
venv\Scripts\activate         # Windows

# Install dependencies
pip install -r requirements.txt

# Run service
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

ML Service runs on: `http://localhost:8001`  
API docs: `http://localhost:8001/docs`

---

## Mobile App Setup

```bash
cd mobile

# Install dependencies
npm install

# Start Expo development server
npx expo start

# Run on Android emulator
npx expo run:android

# Run on physical device — scan QR code with Expo Go
```

---

## Docker Setup

```bash
# Start all services
docker-compose up -d

# Start specific services
docker-compose up -d postgres
docker-compose up -d backend
docker-compose up -d ml-service

# View logs
docker-compose logs -f backend

# Stop all
docker-compose down
```

---

## Running the Project

### Development (all services)

```bash
# Terminal 1: Database
docker-compose up -d postgres

# Terminal 2: ML Service
cd ml-service && uvicorn app.main:app --port 8001 --reload

# Terminal 3: Backend
cd backend && ./mvnw spring-boot:run

# Terminal 4: Mobile
cd mobile && npx expo start
```

---

## Running Tests

```bash
# Backend tests
cd backend && ./mvnw test

# ML service tests
cd ml-service && python -m pytest tests/ -v

# Mobile tests
cd mobile && npx jest
```

See [`docs/testing.md`](docs/testing.md) for test strategy.

---

## Datasets

Crime data and other datasets are NOT included in this repository due to size and licensing.

See [`datasets/README.md`](datasets/README.md) for:
- Dataset sources
- Data quality notes
- Location precision levels
- Processing instructions

> **Important:** Geographic precision is preserved. District-level data is NOT used as street-level data.

---

## Data Quality & Limitations

This project explicitly documents its limitations. See [`docs/limitations.md`](docs/limitations.md) and [`docs/data-quality.md`](docs/data-quality.md).

Key limitations:
- Crime datasets may lack precise GPS coordinates
- ML model accuracy depends on available training data
- Route risk is relative, not absolute
- Safety categories are application-defined, not universal standards
- Weather integration requires network connectivity

---

## Academic Contributions

This project's differentiation from a simple crime heatmap:

1. **Road-segment risk prediction** — risk assigned to individual road segments, not areas
2. **ML-based risk estimation** — Random Forest + XGBoost with proper evaluation
3. **Spatial/temporal feature engineering** — KDE, STKDE, temporal patterns
4. **Safety-aware route optimization** — A\* with configurable risk/time weights
5. **Fastest/Balanced/Safest comparison** — three distinct route modes
6. **Explainable AI** — SHAP-based transparency
7. **Dynamic contextual risk** — time, weather, recent activity
8. **Community reporting system** — with verification workflow
9. **Safe zone integration** — PostGIS spatial queries
10. **Personalization** — route preference profiles
11. **PostGIS spatial processing** — proper geospatial backend
12. **Geographic uncertainty handling** — explicit location precision taxonomy

---

## Build Instructions (Android APK)

> **Note:** An APK will be documented here once the build has actually succeeded.  
> Do not treat this section as confirming that a working APK exists.

```bash
cd mobile

# Build for Android (EAS Build — requires Expo account)
npx eas build --platform android --profile preview

# Local development build
npx expo run:android --variant release
```

See [`docs/deployment.md`](docs/deployment.md) for full instructions.

---

## Documentation

| Document | Purpose |
|----------|---------|
| [`docs/architecture.md`](docs/architecture.md) | System architecture |
| [`docs/database.md`](docs/database.md) | Database schema |
| [`docs/api.md`](docs/api.md) | API reference |
| [`docs/ml-pipeline.md`](docs/ml-pipeline.md) | ML pipeline |
| [`docs/routing.md`](docs/routing.md) | Routing algorithm |
| [`docs/data-quality.md`](docs/data-quality.md) | Data quality & precision |
| [`docs/security.md`](docs/security.md) | Security design |
| [`docs/testing.md`](docs/testing.md) | Testing strategy |
| [`docs/deployment.md`](docs/deployment.md) | Deployment guide |
| [`docs/limitations.md`](docs/limitations.md) | Known limitations |
| [`docs/research-notes.md`](docs/research-notes.md) | Research references |
| [`docs/PROJECT-CONTRACT.md`](docs/PROJECT-CONTRACT.md) | Non-negotiable rules |
| [`docs/MASTER-IMPLEMENTATION-PLAN.md`](docs/MASTER-IMPLEMENTATION-PLAN.md) | Full implementation plan |
| [`docs/development-progress.md`](docs/development-progress.md) | Project state |

---

## Known Issues

See [`docs/limitations.md`](docs/limitations.md) for current known issues and limitations.

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*SafePath AI — Final Year Project | Built with academic integrity*
