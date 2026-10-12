package com.safepath.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for batch road segment risk prediction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchPredictRequestDto {

    private List<PredictRiskRequestDto> segments;
}
