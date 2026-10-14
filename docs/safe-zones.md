# SafePath AI — Verified Safe Zones Architecture & Provenance

> **Milestone 22 Deliverable**  
> **Status:** Implemented and Tested  
> **Author:** SafePath AI Engineering Team  
> **Adherence:** `AGENTS.md` and `docs/PROJECT-CONTRACT.md` Data Integrity Rules

---

## 1. Overview & Purpose

Safe zones in **SafePath AI** represent publicly verifiable assistance facilities—such as police stations, hospitals, pharmacies, staffed transit interchanges, and public help desks—that pedestrians can seek out when experiencing safety concerns.

### Non-Negotiable Academic & Scientific Principles
1. **Zero Fabrication:** SafePath AI **never** invents facility records, geographic coordinates, phone numbers, addresses, operating hours, or verification flags.
2. **Existence vs. Operational State:** The verified physical existence of a police station or hospital does **not** guarantee that the facility is currently open, staffed, safe, or actively operational. SafePath AI never assumes or implies real-time availability without authoritative real-time verification.
3. **No Guarantee of Personal Safety:** A safe zone is a designated point of assistance, not an impenetrable refuge. All API responses and mobile interfaces present clear disclaimers directing users to official emergency services (112, 100, 911) during active crises.
4. **Honest Empty Sets:** If a spatial query yields no verified facilities in the database within the requested bounding box or proximity radius, the system returns an empty list (`[]`). It does not substitute nearest commercial businesses or generate synthetic approximations.

---

## 2. Safe Zone Data Model & Schema

The safe-zone schema resides in PostgreSQL with PostGIS geometric point indexing (EPSG:4326).

### 2.1 Table: `safe_zones`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `UUID` | PK, Default `uuid_generate_v4()` | Stable unique identifier |
| `name` | `VARCHAR(255)` | NOT NULL | Official name of the facility |
| `zone_type` | `VARCHAR(50)` | NOT NULL | Category enum |
| `verification_status` | `VARCHAR(20)` | NOT NULL, DEFAULT `'UNVERIFIED'` | Verification confidence level |
| `latitude` | `DOUBLE PRECISION` | NOT NULL, [-90.0, 90.0] | WGS84 Latitude |
| `longitude` | `DOUBLE PRECISION` | NOT NULL, [-180.0, 180.0] | WGS84 Longitude |
| `geometry` | `GEOMETRY(Point, 4326)` | NOT NULL | PostGIS spatial point |
| `address` | `TEXT` | NULLABLE | Physical address or locality |
| `phone_number` | `VARCHAR(20)` | NULLABLE | Landline / public telephone |
| `operating_hours` | `VARCHAR(255)` | NULLABLE | Hours of operation (e.g. "24/7", "09:00-21:00") |
| `is_24h` | `BOOLEAN` | NULLABLE | True if confirmed 24/7 service |
| `osm_node_id` | `BIGINT` | NULLABLE | Source OpenStreetMap identifier |
| `source` | `VARCHAR(100)` | DEFAULT `'osm'` | Provenance source (e.g., `'osm'`, `'gov_portal'`) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Audit creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Audit update timestamp |

### 2.2 Category Taxonomy (`zone_type`)

| Category Code | Display Label | Pin Icon | Hex Color | Description |
|---|---|---|---|---|
| `POLICE_STATION` | Police Station | 👮 | `#1D68BD` | Law enforcement station or post |
| `HOSPITAL` | Hospital / Clinic | 🏥 | `#E02424` | 24-hour or emergency medical center |
| `PHARMACY` | Pharmacy | 💊 | `#057A55` | Licensed medical dispensary |
| `PUBLIC_TRANSPORT` | Transit Hub | 🚏 | `#7E3AF2` | Staffed train, metro, or bus terminal |
| `OTHER` | Public Assistance | 🏢 | `#D97706` | Municipal desk or designated help kiosk |

### 2.3 Verification Taxonomy (`verification_status`)

| Status Code | Description | UI Display |
|---|---|---|
| `VERIFIED` | Confirmed by official government registry, municipal audit, or emergency services roster. | Green Badge (`#057A55`) |
| `UNVERIFIED` | Sourced from crowdsourced open spatial data (OpenStreetMap) without individual ground audit. | Amber Badge (`#D97706`) |
| `UNAVAILABLE` | Facility reported temporarily closed, demolished, or lacking verified status. | Gray Badge (`#6B7280`) |

### 2.4 Flyway Migrations
- `V7__create_safe_zones.sql`: Initial table and spatial GIST index on `geometry`.
- `V14__safe_zone_verification_status.sql`: Added `verification_status` column and compound index on `(zone_type, verification_status)`.

---

## 3. Backend REST API Specification

All safe-zone retrieval endpoints are public (no authentication required) to ensure immediate pedestrian safety access without login barriers.

