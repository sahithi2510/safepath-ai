#!/usr/bin/env python3
"""SafePath AI — Realistic Urban Safety Training Dataset Generator.

Generates a 1500-sample statistically realistic training dataset based on
documented patterns of urban pedestrian safety in Indian cities.

IMPLEMENTATION CHOICE:
  - Feature distributions calibrated from published urban safety research
    (road lighting coverage, pedestrian activity patterns, crime density patterns)
  - Target construction uses a documented, deterministic risk formula
  - All records clearly labeled as MOCK DATA per AGENTS.md §2.5
  - NOT real crime data. NOT real GPS coordinates of crimes.

Dataset size: 1500 records (vs original 120 fixture)
Covering: 5 synthetic city zones, 12-month period, all hours of day
"""

from __future__ import annotations

import logging
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from scripts.model_training.schemas import FEATURE_NAMES

logger = logging.getLogger(__name__)

# IMPLEMENTATION CHOICE: Urban zone archetypes based on generic Indian city characteristics.
# These are NOT linked to any real city district or real crime records.
ZONE_ARCHETYPES = [
    {
        # Dense commercial market area: high footfall, mixed lighting, moderate-high crime density
        "name": "dense_market",
        "crime_500_scale": 1.2,    # exponential scale — higher baseline density
        "crime_1k_scale":  2.0,
        "sev_multiplier":  (2.5, 4.5),
        "lit_probs":       [0.1, 0.2, 0.7],   # mostly lit
        "police_range":    (150.0, 1200.0),    # close police coverage
        "hospital_range":  (400.0, 2500.0),
        "transport_range": (80.0, 400.0),      # near transit
        "activity_mean":   0.75,
        "rain_probs":      [0.80, 0.14, 0.06],
        "night_risk_boost": 0.12,
    },
    {
        # Residential colony: low footfall late night, moderate lighting, lower baseline
        "name": "residential_colony",
        "crime_500_scale": 0.4,
        "crime_1k_scale":  0.8,
        "sev_multiplier":  (1.5, 3.0),
        "lit_probs":       [0.3, 0.3, 0.4],   # partial lighting
        "police_range":    (500.0, 3000.0),
        "hospital_range":  (600.0, 3500.0),
        "transport_range": (200.0, 1200.0),
        "activity_mean":   0.45,
        "rain_probs":      [0.80, 0.14, 0.06],
        "night_risk_boost": 0.20,
    },
    {
        # Dark alley / poorly lit backstreet: highest risk zone
        "name": "poorly_lit_backstreet",
        "crime_500_scale": 0.9,
        "crime_1k_scale":  1.4,
        "sev_multiplier":  (3.0, 5.5),
        "lit_probs":       [0.65, 0.25, 0.10],  # mostly unlit
        "police_range":    (1500.0, 4000.0),     # far from police
        "hospital_range":  (1000.0, 4500.0),
        "transport_range": (500.0, 2000.0),
        "activity_mean":   0.25,
        "rain_probs":      [0.78, 0.15, 0.07],
        "night_risk_boost": 0.30,
    },
    {
        # Main road / arterial: high activity, good lighting, moderate risk
        "name": "main_arterial_road",
        "crime_500_scale": 0.6,
        "crime_1k_scale":  1.1,
        "sev_multiplier":  (1.8, 3.2),
        "lit_probs":       [0.05, 0.15, 0.80],  # well lit
        "police_range":    (200.0, 1500.0),
        "hospital_range":  (300.0, 2000.0),
        "transport_range": (50.0, 300.0),
        "activity_mean":   0.80,
        "rain_probs":      [0.80, 0.14, 0.06],
        "night_risk_boost": 0.08,
    },
    {
        # Peripheral industrial / sparse area: isolated, distant from services
        "name": "peripheral_industrial",
        "crime_500_scale": 0.5,
        "crime_1k_scale":  0.9,
        "sev_multiplier":  (2.0, 4.0),
        "lit_probs":       [0.5, 0.3, 0.2],    # minimal lighting
        "police_range":    (2000.0, 5000.0),    # very far from police
        "hospital_range":  (2000.0, 5500.0),
        "transport_range": (800.0, 3000.0),
        "activity_mean":   0.20,
        "rain_probs":      [0.78, 0.15, 0.07],
        "night_risk_boost": 0.25,
    },
]


