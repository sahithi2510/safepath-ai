# SafePath AI — Known Limitations

> **Last Updated:** 2026-10-08  
> **Purpose:** Honest documentation of known limitations.  
> This document should be cited in any academic submission.

---

## 1. Data Limitations

### 1.1 Crime Data Spatial Precision

**Limitation:** Publicly available Indian crime datasets (NCRB reports, state police data) typically provide data at district level or police station level — not at street or coordinate level.

**Impact:** Road-segment-level crime density features (KDE) are only valid if sufficient `EXACT_COORDINATE` or `ADDRESS_GEOCODED` records exist. If the primary dataset is district-level only, spatial ML features cannot be legitimately computed.

**Mitigation:** Location precision is tracked per record (`location_precision` field). The system only uses records with appropriate precision for each feature type.

**Type:** FACT

---

### 1.2 Crime Data Temporal Precision

**Limitation:** Many Indian crime datasets provide annual counts, not timestamped individual incidents. Without timestamps, temporal features (time-of-day risk patterns, recent crime activity) cannot be computed.

**Impact:** Temporal ML features may be unavailable or unreliable.

**Type:** FACT

---

### 1.3 Reporting Bias

**Limitation:** Crime data reflects reported crimes, not actual crimes. Areas near police stations may have higher reported crime simply due to proximity, not higher actual danger. Affluent areas may have lower reported crime due to social factors.

**Impact:** ML model may inherit these biases.

**Mitigation:** Documented here and in the dissertation. Cannot be fully corrected without unbiased crime data.

**Type:** FACT

---

### 1.4 Coverage Gaps

**Limitation:** OSM data quality varies by city and neighbourhood. Road segments, lighting data, and safe zone locations may be incomplete.

**Type:** FACT

---

## 2. ML Limitations

### 2.1 Relative, Not Absolute Risk

**Limitation:** The system predicts relative road-segment risk — not absolute probability of crime. A "high risk" score means the segment is predicted riskier than average segments, not that a crime is likely to occur.

**Impact:** The system should not be presented as a crime prediction system.

**Type:** FACT

---

### 2.2 Model Training Data

**Limitation:** Model metrics are not reported here because the model has not yet been trained at the time of this writing. Metrics will be documented in `final-implementation-summary.md` after actual training.

**Type:** FACT — honesty constraint

---

### 2.3 Safety Score Thresholds

**Limitation:** The boundaries between safety categories (Very Unsafe, Unsafe, Moderate, Safe, Very Safe) are application-defined implementation choices, not universal scientific standards.

**Impact:** Different thresholds would produce different category labels for the same risk score.

**Type:** IMPLEMENTATION CHOICE

---

### 2.4 Feature Proxies

**Limitation:** Several features are proxies:
- Lighting score from OSM `lit` tag (incomplete OSM data)
- Activity proxy from road type and time (no direct measurement)
- Missing lighting defaults to 0.5 (moderate) — an assumption

**Type:** ASSUMPTION (documented)

---

### 2.5 Geographic Generalization

**Limitation:** The model is trained on data from specific cities/areas. Risk predictions in other areas (not in training data) may be less accurate.

**Type:** LIMITATION

---

## 3. Routing Limitations

### 3.1 Static Road Graph

**Limitation:** The road graph is loaded at startup and is static. Road closures, new roads, and construction are not reflected without a restart and data refresh.

**Type:** IMPLEMENTATION CHOICE — acceptable for project scope

---

### 3.2 No Real-Time Traffic

**Limitation:** Walking speed is estimated from distance, not measured. Actual crowd conditions, road closures, or real-time pedestrian density are not considered.

**Type:** LIMITATION

---

### 3.3 Route Mode Weights

**Limitation:** The A\* weight values for FASTEST, BALANCED, and SAFEST modes are heuristically chosen implementation decisions. They have not been empirically validated through user studies.

**Type:** IMPLEMENTATION CHOICE

---

### 3.4 Walking Only

**Limitation:** The current implementation supports walking navigation only.

**Type:** IMPLEMENTATION CHOICE — by design for project scope

---

## 4. System Limitations

### 4.1 Weather Integration

**Limitation:** Weather affects routing context but is an optional dependency. If Open-Meteo is unavailable, the system continues without weather features.

**Type:** IMPLEMENTATION CHOICE — design decision

---

### 4.2 Community Reports Influence

**Limitation:** Unverified community reports have no direct influence on safety scores. Only verified reports contribute, meaning new hazards reported by users may not immediately affect route recommendations.

**Rationale:** Preventing manipulation of safety scores by single unverified reports.

**Type:** IMPLEMENTATION CHOICE — with documented rationale

---

### 4.3 No Real Emergency Service Integration

**Limitation:** The SOS feature logs the event and provides contact information. It does NOT automatically contact emergency services (police, ambulance). This would require agreements with relevant authorities.

**Type:** LIMITATION

---

### 4.4 Android Only (Initial Release)

**Limitation:** The initial build targets Android only. iOS requires additional configuration and an Apple Developer account.

**Type:** IMPLEMENTATION CHOICE

---

## 5. Academic Limitations

### 5.1 Ground Truth

**Limitation:** There is no reliable ground truth for "actual safety" of road segments. The ML target is constructed from crime data, which has the reporting biases documented above. The model predicts relative risk based on available data, not objective safety.

**Type:** FUNDAMENTAL LIMITATION of the problem domain

---

### 5.2 User Studies

**Limitation:** The route preference weights have not been validated through user studies. Whether users find the SAFEST route genuinely safer is not empirically tested in this project.

**Type:** FUTURE WORK

---

*SafePath AI — limitations.md — 2026-10-08*
