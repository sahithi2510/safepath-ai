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

### POST /api/routes/search

Search for routes. Returns three route options.

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

**Response 200:**
```json
{
  "requestId": "uuid",
  "fastestRoute": {
    "routeMode": "FASTEST",
    "geometry": {
      "type": "LineString",
      "coordinates": [[77.5946, 12.9716], ["..."]]
    },
    "distanceMeters": 1850.5,
    "durationSeconds": 1320,
    "safetyScore": 0.62,
    "riskLevel": "MODERATE",
    "safetyLabel": "Moderate",
    "importantFactors": [
      "Faster route passes through less-lit areas",
      "Lower pedestrian activity estimated"
    ],
    "explanation": {
      "factors": [
        {"name": "lighting_score", "contribution": -0.12, "description": "Lower lighting along this route"},
        {"name": "nearest_police_m", "contribution": 0.05, "description": "Police station within 500m"}
      ],
      "disclaimer": "Explanations are based on model features, not direct observations."
    },
    "demo": false
  },
  "balancedRoute": { "..." },
  "safestRoute": { "..." },
  "computedAt": "2026-10-08T12:30:00Z",
  "mlServiceAvailable": true,
  "weatherContextAvailable": true
}
```

**Errors:**
- `400` — Invalid coordinates or missing fields
- `404` — No route found between origin and destination
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
