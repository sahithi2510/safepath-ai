"""SafePath AI - Crime Data Ingestion Orchestrator.

Coordinates reading, validating, geocoding, and batch persisting crime data.
Provides comprehensive data-quality tracking and execution summaries.
"""

from __future__ import annotations

import csv
from datetime import datetime, timezone
import json
import logging
from pathlib import Path
import time
from typing import Any, Dict, Iterator, List, Optional, Union

from scripts.crime_pipeline.cleaner import CrimeDataCleaner
from scripts.crime_pipeline.db import DatabaseManager
from scripts.crime_pipeline.geocoder import BaseGeocoder, NominatimGeocoder
from scripts.crime_pipeline.schemas import (
    IngestionSummary,
    LocationPrecision,
    NormalizedCrimeRecord,
)

logger = logging.getLogger(__name__)


class CrimeDataIngester:
    """Orchestrates end-to-end ingestion of crime datasets into PostGIS."""

    def __init__(
        self,
        cleaner: Optional[CrimeDataCleaner] = None,
        db_manager: Optional[DatabaseManager] = None,
        geocoder: Optional[BaseGeocoder] = None,
    ):
        self.cleaner = cleaner or CrimeDataCleaner()
        self.db_manager = db_manager
        self.geocoder = geocoder

    def ingest_file(
        self,
        file_path: Union[str, Path],
        source: Optional[str] = None,
        enable_geocoding: bool = False,
        batch_size: int = 500,
        dry_run: bool = False,
        update_existing: bool = False,
    ) -> IngestionSummary:
        """Ingest a CSV or JSON crime dataset with full validation and provenance.

        Args:
            file_path: Path to dataset file.
            source: Source dataset identifier (e.g., 'ncrb_delhi_2022', 'synthetic_test_fixture').
            enable_geocoding: Whether to attempt Nominatim geocoding for un-geocoded addresses.
            batch_size: Number of records to insert per transaction batch.
            dry_run: If True, validate and prepare data without writing to database.
            update_existing: If True, update existing records instead of skipping duplicates.

        Returns:
            IngestionSummary containing counts, precision breakdown, and errors.
        """
        start_time = time.time()
        path = Path(file_path)
        if not path.exists():
            raise FileNotFoundError(f"Dataset file not found: {path}")

        source_name = source or path.stem
        summary = IngestionSummary(
            source_dataset=source_name,
            dry_run=dry_run,
        )

        geocoder_instance = self.geocoder
        if enable_geocoding and geocoder_instance is None:
            geocoder_instance = NominatimGeocoder()

        precision_counts: Dict[str, int] = {p.value: 0 for p in LocationPrecision}
        batch: List[NormalizedCrimeRecord] = []

        logger.info(
            "Starting ingestion of '%s' (source='%s', dry_run=%s, geocode=%s)",
            path,
            source_name,
            dry_run,
            enable_geocoding,
        )

        record_iterator = self._read_records(path)

        for raw_record in record_iterator:
            summary.total_records += 1

            try:
                # 1. Clean & validate raw fields
                cleaned_record = self.cleaner.clean_record(
                    raw_record, source_override=source_name
                )

                # 2. Geocoding if enabled and record has address but no coordinates
                if (
                    enable_geocoding
                    and geocoder_instance is not None
                    and cleaned_record.latitude is None
                    and cleaned_record.original_address
                ):
                    geo_res = geocoder_instance.geocode(
                        query=cleaned_record.original_address,
                        city=cleaned_record.city,
                        state=cleaned_record.state,
                        country=cleaned_record.country,
                    )
                    if geo_res.success and geo_res.latitude is not None:
                        summary.geocoded_records += 1
                        cleaned_record.latitude = geo_res.latitude
                        cleaned_record.longitude = geo_res.longitude
                        cleaned_record.location_precision = LocationPrecision.ADDRESS_GEOCODED
                        cleaned_record.location_confidence = geo_res.confidence
                        # Record geocoding provenance in raw source metadata
                        cleaned_record.raw_source_data["geocoding_provenance"] = geo_res.to_dict()
                    else:
                        summary.geocoding_failures += 1
                        # Retain coarse precision and NULL coordinates per AGENTS.md §2.2
                        cleaned_record.raw_source_data["geocoding_failure"] = {
                            "query": cleaned_record.original_address,
                            "error": geo_res.error_message,
                        }

                # Update precision statistics
                prec_val = (
                    cleaned_record.location_precision.value
                    if hasattr(cleaned_record.location_precision, "value")
                    else str(cleaned_record.location_precision)
                )
                precision_counts[prec_val] = precision_counts.get(prec_val, 0) + 1

                summary.valid_records += 1
                batch.append(cleaned_record)

                # Flush batch
                if len(batch) >= batch_size:
                    self._flush_batch(batch, summary, dry_run, update_existing)
                    batch = []

            except Exception as exc:
                summary.validation_errors += 1
                err_msg = f"Record #{summary.total_records} error: {exc}"
                logger.warning(err_msg)
                if len(summary.error_messages) < 50:
                    summary.error_messages.append(err_msg)

        # Flush remaining records
        if batch:
            self._flush_batch(batch, summary, dry_run, update_existing)

        summary.precision_breakdown = precision_counts
        summary.duration_seconds = round(time.time() - start_time, 2)

        logger.info(
            "Ingestion completed: %d total, %d valid, %d inserted, %d skipped, %d errors (%.2fs)",
            summary.total_records,
            summary.valid_records,
            summary.inserted_records,
            summary.duplicate_skipped,
            summary.validation_errors,
            summary.duration_seconds,
        )

        return summary

    def _flush_batch(
        self,
        batch: List[NormalizedCrimeRecord],
        summary: IngestionSummary,
        dry_run: bool,
        update_existing: bool,
    ):
        """Persist batch via DatabaseManager or record simulated counts in dry-run."""
        if not batch:
            return

        if self.db_manager is not None:
            ins, upd, skip = self.db_manager.insert_batch(
                batch, dry_run=dry_run, update_existing=update_existing
            )
            summary.inserted_records += ins
            summary.updated_records += upd
            summary.duplicate_skipped += skip
        else:
            # When db_manager is omitted (e.g., in offline validation mode)
            if dry_run:
                summary.inserted_records += len(batch)
            else:
                summary.inserted_records += len(batch)

    def _read_records(self, path: Path) -> Iterator[Dict[str, Any]]:
        """Read records from CSV or JSON file handling encoding variations."""
        suffix = path.suffix.lower()

        if suffix == ".csv":
            # Detect encoding and ignore comment lines starting with '#'
            for encoding in ("utf-8-sig", "utf-8", "latin-1"):
                try:
                    with open(path, mode="r", encoding=encoding, errors="replace") as f:
                        lines = [line for line in f if not line.strip().startswith("#")]
                        if not lines:
                            return
                        reader = csv.DictReader(lines)
                        for row in reader:
                            # Strip whitespace from keys and values
                            cleaned_row = {
                                k.strip(): v.strip() if isinstance(v, str) else v
                                for k, v in row.items()
                                if k is not None
                            }
                            yield cleaned_row
                    return
                except UnicodeDecodeError:
                    continue

        elif suffix in (".json", ".jsonl"):
            with open(path, mode="r", encoding="utf-8") as f:
                content = f.read().strip()
                if content.startswith("["):
                    # JSON array
                    data = json.loads(content)
                    for item in data:
                        if isinstance(item, dict):
                            yield item
                else:
                    # JSON Lines
                    for line in content.splitlines():
                        if line.strip() and not line.strip().startswith("#"):
                            yield json.loads(line)
        else:
            raise ValueError(f"Unsupported dataset file format: '{suffix}'. Expected .csv, .json, or .jsonl")
