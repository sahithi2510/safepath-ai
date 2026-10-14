# SafePath AI — Weather Integration Architecture & Provenance

> **Milestone 23 Deliverable**  
> **Status:** Implemented, Tested, and Verified  
> **Author:** SafePath AI Engineering Team  
> **Adherence:** `AGENTS.md` and `docs/PROJECT-CONTRACT.md` Data Integrity Rules

---

## 1. Overview & Purpose

Milestone 23 introduces genuine ambient weather context into **SafePath AI**. Environmental factors like rainfall, temperature, wind velocity, and atmospheric visibility influence walking conditions, visibility, and pedestrian comfort.

However, in accordance with the project's core research integrity charter, weather is treated strictly as **environmental context**, never as direct causation or proof that a street segment is safe or unsafe from crime.

---

## 2. Non-Negotiable Scientific & Data Integrity Principles

1. **Zero Fabrication:** SafePath AI **never** fabricates weather observations, temperatures, precipitation levels, visibility readings, or sensor data.
2. **Environmental Context vs. Crime Causation:** Weather conditions (such as light rain or overcast skies) do **not** constitute direct evidence of criminal activity or safety threats. Weather is presented as ambient context to assist travelers in journey planning.
3. **No Retraining or False Metric Claims:** Adding weather context does **not** validate synthetic ML models or improve empirical accuracy without rigorous evaluation on real, co-located temporal crime and sensor datasets. No fabricated accuracy improvements are claimed.
4. **Honest Missing-Data Policy:** If the external weather provider is unreachable, or if observations for a requested location are unavailable:
   - Missing fields remain explicitly `null` (or `available: false`).
   - Missing weather measurements are **never** silently converted to `0.0`.
   - The ML service continues using documented feature imputation routines.
5. **Observation vs. Forecast Separation:** Current observations (e.g., current temperature, current precipitation rate) are strictly distinguished from forecast estimations (e.g., hourly precipitation probability). Forecast values are never labeled as current observations.
6. **Fault-Tolerant Routing:** Failure of the weather provider or network timeouts **never** prevents route calculation. The `FASTEST` route mode computes independently without ML or weather dependencies, and `BALANCED` / `SAFEST` modes fall back gracefully to default environmental feature baselines.

---

## 3. External Weather Provider: Open-Meteo

SafePath AI integrates with **Open-Meteo** (open-meteo.com), an open-source, non-commercial-friendly weather API providing WMO-standardized observations and forecasts without requiring paid credentials or proprietary keys.

### 3.1 Endpoint & Parameters

- **Base URL:** `https://api.open-meteo.com/v1/forecast`
- **Query Parameters:**
  - `latitude`: WGS84 latitude [-90.0, 90.0]
  - `longitude`: WGS84 longitude [-180.0, 180.0]
  - `current`: `temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,is_day,visibility`
  - `hourly`: `precipitation_probability`
  - `forecast_days`: `1`

### 3.2 Unit Conventions

All measurements are exposed with explicit unit metadata:
- **Temperature:** Celsius (°C)
- **Precipitation:** Millimeters (mm)
- **Wind Speed:** Kilometers per hour (km/h)
- **Relative Humidity:** Percentage (%)
- **Visibility:** Converted from provider meters to kilometers (km)
- **Precipitation Probability:** Percentage (%)

### 3.3 WMO Weather Code Taxonomy

Weather conditions map to the standard World Meteorological Organization (WMO) Weather Interpretation Codes (WW):

| WMO Code | Meaning | Mobile Display Icon |
|---|---|---|
| `0` | Clear sky | ☀️ (Day) / 🌙 (Night) |
| `1`, `2` | Mainly clear / Partly cloudy | 🌤️ (Day) / ☁️ (Night) |
| `3` | Overcast | ☁️ |
| `45`, `48` | Fog / Depositing rime fog | 🌫️ |
| `51`, `53`, `55` | Light, moderate, dense drizzle | 🌦️ |
| `61`, `63` | Slight, moderate rain | 🌧️ |
| `65` | Heavy rain | ⛈️ |
| `71`, `73`, `75` | Slight, moderate, heavy snowfall | ❄️ |
| `80`, `81`, `82` | Rain showers (slight to violent) | 🌧️ / ⛈️ |
| `95`, `96`, `99` | Thunderstorm with/without hail | ⛈️ |

