# SafePath AI — API Reference

> **Last Updated:** 2026-10-08  
> **Base URL:** `http://localhost:8080/api`  
> **Authentication:** Bearer JWT token (except `/auth/*` endpoints)  
> **Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## 1. Authentication

### POST /api/auth/register

Register a new user.

**Request:**
```json
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "password": "SecurePassword123!",
  "phoneNumber": "+91-9876543210"
}
```

**Response 201:**
```json
{
  "id": "uuid",
  "email": "jane@example.com",
  "fullName": "Jane Doe",
  "createdAt": "2026-10-08T10:00:00Z"
}
```

**Errors:**
- `400` — Validation failure (invalid email, weak password, etc.)
- `409` — Email already registered

---

### POST /api/auth/login

Authenticate and receive JWT token.

**Request:**
```json
{
  "email": "jane@example.com",
  "password": "SecurePassword123!"
}
```

**Response 200:**
```json
{
  "token": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "uuid",
    "email": "jane@example.com",
    "fullName": "Jane Doe",
    "routePreference": "STANDARD"
  }
}
```

**Errors:**
- `400` — Validation failure
- `401` — Invalid credentials

---

## 2. Users

### GET /api/users/me

Get current user profile.

**Response 200:**
```json
{
  "id": "uuid",
  "email": "jane@example.com",
  "fullName": "Jane Doe",
  "phoneNumber": "+91-9876543210",
  "routePreference": "STANDARD",
  "createdAt": "2026-10-08T10:00:00Z"
}
```

---

### PUT /api/users/me

Update user profile.

**Request:**
```json
{
  "fullName": "Jane Updated",
  "phoneNumber": "+91-9876543211"
}
```

---

### PUT /api/users/me/preferences

Update route preference profile.

**Request:**
```json
{
  "routePreference": "SOLO_TRAVELLER"
}
```

`routePreference` values: `STANDARD`, `SOLO_TRAVELLER`, `TOURIST`, `ELDERLY`

---

## 3. Routes

### POST /api/routes/search (and POST /api/routes/compare)

Search and compare three candidate routes (`FASTEST`, `BALANCED`, `SAFEST`). Enforces duplicate-path detection, transparent trade-off explanation, and explicit UNSCORED state when ML service is offline.

**Request:**
```json
{
  "origin": {
    "latitude": 12.9716,
    "longitude": 77.5946
  },
  "destination": {
    "latitude": 12.9800,
    "longitude": 77.6100
  },
  "travelMode": "WALKING",
  "routePreference": "STANDARD",
  "departureTime": "2026-10-08T18:00:00+05:30"
}
```
*Flat coordinates (`startLat`, `startLon`, `endLat`, `endLon`) are also supported.*

**Response 200:**
```json
{
  "requestId": "uuid",
  "fastestRoute": {
    "routeMode": "FASTEST",
    "distanceMeters": 1850.5,
    "durationSeconds": 1320.0,
    "safetyScore": 45.0,
    "riskLevel": "MODERATE",
    "safetyLabel": "Moderate",
    "litSegmentsPercentage": 60.0,
    "isDistinct": true,
    "duplicateOf": null,
    "isMlScored": true,
    "modelVersion": "xgb-v1.0",
    "importantFactors": [
      "Minimizes estimated walking duration",
      "60% of route is along verified lit segments"
    ],
    "geometry": {
      "type": "LineString",
      "coordinates": [[77.5946, 12.9716], ["..."]]
    },
    "coordinates": [
      { "latitude": 12.9716, "longitude": 77.5946 },
      { "latitude": 12.9800, "longitude": 77.6100 }
    ],
    "disclaimer": "Relative risk estimate derived from street attributes; not a validated empirical probability of crime occurrence."
  },
  "balancedRoute": {
    "routeMode": "BALANCED",
    "distanceMeters": 1980.0,
    "durationSeconds": 1414.0,
    "safetyScore": 78.0,
    "riskLevel": "LOW",
    "safetyLabel": "Safe",
    "isDistinct": true,
    "duplicateOf": null
  },
  "safestRoute": {
    "routeMode": "SAFEST",
    "distanceMeters": 2100.0,
    "durationSeconds": 1500.0,
    "safetyScore": 88.0,
    "riskLevel": "VERY_LOW",
    "safetyLabel": "Very Safe",
    "isDistinct": true,
    "duplicateOf": null
  },
  "tradeOffExplanation": "SAFEST route trades +3.0 min (+250 m) for a +43.0 pt improvement in safety score (95% vs 60% lit streets).",
  "hasDistinctAlternatives": true,
  "distinctRouteCount": 3,
  "computedAt": "2026-10-08T12:30:00Z",
  "mlServiceAvailable": true,
  "weatherContextAvailable": false
}
```

