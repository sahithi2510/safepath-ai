#!/usr/bin/env python3
"""SafePath AI - Crime Data Ingestion CLI.

Ingests raw crime datasets into PostgreSQL/PostGIS with geographic precision tracking.
Enforces non-negotiable data integrity rules defined in AGENTS.md.

Usage Examples:
    # 1. Dry run validation on synthetic test fixture (no DB required)
    python scripts/ingest_crime_data.py --file scripts/fixtures/mock_crime_records.csv --dry-run

    # 2. Ingest real dataset into PostgreSQL/PostGIS
    python scripts/ingest_crime_data.py --file datasets/raw/ncrb_crime_2022.csv --source ncrb_2022

    # 3. Ingest with Nominatim geocoding for street addresses
    python scripts/ingest_crime_data.py --file datasets/raw/police_fir.csv --geocode --batch-size 250

    # 4. Check DB connection
    python scripts/ingest_crime_data.py --test-db
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from pathlib import Path

# Add project root to sys.path so scripts can be imported cleanly
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from scripts.crime_pipeline.cleaner import CrimeDataCleaner
from scripts.crime_pipeline.db import DatabaseManager
from scripts.crime_pipeline.geocoder import NominatimGeocoder
from scripts.crime_pipeline.ingester import CrimeDataIngester
from scripts.crime_pipeline.schemas import IngestionSummary


def setup_logging(verbose: bool = False):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def print_summary_table(summary: IngestionSummary):
    """Print a clean ASCII summary report of the ingestion run."""
    border = "=" * 65
    divider = "-" * 65

    print(f"\n{border}")
    print(f"  SafePath AI -- Crime Data Ingestion Report")
    print(f"  Source Dataset: {summary.source_dataset}")
    print(f"  Execution Mode: {'DRY-RUN (No DB write)' if summary.dry_run else 'LIVE PERSISTENCE'}")
    print(border)
    print(f"  {'Metric':<35} | {'Count':<20}")
    print(divider)
    print(f"  {'Total Records Read':<35} | {summary.total_records:<20}")
    print(f"  {'Valid Records Processed':<35} | {summary.valid_records:<20}")
    print(f"  {'Records Inserted':<35} | {summary.inserted_records:<20}")
    print(f"  {'Records Updated':<35} | {summary.updated_records:<20}")
    print(f"  {'Duplicates Skipped':<35} | {summary.duplicate_skipped:<20}")
    print(f"  {'Validation Errors':<35} | {summary.validation_errors:<20}")
    print(f"  {'Addresses Geocoded':<35} | {summary.geocoded_records:<20}")
    print(f"  {'Geocoding Failures':<35} | {summary.geocoding_failures:<20}")
    print(f"  {'Execution Time':<35} | {summary.duration_seconds:.2f}s")
    print(f"  Location Precision Breakdown (Taxonomy per AGENTS.md Section 2.3):")
    for prec, count in summary.precision_breakdown.items():
        can_use_ml = "[Eligible for Road-ML]" if prec in ("EXACT_COORDINATE", "ADDRESS_GEOCODED") else "[Coarse: No Point Coords]"
        print(f"    - {prec:<20}: {count:<6} {can_use_ml}")
    print(border)

    if summary.error_messages:
        print("\n  Sample Errors Encountered:")
        for err in summary.error_messages[:5]:
            print(f"    ! {err}")
        if len(summary.error_messages) > 5:
            print(f"    ... and {len(summary.error_messages) - 5} more.")
        print(border)


def main():
    parser = argparse.ArgumentParser(
        description="SafePath AI Crime Data Ingestion CLI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--file",
        type=str,
        help="Path to crime dataset file (.csv, .json, .jsonl)",
    )
    parser.add_argument(
        "--source",
        type=str,
        default=None,
        help="Source dataset identifier (e.g. ncrb_delhi_2022)",
    )
    parser.add_argument(
        "--geocode",
        action="store_true",
        help="Enable Nominatim geocoding for unlocated addresses",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate, clean, and report without writing to PostGIS",
    )
    parser.add_argument(
        "--update",
        action="store_true",
        help="Update existing records when duplicate external_id is detected",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=500,
        help="Batch size for database insertions (default: 500)",
    )
    parser.add_argument(
        "--test-db",
        action="store_true",
        help="Test connection to PostgreSQL/PostGIS and exit",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose debug logging",
    )

    args = parser.parse_args()
    setup_logging(args.verbose)

    db_manager = DatabaseManager()

    if args.test_db:
        success, message = db_manager.test_connection()
        print(f"\n[Database Test] {'SUCCESS' if success else 'FAILURE'}: {message}\n")
        sys.exit(0 if success else 1)

    if not args.file:
        parser.print_help()
        print("\nError: Please provide --file <path> or use --test-db.")
        sys.exit(1)

    file_path = Path(args.file)
    if not file_path.exists():
        print(f"\nError: File not found: {file_path}")
        sys.exit(1)

    # Initialize components
    cleaner = CrimeDataCleaner()
    geocoder = NominatimGeocoder() if args.geocode else None

    # In dry-run, db_manager is optional
    active_db = None if args.dry_run else db_manager

    # Test DB connection before attempting live run
    if not args.dry_run:
        success, msg = db_manager.test_connection()
        if not success:
            print(f"\n[Warning] Database is not currently reachable: {msg}")
            print("Falling back to --dry-run mode to complete data cleaning and validation.\n")
            args.dry_run = True
            active_db = None

    ingester = CrimeDataIngester(
        cleaner=cleaner,
        db_manager=active_db,
        geocoder=geocoder,
    )

    try:
        summary = ingester.ingest_file(
            file_path=file_path,
            source=args.source,
            enable_geocoding=args.geocode,
            batch_size=args.batch_size,
            dry_run=args.dry_run,
            update_existing=args.update,
        )
        print_summary_table(summary)
    except Exception as exc:
        logging.error("Fatal ingestion error: %s", exc, exc_info=args.verbose)
        sys.exit(1)


if __name__ == "__main__":
    main()
