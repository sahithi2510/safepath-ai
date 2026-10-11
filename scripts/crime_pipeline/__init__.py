"""SafePath AI - Crime Data Ingestion Pipeline.

Modular pipeline for importing, cleaning, normalizing, validating,
and persisting crime incident data with strict geographic precision tracking.
"""

from scripts.crime_pipeline.schemas import (
    LocationPrecision,
    RawCrimeRecord,
    NormalizedCrimeRecord,
    IngestionSummary,
    SEVERITY_MAPPINGS,
)
from scripts.crime_pipeline.cleaner import CrimeDataCleaner
from scripts.crime_pipeline.geocoder import BaseGeocoder, NominatimGeocoder, MockGeocoder
from scripts.crime_pipeline.db import DatabaseManager
from scripts.crime_pipeline.ingester import CrimeDataIngester

__all__ = [
    "LocationPrecision",
    "RawCrimeRecord",
    "NormalizedCrimeRecord",
    "IngestionSummary",
    "SEVERITY_MAPPINGS",
    "CrimeDataCleaner",
    "BaseGeocoder",
    "NominatimGeocoder",
    "MockGeocoder",
    "DatabaseManager",
    "CrimeDataIngester",
]
