package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Health and readiness status response from FastAPI ML service (/health).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlHealthResponseDto {

    private String status;

    @JsonProperty("models_loaded")
    private Boolean modelsLoaded;

    @JsonProperty("active_model")
    private String activeModel;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("model_trained_at")
    private String modelTrainedAt;

    @JsonProperty("is_synthetic_demonstration")
    private Boolean isSyntheticDemonstration;

    private String detail;
}
