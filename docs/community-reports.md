# SafePath AI — Milestone 21: Community Reports Specification

> **Status:** Stable (Milestone 21 Complete)  
> **Author:** SafePath AI Development Team  
> **Scope:** Citizen Reporting Lifecycle, Optional Spatial Attribution, Privacy Guarantees, Review Workflow, and Abuse Prevention

---

## 1. Overview & Architectural Principles

Community safety reports empower citizens to report observed neighborhood hazards (poor lighting, road blockages, disturbances, harassment, or infrastructure issues). However, to preserve academic validity, scientific honesty, and user safety, community reports must never be treated as unvetted ground-truth crime observations.

### 1.1 Non-Negotiable Data Integrity Principles

In compliance with `AGENTS.md` (Section 9) and `docs/PROJECT-CONTRACT.md` (Section 7):

1. **Strict Isolation from Primary ML Models:**
   Unverified community reports (`PENDING`) must **never** directly influence road-segment risk scores, A* routing edge penalties, or the spatial feature engineering pipeline.
2. **Controlled Lifecycle Transitions:**
   Reports follow an explicit state machine:
   $$\text{PENDING} \longrightarrow \text{VERIFIED} \quad \text{or} \quad \text{PENDING} \longrightarrow \text{REJECTED}$$
   Terminal states cannot transition back to `PENDING` or alternate between `VERIFIED` and `REJECTED`.
3. **No Automatic Ground Truth:**
   A single unverified report cannot make a road arbitrarily unsafe. Only verified reports may ever contribute to neighborhood awareness, and unverified reports are strictly excluded from public queries.
4. **Optional Location & No Coordinate Fabrication:**
   Location coordinates are strictly optional. When provided by the user with explicit confirmation, coordinates are stored with `location_precision = EXACT_COORDINATE`. When omitted, coordinates are stored as `null` with `location_precision = UNKNOWN`. The system never fabricates coordinates or silently geocodes ambiguous addresses.
5. **Reporter Privacy & Identity Protection:**
   The identity (user ID, email, name, phone) of the reporter is stored securely in the database for abuse accountability, but is **never** serialized or exposed in public responses (`GET /api/reports/nearby`).
6. **No Background Location Tracking:**
   The mobile client never requests background location permissions for community reporting.
7. **Emergency Non-Equivalence:**
   The mobile UI and documentation clearly state that community reports are reviewed asynchronously and do **not** dispatch police or emergency responders. Users in immediate danger are directed to emergency hotlines (112 / 100).

---

## 2. Report Lifecycle & Workflow

```mermaid
stateDiagram-v2
    [*] --> PENDING: User submits report (Rate limited <= 5 per 10 min)
    
    state PENDING {
        [*] --> InReviewQueue
        note right of InReviewQueue: Visible only to reporter in /api/reports/my<br/>and moderators in admin queue.<br/>NEVER returned in public /api/reports/nearby.
    }
    
    PENDING --> VERIFIED: Moderator/Admin verifies (PUT /api/reports/{id}/verify)
    PENDING --> REJECTED: Moderator/Admin rejects (PUT /api/reports/{id}/verify)
    
    state VERIFIED {
        note right: Publicly visible in /api/reports/nearby.<br/>Audit logged with verified_by, verified_at.
    }
    
    state REJECTED {
        note right: Archived with rejection note.<br/>Excluded from public endpoints.
    }
    
    VERIFIED --> [*]
    REJECTED --> [*]
```

---

## 3. Abuse Control & Rate Limiting

To prevent report flooding, denial-of-service, and false reporting campaigns, the backend implements `ReportRateLimiterService`:
- **Policy:** Maximum 5 report submissions per authenticated user within a rolling 10-minute window (600,000 ms).
- **Mechanism:** Thread-safe in-memory sliding window tracking timestamps per user UUID.
- **Enforcement:** If a 6th request arrives within the window, the request is rejected with `HTTP 429 Too Many Requests` and a message specifying the remaining retry-after duration.
- **Architectural Fit:** Conforms to `AGENTS.md` Rule 12 by using an in-memory concurrent map without introducing Redis or external dependencies.

---

## 4. API Endpoints

### 4.1 Submit Safety Report
- **Route:** `POST /api/reports`
- **Auth:** Required (Bearer JWT)
- **Request Body:**
  ```json
  {
    "reportType": "POOR_LIGHTING",
    "description": "3 consecutive street lights are unlit on 100ft road near junction",
    "latitude": 12.9716,
    "longitude": 77.5946,
    "locationConfirmed": true,
    "incidentDatetime": "2026-10-14T11:30:00Z"
  }
  ```
- **Validation Rules:**
  - `reportType`: Required, must match `ReportType` enum.
  - `description`: Optional, max 1000 characters, untrusted user input is sanitized (HTML stripped).
  - `latitude` and `longitude`: Optional together. If one is present, both must be present and within valid WGS84 ranges ($[-90, 90]$ and $[-180, 180]$).
  - `incidentDatetime`: Optional, must be `@PastOrPresent` (future timestamps rejected with 400).