### POST /api/routes

Legacy single route planning endpoint.

**Request:**
```json
{
  "startLat": 12.9716,
  "startLon": 77.5946,
  "endLat": 12.9800,
  "endLon": 77.6100,
  "preference": "SAFEST"
}
```

**Errors:**
- `400` — Invalid coordinates or missing fields
- `404` — No walkable road network or no route found between origin and destination
- `503` — ML service unavailable when `SAFEST` route is requested individually
- `503` — Routing service temporarily unavailable

**Notes:**
- If ML service is unavailable, routes are returned without safety scores; `mlServiceAvailable: false`.
- If weather is unavailable, weather features are omitted; `weatherContextAvailable: false`.
- `demo: false` confirms this is not mock data. If `demo: true`, it is demonstration data only.

---

### GET /api/routes/history

Get user's route search history.

**Query params:** `page=0&size=20`

---

## 3.1 Safety Heatmap

### GET /api/heatmap (Alias: /api/routes/heatmap)

Retrieve road-segment geometries and relative-risk safety predictions within a geographic bounding box.

**Query Parameters:**
- `minLat` (required): Minimum latitude (WGS84, -90.0 to 90.0)
- `minLon` (required): Minimum longitude (WGS84, -180.0 to 180.0)
- `maxLat` (required): Maximum latitude (WGS84, -90.0 to 90.0)
- `maxLon` (required): Maximum longitude (WGS84, -180.0 to 180.0)
- `limit` (optional): Maximum segments returned (default: 500, clamped to 1..1000)

**Validation Constraints:**
- `minLat <= maxLat` and `minLon <= maxLon`
- `(maxLat - minLat) <= 0.15` (~16.5 km maximum latitude span)
- `(maxLon - minLon) <= 0.15` (~16.5 km maximum longitude span)

**Response 200:**
```json
{
  "boundingBox": {
    "minLat": 12.9700,
    "minLon": 77.6000,
    "maxLat": 12.9800,
    "maxLon": 77.6100
  },
  "totalSegmentsReturned": 45,
  "scoredSegmentsCount": 45,
  "unscoredSegmentsCount": 0,
  "mlServiceAvailable": true,
  "isSyntheticDemonstration": true,
  "modelVersion": "xgboost-v1.0.0",
  "generatedAt": "2026-10-13T12:00:00Z",
  "disclaimer": "Safety scores and risk categories are feature-based estimates derived from demonstration models. They do not constitute guaranteed safety or calibrated crime probabilities.",
  "dataProvenanceNote": "Model predictions derive from synthetic training fixtures and demonstration road attributes. Unobserved streets are never assumed safe.",
  "segments": [
    {
      "segmentId": 101,
      "osmId": 1234567,
      "name": "MG Road",
      "highwayType": "primary",
      "coordinates": [
        { "latitude": 12.9756, "longitude": 77.6066 },
        { "latitude": 12.9716, "longitude": 77.6074 }
      ],
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [77.6066, 12.9756],
          [77.6074, 12.9716]
        ]
      },
      "relativeRiskScore": 0.25,
      "safetyScore": 75.0,
      "riskCategory": "LOW",
      "scoringStatus": "SCORED",
      "colorHex": "#34C759",
      "isLit": true,
      "surface": "asphalt",
      "lengthMeters": 450.2,
      "isSyntheticDemonstration": true,
      "modelVersion": "xgboost-v1.0.0",
      "scoredAt": "2026-10-13T12:00:00Z"
    }
  ]
}
```

**Errors:**
- `400` — Invalid bounding box coordinates or span exceeding 0.15 degrees
- `500` — Unexpected server error