---

## 4. Backend Client & Service Architecture

### 4.1 Typed Client (`OpenMeteoClient`)

- Implemented in `com.safepath.backend.service.weather.OpenMeteoClientImpl`.
- Utilizes Spring Framework 6 / Boot 3 `RestClient` configured with:
  - Connection timeout: 3,000 ms (configurable via `safepath.weather.connect-timeout-ms`)
  - Read timeout: 4,000 ms (configurable via `safepath.weather.timeout-ms`)
- Strict coordinate validation rejecting inputs outside $[-90, 90]$ and $[-180, 180]$.
- Catches network timeouts (`ResourceAccessException`) and HTTP errors (`RestClientResponseException`), mapping them to typed `WeatherClientException`.

### 4.2 Spatial-Temporal Caching & Rate Protection

To prevent overloading external weather infrastructure and respect request quotas:
1. **Grid Quantization:** Coordinates are rounded to 2 decimal places (`%.2f, %.2f`), providing ~1.1 km spatial resolution matching Open-Meteo's atmospheric grid model.
2. **In-Memory TTL Cache:** A concurrent in-memory cache stores recent observations with a configurable 30-minute time-to-live (`safepath.weather.cache-ttl-minutes: 30`).
3. **Database Provenance & Fallback:** Live observations are recorded in PostgreSQL (`weather_observations`) with PostGIS spatial point geometry for auditability. When the provider is unreachable, the system queries for recent observations within a 5 km radius (< 3 hours old) before falling back to an explicit unavailable state.

---

## 5. PostGIS Persistence & Data Model

Database migration `V15__weather_enhancements.sql` augments the `weather_observations` table created in `V9`:

```sql
CREATE TABLE weather_observations (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    geometry          GEOMETRY(Point, 4326) NOT NULL,
    observed_at       TIMESTAMPTZ NOT NULL,
    temperature_c     FLOAT,
    precipitation_mm  FLOAT,
    visibility_km     FLOAT,
    weather_code      INTEGER,
    is_night          BOOLEAN,
    wind_speed_kmh    FLOAT,
    relative_humidity FLOAT,
    weather_description VARCHAR(100),
    source            VARCHAR(50) NOT NULL DEFAULT 'open-meteo',
    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_weather_geometry    ON weather_observations USING GIST(geometry);
CREATE INDEX idx_weather_observed_at ON weather_observations(observed_at DESC);
CREATE INDEX idx_weather_source      ON weather_observations(source);
```

---

## 6. Machine Learning Feature Pipeline Integration

SafePath AI's 15-feature inference schema includes two dedicated weather attributes:
- Feature #13: `weather_precipitation` (mm)
- Feature #14: `weather_visibility` (km)

### Implementation in `RoadSegmentFeatureService`:
- Overloaded methods `extractFeatures(segment, dateTime, precipMm, visibilityKm)` and `prepareBatchRequests(segments, dateTime, precipMm, visibilityKm)` accept optional weather parameters.
- When weather data is retrieved, the values are populated directly into `RiskFeaturesDto`.
- When weather is unavailable or fetch fails, features remain `null`, prompting the ML service's documented median/mean imputation routines.
- Backward compatibility is preserved for existing callers via default overloads.

---

## 7. Route Scoring & Fallback Policy

During route comparison (`RouteService.searchRoutes`):
1. Ambient weather is fetched for the journey origin coordinate.
2. If weather retrieval succeeds:
   - Precipitation and visibility are passed to the batch feature extractor.
   - The response flags `weatherContextAvailable: true`.
3. If weather retrieval fails or times out:
   - Feature extraction continues with `null` weather.
   - The response flags `weatherContextAvailable: false`.
   - Route comparison succeeds without interruption:
     - `FASTEST` route calculates topologically using walking speed.
     - `BALANCED` and `SAFEST` routes utilize ML predictions with default feature imputation.

