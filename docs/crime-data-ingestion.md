# SafePath AI — Crime Data Ingestion Pipeline

> **Document Type:** Pipeline Specification & Operational Guide  
> **Status:** Implemented & Verified (Milestone 11)  
> **Authoritative Rules:** Governed strictly by `AGENTS.md` §2 (Data Integrity) and §3 (Architecture)

---

## 1. Overview & Objective

The SafePath AI Crime Data Ingestion Pipeline (`scripts/crime_pipeline/`) provides a modular, repeatable, and transaction-safe system to ingest, clean, normalize, validate, and persist crime incident records into the PostgreSQL/PostGIS `crime_incidents` table.

A central requirement of SafePath AI is **academic honesty and scientific validity**:
- **Zero Fabrication:** The pipeline **never** invents crime records, coordinates, addresses, or geocoding outcomes.
- **Precision Taxonomy:** Every record is assigned an explicit `location_precision` tag.
- **Coordinate Invariance:** Records with coarse location precision (`LOCALITY`, `POLICE_STATION`, `DISTRICT`, `UNKNOWN`) **never** receive synthetic or centroid coordinates; their point geometries remain strictly `NULL`. Only `EXACT_COORDINATE` and verified `ADDRESS_GEOCODED` records are eligible for street-level machine learning features.

---

## 2. Pipeline Architecture

```
scripts/
├── __init__.py
├── ingest_crime_data.py               # CLI entry point
├── crime_pipeline/
│   ├── __init__.py
│   ├── schemas.py                     # Data schemas, LocationPrecision enum, severity mappings
│   ├── cleaner.py                     # Normalization, temporal parsing, coordinate validation
│   ├── geocoder.py                    # Nominatim client, persistent cache, rate limiter, MockGeocoder
│   ├── db.py                          # PostgreSQL/PostGIS connection, duplicate handling, transactions
│   └── ingester.py                    # Batch orchestrator, error tracking, quality metrics
├── fixtures/
│   └── mock_crime_records.csv         # DEMO DATA / MOCK DATA verification fixture
└── tests/
    ├── __init__.py
    ├── test_cleaner.py                # Unit tests for cleaner, severity, precision invariants
    ├── test_geocoder.py               # Unit tests for geocoder caching, rate limits, confidence
    ├── test_db_and_dedup.py           # Unit tests for PostGIS parameters and batch deduplication
    └── test_ingestion_e2e.py          # End-to-end integration tests using mock fixture
```

---

## 3. Location Precision Taxonomy (`AGENTS.md` §2.3)

| Level | Code | Definition | Road-Segment ML Eligible? | Coordinates Populated? |
|-------|------|------------|---------------------------|------------------------|
| Exact GPS Coordinates | `EXACT_COORDINATE` | Lat/Lon supplied in original dataset | ✅ Yes (with validation) | Yes (`Point, 4326`) |
| Geocoded Street Address | `ADDRESS_GEOCODED` | Street address geocoded via Nominatim; original address preserved | ✅ Yes (confidence $\ge 0.35$) | Yes (`Point, 4326`) |
| Locality / Area Only | `LOCALITY` | Neighbourhood or locality known; no exact address | ⚠️ Area features only | ❌ Strictly `NULL` |
| Police Station Only | `POLICE_STATION` | Attributed only to a police station | ❌ No | ❌ Strictly `NULL` |
| District Level | `DISTRICT` | Attributed only to a revenue district | ❌ No | ❌ Strictly `NULL` |
| Unknown / Unusable | `UNKNOWN` | No usable geographic identifier | ❌ No | ❌ Strictly `NULL` |

---

## 4. Severity Weighting (Implementation Choice)

In accordance with `AGENTS.md` §4 & §11, severity weights are mapped on an academic 1–5 scale:

| Severity | Category | Typical Crime Types |
|:---:|---|---|
| **5** | **Critical / Extreme Danger** | Rape, sexual assault, homicide, murder, kidnapping, armed robbery |
| **4** | **High Danger** | Aggravated physical assault, stalking with violence, robbery, molestation |
| **3** | **Moderate Danger / Street Crimes** | Street harassment, eve teasing, chain snatching, intimidation, threats |
| **2** | **Property / Low Danger** | Theft, burglary, pickpocketing, vandalism, trespassing |
| **1** | **Minor / Nuisance** | Public nuisance, disorderly conduct, minor non-violent disputes |

*Note: If an explicit severity is provided in the source dataset (1–5), it is preserved. Unmatched crimes fall back to default severity `3` with a logged notice.*

---

## 5. Geocoding Service (`geocoder.py`)

Geocoding is supported for datasets with street addresses:
- **Provider:** OpenStreetMap Nominatim (Free, no paid API required).
- **Usage Policy Compliance:**
  - Enforces minimum 1.05s delay between live network requests (satisfies Nominatim 1 req/sec limit).
  - Supplies identifiable custom User-Agent (`SafePathAI-AcademicResearch/1.0`).
