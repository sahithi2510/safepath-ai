package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload from FastAPI for batch road segment risk prediction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPredictResponseDto {

    private List<PredictRiskResponseDto> predictions;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("model_type")
    private String modelType;

    @JsonProperty("total_segments")
    private Integer totalSegments;

    @JsonProperty("is_synthetic_demonstration")
    private Boolean isSyntheticDemonstration;

    private Boolean demo;
}
