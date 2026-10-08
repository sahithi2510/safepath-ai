# SafePath AI — Research Notes

> **Last Updated:** 2026-10-08  
> **Purpose:** Relevant literature, algorithms, and approaches considered for this project.

---

## 1. Crime Prediction and Spatial Analysis

### Hot Spot Analysis
- Crime hot spots are geographic areas with higher crime concentration.
- Kernel Density Estimation (KDE) is a standard technique for visualizing crime density.
- Sherman et al. (1989) established that crime clusters at specific locations ("hot spots").

**Relevance:** KDE is used as a feature-generation technique in this project, NOT as a standalone crime prediction method.

**Limitation for this project:** KDE requires coordinate-precise crime data. District-level data cannot produce meaningful KDE.

### Spatio-Temporal Crime Analysis
- Crimes exhibit both spatial and temporal patterns.
- Time-of-day and day-of-week effects are well-documented.
- STKDE (Spatio-Temporal KDE) extends KDE to incorporate time as a dimension.

**Reference:** Nakaya, T., & Yano, K. (2010). Visualising crime clusters in a space-time cube: An exploratory data-analytic approach using space-time kernel density estimation and scan statistics.

---

## 2. Safety-Aware Routing

### Women's Safety and Route Choice
- Research shows women make route choices that prioritize perceived safety, not just efficiency.
- Environmental factors: lighting, pedestrian presence, building density, isolation.
- Time-of-day significantly affects perceived risk.

**Reference:** Koskela, H., & Pain, R. (2000). Revisiting fear and place: Women's fear of attack and the built environment. *Geoforum*.

### Route Optimization with Risk
- The travelling safety problem can be framed as a weighted shortest path problem.
- A\* is appropriate when a good heuristic exists.

---

## 3. Machine Learning for Crime Risk

### Decision Tree Ensembles
- Random Forest and gradient boosting (XGBoost) are effective for tabular crime prediction.
- These models handle mixed feature types and missing values better than linear models.

**Reference:** Chen, T., & Guestrin, C. (2016). XGBoost: A scalable tree boosting system. *KDD*.

### Explainability
- SHAP (Lundberg & Lee, 2017) provides consistent feature attribution.
- SHAP values sum to the prediction, making them interpretable and auditable.

**Reference:** Lundberg, S. M., & Lee, S. I. (2017). A unified approach to interpreting model predictions. *NIPS*.

---

## 4. Geospatial Data Sources

### OpenStreetMap (OSM)
- Open, community-maintained geographic database.
- Contains roads, buildings, amenities, lighting information.
- Accessed via Nominatim (geocoding) and Overpass API (raw data queries).

### Open-Meteo
- Free, open-source weather API.
- No API key required.
- Provides historical and forecast weather data.

---

## 5. Indian Crime Data Considerations

### NCRB (National Crime Records Bureau)
- Published annually by the Ministry of Home Affairs, India.
- Data is typically at state/district level.
- Does not provide GPS coordinates for individual incidents.
- Provides crime type and count aggregates.

**Key challenge for this project:** NCRB data cannot directly support street-level ML features. District-level rates can be used as regional proxy features only.

### State Police Open Data
- Some Indian states publish FIR-level data with locality or police station information.
- Address-level data may be geocodable.
- Availability varies by state.

---

## 6. Related Projects / Prior Art

| Project | Approach | Difference from SafePath AI |
|---------|---------|---------------------------|
| Google Maps | Fastest route only | No safety dimension |
| SafeCity | Crime reporting platform | No routing |
| Safetipin | Safety rating platform | No ML-based route optimization |
| Generic crime heatmaps | Area-level visualization | No road-segment risk; no routing |

**SafePath AI's differentiation:** Combines road-segment ML risk prediction with safety-aware A\* routing, SHAP explainability, and community reporting in a unified mobile application.

---

## 7. Acknowledged Limitations vs. Literature

| Literature Claim | SafePath AI Status |
|----------------|-------------------|
| KDE requires precise coordinates | Respected — precision tracked per record |
| Crime reporting bias is significant | Acknowledged as limitation |
| Route weights need user validation | Acknowledged as future work |
| Safety is subjective | Acknowledged — categories are application-defined |

---

*SafePath AI — research-notes.md — 2026-10-08*