- **Persistent Caching:**
  - Uses a local SQLite cache (`.geocoding_cache.sqlite`) to eliminate redundant requests across runs.
- **Quality & Confidence Checks:**
  - Results with confidence $< 0.35$ are rejected to avoid coarse/vague points.
  - Failures and query provenance are logged into the record's `raw_source_data` audit field.
- **Deterministic Mocking:**
  - Includes `MockGeocoder` for offline development, CI/CD, and fast repeatable testing without network access.

---

## 6. Database Integration & Idempotency (`db.py`)

- **Schema Target:** `crime_incidents` table in PostGIS (Flyway migration `V4__create_crime_incidents.sql`).
- **Credential Safety:** Credentials read strictly from environment variables (`POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) or `.env`. No hardcoded credentials.
- **Spatial Geometry:** Uses `ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)` when coordinates are valid, and `NULL` otherwise.
- **Idempotency & Deduplication:**
  - Pre-queries existing `external_id` values for the specified dataset `source`.
  - In batch processing, detects duplicate external IDs within the file.
  - By default, existing records are safely skipped (`duplicate_skipped` count tracked).
  - If `--update` flag is passed, existing records are updated transactionally.
- **Transaction Safety:** Batch inserts run inside explicit database transactions, rolling back upon unhandled failure.

---

## 7. How to Ingest Genuine Datasets

### 7.1 Preparing a Real Dataset
1. Place the unmodified source file into `datasets/raw/` (e.g. `datasets/raw/ncrb_crime_2022.csv`).
2. Supported formats: `.csv`, `.json`, `.jsonl`.
3. Standard columns recognized (case-insensitive):
   - Identifier: `external_id`, `id`, `fir_number`, `case_id`
   - Crime: `crime_type`, `crime_category`, `crime_subtype`, `severity`
   - Time: `incident_datetime`, `incident_date`, `date`, `timestamp`
   - Location: `latitude`, `longitude`, `original_address`, `district`, `locality`, `police_station_name`, `city`, `state`

### 7.2 Running Ingestion Commands

```bash
# Test database connectivity
python scripts/ingest_crime_data.py --test-db

# Validate dataset without modifying database (Dry Run)
python scripts/ingest_crime_data.py \
    --file datasets/raw/ncrb_crime_2022.csv \
    --source ncrb_2022 \
    --dry-run

# Ingest dataset with database persistence
python scripts/ingest_crime_data.py \
    --file datasets/raw/delhi_police_fir.csv \
    --source delhi_fir_2023 \
    --batch-size 500

# Ingest dataset with address geocoding enabled
python scripts/ingest_crime_data.py \
    --file datasets/raw/street_incidents.csv \
    --source street_reports_2023 \
    --geocode \
    --batch-size 250
```

---

## 8. Verification & Test Suite

The pipeline is verified by a dedicated test suite in `scripts/tests/`:

```bash
# Run complete test suite
pytest scripts/tests/ -v
```

### Test Coverage Summary:
- `test_cleaner.py`:
  - Severity mapping (1–5 scale, explicit overrides, defaults).
  - Coordinate bounds checking ($-90 \le \text{lat} \le 90$, $-180 \le \text{lon} \le 180$, Null Island `(0,0)` filtered).
  - Date/time parsing (ISO 8601, YYYY-MM-DD, DD/MM/YYYY).
  - Precision enforcement (`EXACT_COORDINATE`, `LOCALITY`, `POLICE_STATION`, `DISTRICT`).
  - Pydantic schema validation raising error on coarse coordinates.
- `test_geocoder.py`:
  - SQLite persistent cache read/write.
  - MockGeocoder registration and fallback behavior.
  - Nominatim low-confidence match rejection.
- `test_db_and_dedup.py`:
  - PostgreSQL parameter mapping and JSONB formatting.
  - In-batch deduplication and dry-run count verification.
- `test_ingestion_e2e.py`:
  - End-to-end ingestion of `scripts/fixtures/mock_crime_records.csv` in dry-run mode.
  - Address geocoding and precision upgrade to `ADDRESS_GEOCODED`.

---

## 9. Known Limitations & Remaining Gaps

1. **Indian Public Crime Data Spatial Resolution:** Most publicly released Indian crime datasets (e.g., NCRB reports) aggregate to district or state levels. In accordance with `AGENTS.md` §2.2, these records are stored with `location_precision = 'DISTRICT'` and `NULL` coordinates. They cannot be used as training labels for road-segment safety scoring.
2. **Nominatim Free Rate Limit:** Public Nominatim limits queries to 1 request/second. Ingestion of thousands of un-geocoded addresses requires caching and time; for massive datasets, an offline local Nominatim Docker container is recommended.
3. **External ID Availability:** When datasets lack explicit FIR or record IDs, deduplication relies on composite fields (`source`, `incident_date`, `crime_type`, `original_address`).
