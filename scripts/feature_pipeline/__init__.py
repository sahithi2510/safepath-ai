"""SafePath AI - Spatial & Temporal Feature Pipeline.

Derives feature vectors from valid, provenance-tracked crime incidents,
safe zones, road segments, and temporal contexts for machine learning risk prediction.
"""

from scripts.feature_pipeline.schemas import (
    FeaturePipelineConfig,
    SegmentFeatureRecord,
    FEATURE_NAMES,
)
from scripts.feature_pipeline.temporal import TemporalFeatureExtractor
from scripts.feature_pipeline.spatial import SpatialFeatureExtractor
from scripts.feature_pipeline.leakage_guard import TemporalBoundaryManager
from scripts.feature_pipeline.pipeline import FeaturePipeline

__all__ = [
    "FeaturePipelineConfig",
    "SegmentFeatureRecord",
    "FEATURE_NAMES",
    "TemporalFeatureExtractor",
    "SpatialFeatureExtractor",
    "TemporalBoundaryManager",
    "FeaturePipeline",
]
