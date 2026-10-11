"""SafePath AI - Data Leakage Prevention & Temporal Boundaries.

Enforces strict temporal and spatial separation to prevent data leakage in ML training:
1. Temporal Observation Boundary:
   When computing features for evaluation time T, ONLY historical crimes
   with incident_datetime < T are visible. Future crimes are strictly masked out.
2. Chronological Train/Val/Test Splitting:
   Partitions time series chronologically to prevent future information bleeding
   into past evaluation folds.
"""

from __future__ import annotations

import logging
from typing import Optional, Tuple

import pandas as pd

logger = logging.getLogger(__name__)


class TemporalBoundaryManager:
    """Manages temporal masking and chronological dataset partitioning."""

    @staticmethod
    def mask_future_crimes(
        crimes_df: pd.DataFrame,
        evaluation_time: pd.Timestamp,
        time_col: str = "incident_datetime",
    ) -> pd.DataFrame:
        """Filter out any crime record occurring at or after the evaluation time.

        Strictly prevents temporal data leakage.
        """
        if crimes_df.empty or time_col not in crimes_df.columns:
            return crimes_df

        times = pd.to_datetime(crimes_df[time_col], errors="coerce", utc=True)
        # Only retain crimes strictly before evaluation time
        mask = times.notna() & (times < evaluation_time)
        filtered = crimes_df[mask].copy()

        dropped_count = len(crimes_df) - len(filtered)
        if dropped_count > 0:
            logger.debug(
                "Leakage guard: masked out %d future crimes occurring after %s",
                dropped_count,
                evaluation_time.isoformat(),
            )
        return filtered

    @staticmethod
    def chronological_split(
        df: pd.DataFrame,
        time_col: str = "snapshot_time",
        train_frac: float = 0.70,
        val_frac: float = 0.15,
    ) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        """Perform a chronological train/validation/test partition.

        Avoids random shuffling leakage in temporal data.

        Returns:
            (train_df, val_df, test_df)
        """
        if df.empty:
            return df.copy(), df.copy(), df.copy()

        # Sort by timestamp
        sorted_df = df.copy()
        if time_col in sorted_df.columns:
            sorted_df["_sort_time"] = pd.to_datetime(
                sorted_df[time_col], errors="coerce", utc=True
            )
            sorted_df = sorted_df.sort_values("_sort_time").drop(columns=["_sort_time"])

        n = len(sorted_df)
        train_end = int(n * train_frac)
        val_end = int(n * (train_frac + val_frac))

        train_df = sorted_df.iloc[:train_end].copy()
        val_df = sorted_df.iloc[train_end:val_end].copy()
        test_df = sorted_df.iloc[val_end:].copy()

        logger.info(
            "Chronological split completed: Train=%d (%.1f%%), Val=%d (%.1f%%), Test=%d (%.1f%%)",
            len(train_df),
            train_frac * 100,
            len(val_df),
            val_frac * 100,
            len(test_df),
            (1.0 - train_frac - val_frac) * 100,
        )

        return train_df, val_df, test_df