- **Response 201 Created:**
  ```json
  {
    "id": "7f8b91a2-...",
    "reportType": "POOR_LIGHTING",
    "status": "PENDING",
    "locationPrecision": "EXACT_COORDINATE",
    "createdAt": "2026-10-14T12:00:00Z",
    "message": "Report received. Thank you. It has been submitted for verification."
  }
  ```

### 4.2 Get Nearby Verified Reports
- **Route:** `GET /api/reports/nearby`
- **Auth:** Public or Authenticated
- **Query Params:** `lat`, `lon`, `radiusMeters` (default 500, max 5000), `limit` (default 20, max 50)
- **Behavior:**
  - Returns **only** reports with `status = 'VERIFIED'` and non-null geometries.
  - Strictly omits reporter personal data (`user_id`, email, phone, name).
- **Response 200 OK:**
  ```json
  [
    {
      "id": "7f8b91a2-...",
      "reportType": "POOR_LIGHTING",
      "description": "3 consecutive street lights are unlit on 100ft road near junction",
      "latitude": 12.9716,
      "longitude": 77.5946,
      "locationPrecision": "EXACT_COORDINATE",
      "status": "VERIFIED",
      "incidentDatetime": "2026-10-14T11:30:00Z",
      "createdAt": "2026-10-14T12:00:00Z",
      "distanceMeters": 142.5
    }
  ]
  ```

### 4.3 Get My Submitted Reports
- **Route:** `GET /api/reports/my`
- **Auth:** Required (Bearer JWT)
- **Query Params:** `page` (default 0), `size` (default 20)
- **Behavior:** Returns all reports submitted by the authenticated user across all statuses (`PENDING`, `VERIFIED`, `REJECTED`), allowing them to track the verification progress of their contributions.

### 4.4 Verify or Reject Report (Moderator / Admin)
- **Route:** `PUT /api/reports/{id}/verify`
- **Auth:** Required with `ROLE_MODERATOR` or `ROLE_ADMIN` (`@PreAuthorize`)
- **Request Body:**
  ```json
  {
    "status": "VERIFIED",
    "verificationNote": "Verified by municipal streetlight outage ticket #4928"
  }
  ```
- **Validation:**
  - Status must be `VERIFIED` or `REJECTED`.
  - Existing report status must be `PENDING`. Processing an already verified or rejected report throws `400 Bad Request`.
  - Records `verifiedBy`, `verifiedAt`, and `verificationNote`.

---

## 5. Controlled Category Enumeration

| Code | Label | Typical Conditions |
|------|-------|-------------------|
| `POOR_LIGHTING` | Poor Street Lighting | Dim, shadowed, or absent streetlamps |
| `BROKEN_STREETLIGHT` | Broken Streetlight | Specific damaged or flickering lighting poles |
| `ROAD_BLOCKAGE` | Road Blockage | Construction barricades, fallen debris, fallen trees |
| `UNSAFE_ROAD` | Unsafe Road Condition | Hazardous pavement, missing sidewalk, high-speed blind curves |
| `CROWD_ISSUE` | Crowd Concern | Unruly gatherings, brawls, hostile crowds |
| `SUSPICIOUS_ACTIVITY` | Suspicious Activity | Loitering or prowling behavior observed |
| `HARASSMENT` | Harassment | Verbal catcalling, intimidation, following |
| `OTHER` | Other Concern | Neighborhood safety concerns not covered above |

---

## 6. Mobile Experience (`mobile/app/(tabs)/reports.tsx`)

1. **Prominent Emergency Notice:**
   Alert card emphasizing that reports are not an emergency dispatch mechanism and prompting users to dial 112/100 for urgent safety threats.
2. **Two-Tab Interface:**
   - **Submit Report:** Interactive form with category cards, character-counted description input, quick incident timing chips, and optional location toggle.
   - **My Reports:** History view displaying submitted reports with color-coded status badges (`PENDING` in amber, `VERIFIED` in green, `REJECTED` in gray).
3. **Explicit Location Confirmation:**
   - Location coordinates are only attached when the user actively toggles the switch and confirms.
   - Requests only foreground permissions (`requestForegroundPermissionsAsync`).
   - Displays clear privacy note explaining that unverified report coordinates remain hidden from other users.
4. **Resilience & Feedback:**
   - Loading spinners during submission.
   - Success confirmation screen displaying reference UUID and review notice.
   - Clean error banner with retry option for network interruptions or rate limits.

---

## 7. Limitations & Documented Choices

- **Moderation Interface:** The moderation transition endpoint (`PUT /api/reports/{id}/verify`) is secured via Spring Security RBAC (`ROLE_MODERATOR`, `ROLE_ADMIN`). A dedicated mobile moderation dashboard is not yet implemented in the citizen-facing app; moderation is performed via backend API or admin portals.
- **Rate Limit Window:** 5 submissions per 10 minutes per user is an implementation choice balancing citizen convenience against spam protection.