---

## 8. REST API Specification

### Endpoint: `GET /api/weather`
- **Access:** Public (no authentication required)
- **Parameters:** `lat` (double), `lon` (double)

#### Example Response (200 OK — Live/Cached):
```json
{
  "latitude": 12.9716,
  "longitude": 77.5946,
  "available": true,
  "observation": {
    "temperatureC": 24.5,
    "precipitationMm": 0.5,
    "windSpeedKmh": 12.3,
    "relativeHumidity": 68.0,
    "visibilityKm": 18.5,
    "weatherCode": 61,
    "condition": "Slight rain",
    "icon": "weather-rainy",
    "isDay": true,
    "observedAt": "2026-10-14T12:00"
  },
  "forecast": {
    "precipitationProbabilityPercent": 20.0,
    "forecastTimestamp": "2026-10-14T12:00"
  },
  "units": {
    "temperature": "°C",
    "precipitation": "mm",
    "windSpeed": "km/h",
    "relativeHumidity": "%",
    "visibility": "km",
    "precipitationProbability": "%"
  },
  "source": "Open-Meteo (open-meteo.com)",
  "retrievedAt": "2026-10-14T12:00:30Z",
  "cached": false,
  "disclaimer": "Weather context represents ambient environmental conditions (temperature, precipitation, wind, visibility) from Open-Meteo. It does not measure personal safety or crime risk directly."
}
```

---

## 9. Mobile Integration

### 9.1 `WeatherWidget` Component (`mobile/components/WeatherWidget.tsx`)
- **Compact Pill Mode:** Sits cleanly in the floating map header showing current weather emoji and temperature (e.g. `☀️ 25°C`). Tapping expands the contextual card.
- **Detailed Context Mode:** Displays full observation metrics (precipitation, wind speed, humidity, visibility), forecast probability badge, source attribution, and academic disclaimer.
- **Loading & Error Handling:** Shows non-intrusive activity indicators and an explicit retry button on network failure. Never blocks the map or route planning.

### 9.2 Route Comparison Screen (`mobile/app/(tabs)/routes.tsx`)
- Displays a dedicated weather status banner confirming whether real-time ambient weather was integrated into the multi-factor scoring.
- Embeds the departure weather widget at route origin coordinates.

---

## 10. Verification & Test Evidence

### 10.1 Backend Test Results (`mvn test`)
- **Total Tests:** 104 passed, 0 failures, 0 errors, 0 skipped.
- **New Weather Tests Added:**
  - `OpenMeteoClientTest`: 5 tests (successful parsing, bounds validation, timeouts, HTTP 500 error mapping, missing current weather block).
  - `WeatherServiceTest`: 6 tests (DTO mapping, in-memory caching, TTL expiration, DB historical fallback, honest unavailable state, disabled mode).
  - `WeatherControllerTest`: 2 tests (200 OK with weather data, 200 OK with unavailable state).
  - `RoadSegmentFeatureServiceTest`: 2 new tests (weather feature propagation, missing weather null preservation).
  - `RouteComparisonServiceTest`: 2 new tests (weather context propagation to ML batch request, provider failure fault tolerance).

### 10.2 ML Service Test Results (`pytest ml-service/tests`)
- **Total Tests:** 15 passed in 13.84s (0 regressions).

### 10.3 Mobile TypeScript Typecheck (`.\node_modules\.bin\tsc.cmd --noEmit`)
- **Outcome:** Clean exit code 0 (0 type errors).

### 10.4 Live Verification Statement
- **Live Provider Request:** An actual live HTTP GET request to `https://api.open-meteo.com/v1/forecast` was successfully executed during development for Bangalore coordinates (`12.9716, 77.5946`), confirming live JSON field compatibility and response timings (< 500 ms).
- **Physical Device Rendering:** Mobile changes were verified against TypeScript compiler and React Native Web/Expo static type safety. Physical-device rendering remains pending manual simulator verification on target hardware.

---

*Last updated: 2026-10-14*  
*Version: 1.0.0*
