"""Deterministic generator for synthetic training fixture.

Creates mock_training_dataset.csv with labeled DEMO DATA metadata for unit testing.
"""

from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys
import numpy as np
import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from scripts.model_training.schemas import FEATURE_NAMES


def generate_mock_training_dataset(output_path: Path, n_samples: int = 120, seed: int = 42):
    np.random.seed(seed)
    base_time = datetime(2026, 1, 1, 0, 0, tzinfo=timezone.utc)

    rows = []
    for i in range(n_samples):
        # Timestamps spread chronologically across 4 months
        ts = base_time + timedelta(hours=i * 24 + np.random.randint(0, 12))
        hour = float(ts.hour)
        day = float(ts.weekday())

        # Synthetic feature generation with realistic distributions
        is_night = 1.0 if (hour >= 20.0 or hour < 5.0) else 0.0
        lit = float(np.random.choice([0.0, 0.5, 1.0], p=[0.3, 0.2, 0.5]))
        crime_500 = float(np.random.exponential(scale=0.5))
        crime_1k = crime_500 + float(np.random.exponential(scale=0.8))
        sev_density = crime_1k * float(np.random.uniform(1.5, 4.0))
        rec_density = crime_500 * float(np.random.uniform(0.2, 1.0))
        night_density = crime_500 * (1.2 if is_night else 0.3)

        pol_dist = float(np.random.uniform(200.0, 3500.0))
        hosp_dist = float(np.random.uniform(300.0, 4000.0))
        trans_dist = float(np.random.uniform(100.0, 1500.0))

        activity = float(np.clip(np.random.normal(0.6 if 8 <= hour <= 20 else 0.3, 0.15), 0.1, 0.95))
        rain = float(np.random.choice([0.0, 2.5, 10.0], p=[0.8, 0.15, 0.05]))
        vis = float(np.random.uniform(5.0, 15.0) if rain == 0.0 else np.random.uniform(1.0, 5.0))
        reports = int(np.random.poisson(lam=0.4))

        # Target construction for synthetic testing:
        # High crime density, low lighting, night time, and distance from police increase risk
        risk_signal = (
            0.30 * min(1.0, crime_1k / 2.0)
            + 0.20 * min(1.0, sev_density / 6.0)
            + 0.15 * (1.0 - lit)
            + 0.15 * is_night
            + 0.10 * min(1.0, pol_dist / 3000.0)
            + 0.10 * (1.0 - activity)
        )
        # Add random noise
        target_risk = float(np.clip(risk_signal + np.random.normal(0.0, 0.08), 0.0, 1.0))
        # Binary target: High Risk if risk >= 0.5
        risk_label = int(1 if target_risk >= 0.5 else 0)

        row = {
            "road_segment_id": f"SEG_{i+1:04d}",
            "snapshot_time": ts.isoformat(),
            "crime_density_500m": round(crime_500, 4),
            "crime_density_1km": round(crime_1k, 4),
            "severity_weighted_density": round(sev_density, 4),
            "recent_crime_density": round(rec_density, 4),
            "night_crime_density": round(night_density, 4),
            "hour_of_day": hour,
            "day_of_week": day,
            "lighting_score": lit,
            "activity_proxy": round(activity, 4),
            "nearest_police_m": round(pol_dist, 2),
            "nearest_hospital_m": round(hosp_dist, 2),
            "nearest_transport_m": round(trans_dist, 2),
            "weather_precipitation": rain,
            "weather_visibility": round(vis, 2),
            "verified_report_count": reports,
            "target_risk": round(target_risk, 4),
            "risk_label": risk_label,
        }
        rows.append(row)

    df = pd.DataFrame(rows)

    # Write CSV with clear DEMO DATA warning headers per AGENTS.md §2.5
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("# DEMO DATA - SYNTHETIC TRAINING FIXTURE - NOT REAL CRIME DATA\n")
        f.write("# SafePath AI Milestone 13: Random Forest Baseline Verification Fixture\n")
        f.write("# Warning: Metrics on this dataset verify pipeline execution, not real accuracy\n")
        df.to_csv(f, index=False)

    print(f"Generated {len(df)} synthetic rows at {output_path}")


if __name__ == "__main__":
    out = Path(__file__).resolve().parent / "mock_training_dataset.csv"
    generate_mock_training_dataset(out)
