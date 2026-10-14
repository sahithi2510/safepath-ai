# SafePath AI — Data Quality & Geographic Precision

> **Status:** Living document  
> **Last Updated:** 2026-10-08  
> **IMPORTANT:** This document defines the academic and scientific integrity rules for all data handling in this project.

---

## 1. Core Principle

> **"The precision of our data determines the precision of our claims."**

This project explicitly documents:
- What data we have
- What precision it is at
- What we can and cannot legitimately infer from it
- What limitations this creates

---

## 2. Location Precision Taxonomy

Every crime record must be stored with a `location_precision` value from this taxonomy:

| Level | Code | Definition | Can be used for road-segment ML? |
|-------|------|-----------|----------------------------------|
| GPS-quality coordinates in source | `EXACT_COORDINATE` | Precise lat/lon from the dataset | ✅ Yes, with caution |
| Real address, geocoded | `ADDRESS_GEOCODED` | Has street address; geocoded via Nominatim; original address preserved | ✅ Conditionally |
| Locality only | `LOCALITY` | Neighbourhood/locality known; no address | ⚠️ Only for area-level features |
| Police station only | `POLICE_STATION` | Only associated police station recorded | ❌ No — too imprecise |
| District only | `DISTRICT` | Only district recorded | ❌ No — far too imprecise |
| No location info | `UNKNOWN` | No usable geographic data | ❌ No |

---

## 3. Indian Crime Data — Known Challenges

Indian publicly available crime datasets (e.g., NCRB reports, state police data) typically have:

| Issue | Impact |
|-------|--------|
| District-level aggregates only | Cannot assign to road segments |
| Police station attribution | Approximate location; covers large area |
| No coordinates in raw data | Cannot use for spatial ML without geocoding |
| Date ranges, not timestamps | Cannot compute precise temporal patterns |
| Incomplete subtypes | Reduces feature richness |
| Annual aggregates | Cannot compute recent crime features |

### What This Means for the ML Model

> **IMPORTANT:** If the primary crime dataset only provides district-level data, the following are not legitimate:
> - Street-level crime density features
> - KDE computed from fabricated coordinates
> - Claims that the model was trained on precise spatial crime data

> **What IS legitimate:**
> - District-level crime rate as a regional risk proxy
> - Temporal patterns (time of day, day of week) if timestamps exist
> - Severity as a feature if severity is recorded
> - Spatial features from other sources (lighting, emergency proximity)

---

## 4. Geocoding Rules

If a crime record contains a real street address, it may be geocoded:

1. Use **Nominatim** (OpenStreetMap geocoding service).
2. Preserve the **original address** in `original_address`.
3. Store `location_precision = 'ADDRESS_GEOCODED'`.
4. Store `location_confidence` based on geocoding match quality.
5. Do **not** silently accept low-confidence geocoding results.
6. Log all geocoding operations.

**Never:**
- Invent an address for a district-level record
- Assign a police station's coordinates to a crime record
- Claim a geocoded result is GPS-precise

---

## 5. KDE / Spatial Density Rules

Kernel Density Estimation (KDE) may be used as a feature-engineering technique **only** when:

- Crime records have `EXACT_COORDINATE` or `ADDRESS_GEOCODED` precision
- The KDE is documented as a **feature proxy**, not as exact crime location
- The bandwidth selection is documented and justified

**KDE is NOT:**
- A method to create precision from imprecise data
- A justification for upgrading district data to street data
- A replacement for actual coordinates

---

## 6. ML Feature Quality Tiers

| Feature | Quality | Source |
|---------|---------|--------|
| crime_density_500m (KDE) | Only valid if enough `EXACT_COORDINATE` records exist | crime_incidents |
| severity_weighted_density | Same as above | crime_incidents |
| recent_crime_density | Requires timestamps — not just annual counts | crime_incidents |
| lighting_score | Proxy from OSM `lit` tag — approximate | road_segments |
| activity_proxy | Estimated — must be documented as proxy | Multiple sources |
| nearest_police_m | Valid if safe_zones from OSM | safe_zones |
| nearest_hospital_m | Valid if safe_zones from OSM | safe_zones |
| weather_* | Valid from Open-Meteo | weather_observations |
| verified_report_count | Valid from verified community reports | community_reports |

---

## 7. Demo Data Policy

During UI development, synthetic/demo data may be used. Rules:

1. All demo data must be clearly labelled `DEMO DATA` in:
   - UI elements
   - API responses (include `"demo": true` field)
   - Code comments
   - Documentation

2. Demo data must be **isolated** from production data flows:
   - Separate demo data files
   - `DEMO_MODE` environment flag
   - Demo data never written to production database

3. Do NOT present demo data as real data in:
   - API responses without labelling
   - UI without labelling
   - Academic submissions

---

## 8. Model Training Data Audit

When the model is actually trained, document:

| Field | To Document |
|-------|------------|
| Dataset name | e.g., "NCRB Annual Report 2022" |
| Dataset source | URL or reference |
| Records ingested | Count |
| Records with EXACT_COORDINATE | Count |
| Records with ADDRESS_GEOCODED | Count |
| Records with DISTRICT only | Count |
| Records used for ML training | Count (only precise records) |
| Records excluded | Count and reason |
| Features available | List |
| Features actually used | List |
| Training period | Date range |
| Spatial coverage | Cities/states covered |
| Known biases | Documented |

> This table must be filled in `docs/ml-pipeline.md` and `docs/final-implementation-summary.md` **only after actual training**.

---

## 9. Limitations to Document

- [ ] Dataset spatial precision (FACT or ASSUMPTION)
- [ ] Whether KDE features are valid for the available data
- [ ] Whether temporal features are available
- [ ] Coverage gaps (cities, time periods)
- [ ] Potential reporting bias (crimes near police stations more likely to be recorded)
- [ ] Proxy feature accuracy (lighting from OSM is incomplete)

---

## 10. Community Reports & ML Ground Truth Isolation

Community safety reports represent subjective, crowdsourced citizen submissions rather than validated official crime incident records.

**Integrity Mandates:**
1. **Separation from Crime Ingestion:** Unverified community reports (`PENDING`) must **never** be merged into the official crime dataset or used as ground-truth labels for ML model training.
2. **Prohibition of Direct Score Modification:** An unverified report must not directly lower a road segment's safety score or artificially elevate edge costs in the routing engine.
3. **Location Precision Taxonomy:**
   - Reports submitted with explicit, confirmed coordinates are stored as `location_precision = EXACT_COORDINATE`.
   - Reports submitted without coordinates are recorded as `location_precision = UNKNOWN`. The system strictly prohibits automated reverse-geocoding, IP-based approximation, or coordinate invention.
4. **Public Query Filtering:** Public safety endpoints (`GET /api/reports/nearby`) return **only** records with `status = 'VERIFIED'`. Unverified submissions remain visible only to the original submitter and authorized moderation staff.

---

*SafePath AI — data-quality.md — Updated 2026-10-14*

