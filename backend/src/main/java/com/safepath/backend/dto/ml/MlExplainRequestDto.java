package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for SHAP explanation (/explain).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlExplainRequestDto {

    @JsonProperty("segment_id")
    private String segmentId;

    private RiskFeaturesDto features;
}
