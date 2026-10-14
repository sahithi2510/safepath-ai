package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload from FastAPI for a single road segment risk prediction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PredictRiskResponseDto {

    @JsonProperty("segment_id")
    private String segmentId;

    @JsonProperty("prediction_timestamp")
    private String predictionTimestamp;

    @JsonProperty("raw_model_output")
    private Double rawModelOutput;

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("safety_score")
    private Double safetyScore;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("model_type")
    private String modelType;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("model_trained_at")
    private String modelTrainedAt;

    @JsonProperty("feature_completeness")
    private Double featureCompleteness;

    @JsonProperty("imputed_features")
    private List<String> imputedFeatures;

    @JsonProperty("is_synthetic_demonstration")
    private Boolean isSyntheticDemonstration;

    private Boolean demo;

    private String notes;
}
