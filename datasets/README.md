# SafePath AI — Datasets

> **IMPORTANT:** This directory is for dataset documentation only.
> Raw datasets are NOT included in the Git repository due to:
> - File size (typically large)
> - Licensing restrictions
> - Privacy considerations

---

## Directory Structure

```
datasets/
├── raw/          Original, unmodified datasets (not in Git)
├── processed/    Processed/feature-engineered data (not in Git)
└── README.md     This file
```

---

## Currently Planned Datasets

### Crime Data

| Dataset | Source | Level | Coordinates | Status |
|---------|--------|-------|-------------|--------|
| NCRB Annual Crime Statistics | data.gov.in or NCRB website | District | No | Planned |
| State police FIR data (if available) | State government open data | Locality/Address | Sometimes | Planned |

> **Note:** The precision of each dataset will be documented here and stored in the `location_precision` field.
> District-level data will NOT be used for street-level ML features.

### Safe Zones

| Dataset | Source | Type | Status |
|---------|--------|------|--------|
| OSM police stations | Overpass API | Point coordinates | Planned |
| OSM hospitals | Overpass API | Point coordinates | Planned |
| OSM pharmacies | Overpass API | Point coordinates | Planned |

### Road Network

| Dataset | Source | Type | Status |
|---------|--------|------|--------|
| OSM road network | Overpass API / osmnx | Nodes + edges | Planned |

### Weather (real-time, not stored as dataset)
- Open-Meteo API — fetched at route request time

---

## Data Processing Scripts

Data processing scripts are in `scripts/`. See that directory for:
- Ingestion scripts (import raw data to database)
- Geocoding scripts (for address-level data)
- Feature engineering scripts
- Model training scripts

---

## How to Obtain Datasets

### NCRB Data
1. Visit https://ncrb.gov.in/
2. Download "Crime in India" annual report
3. Save raw files to `datasets/raw/ncrb/`
4. Run `scripts/ingest_crime_data.py`

### OSM Data (Police Stations, Hospitals, etc.)
- Run `scripts/fetch_osm_safe_zones.py`
- This queries the Overpass API for the target city
- Results are ingested directly into the database

### Road Network
- Run `scripts/fetch_osm_roads.py`
- Uses Overpass API for the target city bounding box

---

## Data Quality Notes

See `docs/data-quality.md` for the complete data quality rules.

**Key rules:**
- Every crime record must have a `location_precision` label
- Do NOT invent coordinates for district-level data
- All geocoded records preserve the original address
- Only records with `EXACT_COORDINATE` or `ADDRESS_GEOCODED` precision are used for KDE features

---

## Processed Data

The `processed/` directory contains intermediate files produced by processing scripts:
- Cleaned crime data (CSV or Parquet)
- Feature-engineered training data
- Train/validation/test splits (labeled with dates)

These files are not in Git. Run the processing scripts to regenerate them.

---

*SafePath AI — datasets/README.md — 2026-10-08*
