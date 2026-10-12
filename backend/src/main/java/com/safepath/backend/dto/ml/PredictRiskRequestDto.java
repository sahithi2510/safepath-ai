package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for predicting risk on a single road segment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictRiskRequestDto {

    @JsonProperty("segment_id")
    private String segmentId;

    private RiskFeaturesDto features;
}
