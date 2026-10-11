"""SafePath AI - Temporal Feature Extractor.

Derives temporal features from valid timestamps:
- hour_of_day (0-23)
- day_of_week (0-6)
- is_weekend (0 or 1)
- is_night (0 or 1)
- is_rush_hour (0 or 1)
- cyclical hour/day sine and cosine encodings

Handles missing/sparse timestamps with documented defaults and audit flags.
"""

from __future__ import annotations

from datetime import datetime, timezone
import math
from typing import Any, Dict, Optional, Tuple, Union

import numpy as np
import pandas as pd

from scripts.feature_pipeline.schemas import FeaturePipelineConfig


class TemporalFeatureExtractor:
    """Extracts calendar and cyclical temporal features for risk prediction."""

    def __init__(self, config: Optional[FeaturePipelineConfig] = None):
        self.config = config or FeaturePipelineConfig()

    def extract_features(
        self, timestamp: Optional[Union[datetime, str, pd.Timestamp]]
    ) -> Tuple[Dict[str, float], bool]:
        """Extract temporal features from a single timestamp.

        Returns:
            (features_dict, is_valid_timestamp)
        """
        dt = self._normalize_datetime(timestamp)

        if dt is None:
            # Impute documented defaults when timestamp is unavailable
            return {
                "hour_of_day": 12.0,
                "day_of_week": 0.0,
                "is_weekend": 0.0,
                "is_night": 0.0,
                "is_rush_hour": 0.0,
                "month": 1.0,
                "sin_hour": 0.0,
                "cos_hour": 1.0,
                "sin_day": 0.0,
                "cos_day": 1.0,
            }, False

        hour = float(dt.hour)
        day = float(dt.weekday())  # 0=Monday, 6=Sunday
        month = float(dt.month)

        is_weekend = 1.0 if day in (5.0, 6.0) else 0.0
        is_night = (
            1.0
            if (hour >= self.config.night_start_hour or hour < self.config.night_end_hour)
            else 0.0
        )
        is_rush_hour = (
            1.0
            if (
                (self.config.morning_rush_start <= hour <= self.config.morning_rush_end)
                or (self.config.evening_rush_start <= hour <= self.config.evening_rush_end)
            )
            else 0.0
        )

        # Continuous cyclical representations
        sin_hour = float(math.sin(2.0 * math.pi * hour / 24.0))
        cos_hour = float(math.cos(2.0 * math.pi * hour / 24.0))
        sin_day = float(math.sin(2.0 * math.pi * day / 7.0))
        cos_day = float(math.cos(2.0 * math.pi * day / 7.0))

        return {
            "hour_of_day": hour,
            "day_of_week": day,
            "is_weekend": is_weekend,
            "is_night": is_night,
            "is_rush_hour": is_rush_hour,
            "month": month,
            "sin_hour": round(sin_hour, 4),
            "cos_hour": round(cos_hour, 4),
            "sin_day": round(sin_day, 4),
            "cos_day": round(cos_day, 4),
        }, True

    def extract_dataframe(
        self, df: pd.DataFrame, timestamp_column: str = "incident_datetime"
    ) -> pd.DataFrame:
        """Vectorized extraction of temporal features across a pandas DataFrame."""
        result = df.copy()

        if timestamp_column not in result.columns:
            # Impute defaults across all rows
            result["hour_of_day"] = 12.0
            result["day_of_week"] = 0.0
            result["is_weekend"] = 0.0
            result["is_night"] = 0.0
            result["is_rush_hour"] = 0.0
            result["sin_hour"] = 0.0
            result["cos_hour"] = 1.0
            result["sin_day"] = 0.0
            result["cos_day"] = 1.0
            result["temporal_valid"] = False
            return result

        series = pd.to_datetime(result[timestamp_column], errors="coerce", utc=True)
        valid_mask = series.notna()

        # Extract components for valid timestamps
        hours = series.dt.hour.fillna(12).astype(float)
        days = series.dt.weekday.fillna(0).astype(float)

        result["hour_of_day"] = hours
        result["day_of_week"] = days
        result["is_weekend"] = np.where(days.isin([5, 6]), 1.0, 0.0)
        result["is_night"] = np.where(
            (hours >= self.config.night_start_hour) | (hours < self.config.night_end_hour),
            1.0,
            0.0,
        )
        result["is_rush_hour"] = np.where(
            (hours.between(self.config.morning_rush_start, self.config.morning_rush_end))
            | (hours.between(self.config.evening_rush_start, self.config.evening_rush_end)),
            1.0,
            0.0,
        )

        result["sin_hour"] = np.sin(2.0 * np.pi * hours / 24.0).round(4)
        result["cos_hour"] = np.cos(2.0 * np.pi * hours / 24.0).round(4)
        result["sin_day"] = np.sin(2.0 * np.pi * days / 7.0).round(4)
        result["cos_day"] = np.cos(2.0 * np.pi * days / 7.0).round(4)
        result["temporal_valid"] = valid_mask

        return result

    @staticmethod
    def _normalize_datetime(val: Any) -> Optional[datetime]:
        if val is None or (isinstance(val, float) and math.isnan(val)):
            return None

        if isinstance(val, pd.Timestamp):
            dt = val.to_pydatetime()
            return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)

        if isinstance(val, datetime):
            return val if val.tzinfo else val.replace(tzinfo=timezone.utc)

        if isinstance(val, str):
            try:
                ts = pd.to_datetime(val, utc=True)
                if pd.notna(ts):
                    return ts.to_pydatetime()
            except Exception:
                return None

        return None