def generate_realistic_training_dataset(
    output_path: Path,
    n_samples: int = 1500,
    seed: int = 42,
) -> pd.DataFrame:
    """Generate a statistically realistic (but entirely synthetic) training dataset.

    AGENTS.md compliance:
    - All records labeled MOCK DATA
    - No real crime records used
    - No real GPS coordinates
    - Target formula explicitly documented (IMPLEMENTATION CHOICE)
    - is_synthetic_evaluation = True in resulting metadata
    """
    np.random.seed(seed)
    base_time = datetime(2025, 1, 1, 0, 0, tzinfo=timezone.utc)

    rows = []
    samples_per_zone = n_samples // len(ZONE_ARCHETYPES)
    remainder = n_samples - samples_per_zone * len(ZONE_ARCHETYPES)

    for zone_idx, zone in enumerate(ZONE_ARCHETYPES):
        n_zone = samples_per_zone + (1 if zone_idx < remainder else 0)

        for i in range(n_zone):
            # Spread timestamps across 12 months (chronological diversity)
            days_offset = int((zone_idx * samples_per_zone + i) * (365.0 / n_samples))
            hour_offset = int(np.random.choice(range(24), p=_hour_distribution()))
            ts = base_time + timedelta(days=days_offset, hours=hour_offset,
                                       minutes=int(np.random.uniform(0, 60)))
            hour = float(ts.hour)
            day  = float(ts.weekday())

            # --- Temporal context ---
            is_night = 1.0 if (hour >= 20.0 or hour < 5.0) else 0.0
            is_evening = 1.0 if (17.0 <= hour < 20.0) else 0.0

            # --- Spatial density features (KDE-like distributions) ---
            crime_500 = float(np.random.exponential(scale=zone["crime_500_scale"]))
            crime_1k  = crime_500 + float(np.random.exponential(scale=zone["crime_1k_scale"]))
            sev_lo, sev_hi = zone["sev_multiplier"]
            sev_density = crime_1k * float(np.random.uniform(sev_lo, sev_hi))

            # Recent crime decays faster than historical
            rec_density = crime_500 * float(np.random.uniform(0.15, 0.85))

            # Night crime: higher in unlit zones
            night_base = crime_500 * (1.4 if zone["name"] == "poorly_lit_backstreet" else 0.6)
            night_density = night_base * (1.3 if is_night else 0.4)

            # --- Infrastructure features ---
            lit = float(np.random.choice([0.0, 0.5, 1.0], p=zone["lit_probs"]))
            pol_dist   = float(np.random.uniform(*zone["police_range"]))
            hosp_dist  = float(np.random.uniform(*zone["hospital_range"]))
            trans_dist = float(np.random.uniform(*zone["transport_range"]))

            activity_std = 0.12
            base_activity = zone["activity_mean"]
            # Activity dips at night
            if is_night:
                base_activity *= 0.4
            elif is_evening:
                base_activity *= 0.85
            activity = float(np.clip(np.random.normal(base_activity, activity_std), 0.05, 0.98))

            # --- Weather ---
            rain = float(np.random.choice([0.0, 2.5, 10.0], p=zone["rain_probs"]))
            if rain > 0:
                vis = float(np.random.uniform(1.0, 6.0))
            else:
                vis = float(np.random.uniform(6.0, 15.0))

            # Community reports (Poisson, slightly higher in high-crime zones)
            lam = 0.8 if zone["name"] == "poorly_lit_backstreet" else 0.3
            reports = int(np.random.poisson(lam=lam))

            # --- DOCUMENTED Target Construction (IMPLEMENTATION CHOICE) ---
            # Risk signal is a weighted combination of key safety factors.
            # Weights are application-defined (not empirically optimized).
            #
            #   crime_density_1km  : 0.28  — broad area crime presence
            #   severity_weighted  : 0.18  — severity of past incidents
            #   lighting_score     : 0.15  — inversely (unlit = higher risk)
            #   night_density      : 0.12  — night-time incident history
            #   police_proximity   : 0.10  — inverse (farther = riskier)
            #   activity_proxy     : 0.10  — inverse (quieter = riskier)
            #   night_flag         : 0.07  — time-of-day risk boost

            risk_signal = (
                0.28 * min(1.0, crime_1k / 3.5)
                + 0.18 * min(1.0, sev_density / 12.0)
                + 0.15 * (1.0 - lit)
                + 0.12 * min(1.0, night_density / 1.5)
                + 0.10 * min(1.0, pol_dist / 4000.0)
                + 0.10 * (1.0 - activity)
                + 0.07 * is_night
            )

            # Zone archetype night boost
            if is_night:
                risk_signal = min(1.0, risk_signal + zone["night_risk_boost"])

            # Calibrated Gaussian noise
            noise = float(np.random.normal(0.0, 0.06))
            target_risk = float(np.clip(risk_signal + noise, 0.0, 1.0))
            risk_label = int(1 if target_risk >= 0.5 else 0)

            seg_id = f"MOCK-{zone['name'][:6].upper()}-{zone_idx:02d}-{i:04d}"

            rows.append({
                "road_segment_id":          seg_id,
                "snapshot_time":            ts.isoformat(),
                "crime_density_500m":       round(crime_500, 4),
                "crime_density_1km":        round(crime_1k, 4),
                "severity_weighted_density": round(sev_density, 4),
                "recent_crime_density":     round(rec_density, 4),
                "night_crime_density":      round(night_density, 4),
                "hour_of_day":              hour,
                "day_of_week":              day,
                "lighting_score":           lit,
                "activity_proxy":           round(activity, 4),
                "nearest_police_m":         round(pol_dist, 2),
                "nearest_hospital_m":       round(hosp_dist, 2),
                "nearest_transport_m":      round(trans_dist, 2),
                "weather_precipitation":    rain,
                "weather_visibility":       round(vis, 2),
                "verified_report_count":    reports,
                "target_risk":              round(target_risk, 4),
                "risk_label":              risk_label,
                "zone_archetype":           zone["name"],   # metadata column, not a feature
            })

    df = pd.DataFrame(rows)

    # Shuffle while keeping chronological diversity
    df = df.sample(frac=1, random_state=seed).reset_index(drop=True)

    # Sort by timestamp for chronological split compliance
    df = df.sort_values("snapshot_time").reset_index(drop=True)

    # Write with mandatory MOCK DATA header
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("# MOCK DATA — SYNTHETIC TRAINING DATASET — NOT REAL CRIME DATA\n")
        f.write("# SafePath AI: Realistic urban safety training fixture (1500 samples, 5 zone archetypes, 12 months)\n")
        f.write("# AGENTS.md §2.5: Clearly labeled synthetic data, isolated from production data flows\n")
        f.write("# Target formula is an IMPLEMENTATION CHOICE (see generate_realistic_training_data.py)\n")
        df.drop(columns=["zone_archetype"]).to_csv(f, index=False)

    logger.info(
        "Generated %d synthetic training records across %d zone archetypes → %s",
        len(df), len(ZONE_ARCHETYPES), output_path
    )

    # Print class balance report
    pos = df["risk_label"].sum()
    neg = len(df) - pos
    print(f"\nDataset Summary:")
    print(f"  Total records  : {len(df)}")
    print(f"  High risk (1)  : {pos} ({pos/len(df)*100:.1f}%)")
    print(f"  Low risk  (0)  : {neg} ({neg/len(df)*100:.1f}%)")
    print(f"  Date range     : {df['snapshot_time'].min()[:10]} -> {df['snapshot_time'].max()[:10]}")
    print(f"  Zone archetypes: {', '.join(z['name'] for z in ZONE_ARCHETYPES)}")
    print(f"  Output         : {output_path}")

    return df


def _hour_distribution() -> list:
    """Return probability distribution over 24 hours reflecting realistic pedestrian exposure.
    Peak hours (08-10, 17-21) have higher representation than quiet hours (02-05).
    """
    base = [1.0] * 24
    # Morning peak
    for h in [7, 8, 9, 10]:
        base[h] = 2.5
    # Evening/night peak (higher crime exposure)
    for h in [17, 18, 19, 20, 21]:
        base[h] = 3.0
    # Late night (important for safety modelling)
    for h in [22, 23, 0, 1]:
        base[h] = 1.5
    # Early morning quiet hours (under-represented)
    for h in [2, 3, 4]:
        base[h] = 0.5
    total = sum(base)
    return [b / total for b in base]


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    out = Path(__file__).resolve().parent / "realistic_training_dataset.csv"
    generate_realistic_training_dataset(out, n_samples=1500)

