package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for SHAP explanation (/explain).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlExplainResponseDto {

    @JsonProperty("segment_id")
    private String segmentId;

    @JsonProperty("raw_model_output")
    private Double rawModelOutput;

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("safety_score")
    private Double safetyScore;

    @JsonProperty("base_risk")
    private Double baseRisk;

    @JsonProperty("model_type")
    private String modelType;

    @JsonProperty("model_version")
    private String modelVersion;

    private List<FactorContributionDto> factors;

    @JsonProperty("reconstruction_valid")
    private Boolean reconstructionValid;

    @JsonProperty("reconstruction_error")
    private Double reconstructionError;

    @JsonProperty("is_synthetic_demonstration")
    private Boolean isSyntheticDemonstration;

    private String disclaimer;

    @JsonProperty("causality_warning")
    private String causalityWarning;
}
