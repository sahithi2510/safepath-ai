package com.safepath.backend.service.ml;

import com.safepath.backend.dto.ml.*;

import java.util.List;
import java.util.Map;

/**
 * Client interface for communicating with the FastAPI ML inference service.
 */
public interface MlServiceClient {

    /**
     * Checks the operational health and model readiness of the ML service.
     *
     * @return health response containing active model and loaded status
     */
    MlHealthResponseDto checkHealth();

    /**
     * Predicts relative risk and safety score for a single road segment.
     *
     * @param segmentId unique identifier for the road segment
     * @param features  15-feature input vector
     * @return prediction response containing risk score and safety score
     */
    PredictRiskResponseDto predictRisk(String segmentId, RiskFeaturesDto features);

    /**
     * Predicts relative risk and safety scores for a batch of road segments.
     * Chunks segments into requests adhering to configured batch limits.
     *
     * @param segments list of segment prediction requests
     * @return map of predictions keyed strictly by segmentId
     */
    Map<String, PredictRiskResponseDto> predictRiskBatch(List<PredictRiskRequestDto> segments);

    /**
     * Requests SHAP-based feature attributions for a given segment prediction.
     *
     * @param segmentId unique identifier for the road segment
     * @param features  15-feature input vector
     * @return explain response with additive Shapley values and disclaimers
     */
    MlExplainResponseDto explain(String segmentId, RiskFeaturesDto features);
}
