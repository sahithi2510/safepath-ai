package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single feature attribution in a SHAP explanation response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactorContributionDto {

    private String name;

    private Double value;

    @JsonProperty("raw_value")
    private Double rawValue;

    private Double contribution;

    private String direction;

    private String description;

    @JsonProperty("is_imputed")
    private Boolean isImputed;
}