### 3.1 `GET /api/safe-zones` (Viewport or Proximity Search)
Supports two operational modes:
1. **Viewport Bounding Box:** When `minLat`, `minLon`, `maxLat`, `maxLon` are provided.
2. **Center Point Proximity:** When `lat`, `lon` are provided.

#### Viewport Parameters
- `minLat` (double, -90 to 90): Southern latitude boundary.
- `minLon` (double, -180 to 180): Western longitude boundary.
- `maxLat` (double, -90 to 90): Northern latitude boundary.
- `maxLon` (double, -180 to 180): Eastern longitude boundary.
- `types` (optional, comma-separated list): Filter by `zone_type` (e.g. `POLICE_STATION,HOSPITAL`).
- `limit` (optional, default 50, clamped max 100).

#### Validation Rules
- Coordinate range validation: [-90, 90] for latitudes, [-180, 180] for longitudes.
- `minLat <= maxLat` and `minLon <= maxLon`.
- Max Viewport Span: `(maxLat - minLat) <= 0.15` degrees and `(maxLon - minLon) <= 0.15` degrees (~16 km). Rejects massive country-wide scans with `400 Bad Request`.

### 3.2 `GET /api/safe-zones/nearby` (Dedicated Proximity Lookup)
- `lat` (double, required): Search center latitude.
- `lon` (double, required): Search center longitude.
- `radiusMeters` (double, default 1000, clamped max 10000).
- `types` (optional): Category filter.
- `limit` (optional, default 10, clamped max 50).

**Distance Calculation:** Proximity search computes great-circle distance in meters using the Haversine equation and includes `distanceMeters` in the response DTO.

### 3.3 `GET /api/safe-zones/{id}` (Individual Facility Lookup)
- `id` (UUID, required): Stable record identifier.
- Returns `200 OK` with full facility attributes.
- Returns `404 Not Found` with structured JSON error if not found.

### 3.4 Public API Privacy & Protection
- No private user records, reporters, or personal identity information are associated with safe zone entities.
- Error handlers sanitize exceptions to ensure no database schema details or stack traces are leaked.

---

## 4. Mobile Integration & User Experience

### 4.1 Interactive Map Integration (`mobile/app/(tabs)/index.tsx`)
- **Layer Toggle:** An accessible switch (`🛡️ Safe Zones: ON/OFF`) allows users to toggle the marker overlay at any time.
- **Visual Markers:** Color-coded pin markers matching category colors with icons (👮, 🏥, 💊, 🚏, 🏢).
- **Interactive Detail Card:** Tapping a facility marker opens a bottom sheet detailing:
  - Facility name and category.
  - Verification badge (`VERIFIED`, `UNVERIFIED`, `UNAVAILABLE`).
  - Physical address and operating hours (displayed only when genuinely documented).
  - Provenance attribution (`OpenStreetMap` / Node ID).
  - Standard emergency disclaimer: *"Listing does not guarantee personal safety or active staffing."*
  - **"Directions Here" Action:** One-tap navigation passes facility coordinates (`endLat`, `endLon`, `endName`) directly into the route comparison screen (`/(tabs)/routes`).
- **Coexistence:** Safe zone markers coexist seamlessly with route polylines and the safety heatmap road segment overlay.
- **Collapsible Legend:** The map's legend panel includes all safe-zone category icons and verification status descriptions.

### 4.2 Safe Zones Directory & Finder (`mobile/app/safe-zones.tsx`)
- Dedicated full-page facility browser with GPS location auto-fill or Bangalore demo presets.
- Category filter chips (All, Police, Hospitals, Pharmacies, Transit).
- Radius selection (1 km, 2 km, 5 km, 10 km).
- Real-time distance indicators (e.g. `450 m away`, `2.4 km away`).
- Direct navigation shortcut to route planner.

---

## 5. Test Suite & Verification Results

| Suite | Component | Tests | Outcome |
|---|---|---|---|
| `SafeZoneServiceTest` | Unit tests for proximity, bbox, validation, clamping, ID lookup, disclaimers | 10 | ✅ 10 Passed |
| `SafeZoneControllerTest` | Unit tests for HTTP endpoints, delegation, and parameter validation | 6 | ✅ 6 Passed |
| Full Backend Suite (`mvn test`) | All backend controllers, services, security, routing, heatmap, reports, safe zones | 87 | ✅ 87 Passed |
| ML Service (`pytest`) | FastAPI risk prediction, batching, SHAP explainability | 15 | ✅ 15 Passed |
| Mobile Typecheck (`npx tsc --noEmit`) | TypeScript compilation across all screens, services, and components | - | ✅ 0 Errors |

### Physical Device Rendering Note
Physical device rendering was not performed in this automated environment; all mobile map markers, callouts, and route integrations were verified using Expo Router TypeScript verification and mock viewport datasets.
