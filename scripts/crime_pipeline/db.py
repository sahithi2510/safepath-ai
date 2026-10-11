"""SafePath AI - PostGIS Database Integration for Crime Data.

Manages connection pooling, transactional batch insertion, duplicate detection,
and PostGIS spatial point generation with strict environment configuration.
"""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

from dotenv import load_dotenv
import psycopg2
from psycopg2.extras import Json

from scripts.crime_pipeline.schemas import (
    LocationPrecision,
    NormalizedCrimeRecord,
)

logger = logging.getLogger(__name__)


class DatabaseManager:
    """PostgreSQL / PostGIS connection and ingestion manager.

    Never hardcodes credentials — reads strictly from environment variables or .env.
    """

    def __init__(
        self,
        host: Optional[str] = None,
        port: Optional[int] = None,
        database: Optional[str] = None,
        user: Optional[str] = None,
        password: Optional[str] = None,
        env_file: Optional[str] = None,
    ):
        # Load .env if present
        if env_file and Path(env_file).exists():
            load_dotenv(env_file)
        elif Path(".env").exists():
            load_dotenv(".env")

        self.host = host or os.getenv("POSTGRES_HOST", "localhost")
        self.port = int(port or os.getenv("POSTGRES_PORT", "5432"))
        self.database = database or os.getenv("POSTGRES_DB", "safepath_db")
        self.user = user or os.getenv("POSTGRES_USER", "safepath_user")
        self.password = password or os.getenv("POSTGRES_PASSWORD", "")

    def get_connection(self):
        """Establish a psycopg2 connection to PostgreSQL."""
        return psycopg2.connect(
            host=self.host,
            port=self.port,
            dbname=self.database,
            user=self.user,
            password=self.password,
            connect_timeout=5,
        )

    def test_connection(self) -> Tuple[bool, str]:
        """Test database connectivity and PostGIS extension presence."""
        try:
            with self.get_connection() as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT PostGIS_Version();")
                    postgis_ver = cur.fetchone()[0]
                    cur.execute("SELECT to_regclass('crime_incidents');")
                    table_exists = cur.fetchone()[0] is not None
                    return (
                        True,
                        f"Connected successfully. PostGIS: {postgis_ver}. Table 'crime_incidents': {'Present' if table_exists else 'Missing'}",
                    )
        except Exception as exc:
            return False, f"Database connection failed: {exc}"

    def fetch_existing_external_ids(self, source: str) -> Set[str]:
        """Fetch all external_id values already ingested for this source.

        Ensures idempotent and repeatable ingestion.
        """
        try:
            with self.get_connection() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT raw_source_data->>'external_id'
                        FROM crime_incidents
                        WHERE source = %s AND raw_source_data ? 'external_id'
                        """,
                        (source,),
                    )
                    rows = cur.fetchall()
                    return {row[0] for row in rows if row[0]}
        except Exception as exc:
            logger.warning("Could not fetch existing external IDs for source '%s': %s", source, exc)
            return set()

    def insert_batch(
        self,
        records: List[NormalizedCrimeRecord],
        dry_run: bool = False,
        update_existing: bool = False,
    ) -> Tuple[int, int, int]:
        """Transactional batch insertion of normalized crime records into PostGIS.

        Returns:
            Tuple of (inserted_count, updated_count, skipped_count)
        """
        if not records:
            return 0, 0, 0

        source = records[0].source
        existing_ids = self.fetch_existing_external_ids(source) if not dry_run else set()

        to_insert: List[NormalizedCrimeRecord] = []
        to_update: List[NormalizedCrimeRecord] = []
        skipped = 0

        # Local deduplication within current batch
        seen_in_batch: Set[str] = set()

        for rec in records:
            ext_id = rec.external_id
            if ext_id:
                if ext_id in seen_in_batch:
                    skipped += 1
                    continue
                seen_in_batch.add(ext_id)

                if ext_id in existing_ids:
                    if update_existing:
                        to_update.append(rec)
                    else:
                        skipped += 1
                    continue

            to_insert.append(rec)

        if dry_run:
            logger.info(
                "[DRY-RUN] Would insert %d, update %d, skip %d records.",
                len(to_insert),
                len(to_update),
                skipped,
            )
            return len(to_insert), len(to_update), skipped

        inserted_count = 0
        updated_count = 0

        insert_sql = """
            INSERT INTO crime_incidents (
                crime_type,
                crime_subtype,
                severity,
                incident_datetime,
                incident_date,
                source,
                latitude,
                longitude,
                geometry,
                location_precision,
                location_confidence,
                original_address,
                district,
                locality,
                police_station_name,
                city,
                state,
                country,
                raw_source_data
            ) VALUES (
                %(crime_type)s,
                %(crime_subtype)s,
                %(severity)s,
                %(incident_datetime)s,
                %(incident_date)s,
                %(source)s,
                %(latitude)s,
                %(longitude)s,
                CASE
                    WHEN %(longitude)s IS NOT NULL AND %(latitude)s IS NOT NULL
                    THEN ST_SetSRID(ST_MakePoint(%(longitude)s, %(latitude)s), 4326)
                    ELSE NULL
                END,
                %(location_precision)s::location_precision_enum,
                %(location_confidence)s,
                %(original_address)s,
                %(district)s,
                %(locality)s,
                %(police_station_name)s,
                %(city)s,
                %(state)s,
                %(country)s,
                %(raw_source_data)s
            )
        """

        update_sql = """
            UPDATE crime_incidents
            SET
                crime_type = %(crime_type)s,
                crime_subtype = %(crime_subtype)s,
                severity = %(severity)s,
                incident_datetime = %(incident_datetime)s,
                incident_date = %(incident_date)s,
                latitude = %(latitude)s,
                longitude = %(longitude)s,
                geometry = CASE
                    WHEN %(longitude)s IS NOT NULL AND %(latitude)s IS NOT NULL
                    THEN ST_SetSRID(ST_MakePoint(%(longitude)s, %(latitude)s), 4326)
                    ELSE NULL
                END,
                location_precision = %(location_precision)s::location_precision_enum,
                location_confidence = %(location_confidence)s,
                original_address = %(original_address)s,
                district = %(district)s,
                locality = %(locality)s,
                police_station_name = %(police_station_name)s,
                city = %(city)s,
                state = %(state)s,
                country = %(country)s,
                raw_source_data = %(raw_source_data)s
            WHERE source = %(source)s AND raw_source_data->>'external_id' = %(external_id)s
        """

        with self.get_connection() as conn:
            with conn.cursor() as cur:
                # 1. Execute Inserts
                for rec in to_insert:
                    params = self._record_to_params(rec)
                    cur.execute(insert_sql, params)
                    inserted_count += 1

                # 2. Execute Updates
                for rec in to_update:
                    params = self._record_to_params(rec)
                    params["external_id"] = rec.external_id
                    cur.execute(update_sql, params)
                    updated_count += 1

            conn.commit()

        return inserted_count, updated_count, skipped

    @staticmethod
    def _record_to_params(rec: NormalizedCrimeRecord) -> Dict[str, Any]:
        """Convert a normalized record into parameter dict for SQL execution."""
        return {
            "crime_type": rec.crime_type,
            "crime_subtype": rec.crime_subtype,
            "severity": rec.severity,
            "incident_datetime": rec.incident_datetime,
            "incident_date": rec.incident_date,
            "source": rec.source,
            "latitude": rec.latitude,
            "longitude": rec.longitude,
            "location_precision": (
                rec.location_precision.value
                if hasattr(rec.location_precision, "value")
                else str(rec.location_precision)
            ),
            "location_confidence": rec.location_confidence,
            "original_address": rec.original_address,
            "district": rec.district,
            "locality": rec.locality,
            "police_station_name": rec.police_station_name,
            "city": rec.city,
            "state": rec.state,
            "country": rec.country,
            "raw_source_data": Json(rec.raw_source_data),
        }