**Integrity & Degradation Policy:**
- When the ML service is unreachable, road segments are still returned for the viewport with `scoringStatus: "UNAVAILABLE"`, `riskCategory: "UNSCORED"`, and neutral gray color `#8E8E93`.
- Segments lacking observation data are marked `UNSCORED` and are **never** assigned a default low-risk or safe value.
- Coordinates adhere to EPSG:4326 WGS84 standard.

---

## 4. Community Reports

### POST /api/reports

Submit a community safety report.

**Request:**
```json
{
  "reportType": "POOR_LIGHTING",
  "description": "Street lights are broken near the junction",
  "latitude": 12.9750,
  "longitude": 77.5980,
  "incidentDatetime": "2026-10-08T20:00:00+05:30"
}
```

`reportType` values: `HARASSMENT`, `SUSPICIOUS_ACTIVITY`, `POOR_LIGHTING`, `UNSAFE_ROAD`, `BROKEN_STREETLIGHT`, `CROWD_ISSUE`, `ROAD_BLOCKAGE`, `OTHER`

**Response 201:**
```json
{
  "id": "uuid",
  "reportType": "POOR_LIGHTING",
  "status": "PENDING",
  "createdAt": "2026-10-08T12:00:00Z",
  "message": "Report received. Thank you. It will be reviewed."
}
```

---

### GET /api/reports/nearby

Get nearby verified reports.

**Query params:** `lat=12.97&lon=77.59&radiusMeters=500&limit=20`

**Response 200:**
```json
{
  "reports": [
    {
      "id": "uuid",
      "reportType": "POOR_LIGHTING",
      "description": "...",
      "latitude": 12.975,
      "longitude": 77.598,
      "status": "VERIFIED",
      "distanceMeters": 120.5,
      "createdAt": "2026-10-07T20:00:00Z"
    }
  ],
  "count": 1
}
```

Note: Only `VERIFIED` reports are returned in this endpoint.

---

### GET /api/reports/my

Get current user's own reports (all statuses).

---

### PUT /api/reports/{id}/verify (ADMIN)

Verify or reject a report.

**Request:**
```json
{
  "status": "VERIFIED",
  "verificationNote": "Confirmed by local patrol"
}
```

---

## 5. Safe Zones

### GET /api/safe-zones/nearby

Get nearby safe zones.

**Query params:** `lat=12.97&lon=77.59&radiusMeters=1000&types=POLICE_STATION,HOSPITAL&limit=10`

**Response 200:**
```json
{
  "safeZones": [
    {
      "id": "uuid",
      "name": "Cubbon Park Police Station",
      "zoneType": "POLICE_STATION",
      "latitude": 12.9762,
      "longitude": 77.5929,
      "distanceMeters": 250.3,
      "address": "Cubbon Park, Bengaluru",
      "phoneNumber": "100",
      "is24h": true
    }
  ]
}
```

---

## 6. Emergency Contacts

### GET /api/emergency/contacts

Get user's emergency contacts.

### POST /api/emergency/contacts

Add an emergency contact.

**Request:**
```json
{
  "name": "Mom",
  "phoneNumber": "+91-9876543210",
  "relationship": "Mother",
  "isPrimary": true
}
```

### DELETE /api/emergency/contacts/{id}

Remove an emergency contact.

### POST /api/emergency/sos

Log an SOS event (for audit trail and potential notification).

**Request:**
```json
{
  "latitude": 12.9716,
  "longitude": 77.5946,
  "note": "Feeling unsafe near MG Road"
}
```

> **Note:** This does NOT automatically contact emergency services. It logs the event and location.
> The app provides contact information for emergency services separately.

---

## 7. Health

### GET /api/health

Backend health check.

**Response 200:**
```json
{
  "status": "UP",
  "database": "UP",
  "mlService": "UP",
  "timestamp": "2026-10-08T12:00:00Z"
}
```

---

## 8. Error Response Format

All errors follow this format:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-10-08T12:00:00Z",
  "path": "/api/routes/search",
  "fieldErrors": [
    {
      "field": "origin.latitude",
      "message": "must not be null"
    }
  ]
}
```

Stack traces are **never** included in responses.

---

*SafePath AI — api.md — 2026-10-08*
