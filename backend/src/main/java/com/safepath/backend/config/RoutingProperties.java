package com.safepath.backend.config;

import com.safepath.backend.entity.RoutePreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for multi-objective A* routing weights.
 *
 * IMPLEMENTATION CHOICE: Routing cost weights (alpha, beta, gamma) are application-defined
 * trade-off parameters satisfying alpha + beta + gamma = 1.0, where:
 *   alpha = weight for normalized travel time (walk_time / 3600.0)
 *   beta  = weight for normalized travel distance (length_meters / 10000.0)
 *   gamma = weight for normalized relative risk [0.0, 1.0]
 */
@Data
@Component
@ConfigurationProperties(prefix = "safepath.routing")
public class RoutingProperties {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteWeights {
        private double alpha; // time weight
        private double beta;  // distance weight
        private double gamma; // risk weight

        /**
         * Validates weights and returns normalized weights summing to 1.0.
         *
         * @return normalized RouteWeights where alpha + beta + gamma = 1.0
         * @throws IllegalArgumentException if any weight is negative, non-finite, or sum <= 0
         */
        public RouteWeights validate() {
            if (!Double.isFinite(alpha) || !Double.isFinite(beta) || !Double.isFinite(gamma)) {
                throw new IllegalArgumentException("Routing weights must be finite numbers: alpha=" + alpha + ", beta=" + beta + ", gamma=" + gamma);
            }
            if (alpha < 0.0 || beta < 0.0 || gamma < 0.0) {
                throw new IllegalArgumentException("Routing weights cannot be negative: alpha=" + alpha + ", beta=" + beta + ", gamma=" + gamma);
            }
            double sum = alpha + beta + gamma;
            if (sum <= 0.0) {
                throw new IllegalArgumentException("Sum of routing weights must be strictly positive: sum=" + sum);
            }
            if (Math.abs(sum - 1.0) > 1e-6) {
                // Normalize so alpha + beta + gamma = 1.0
                return new RouteWeights(alpha / sum, beta / sum, gamma / sum);
            }
            return this;
        }
    }

    private WeightsConfig weights = new WeightsConfig();

    @Data
    public static class WeightsConfig {
        private RouteWeights fastest = new RouteWeights(0.8, 0.2, 0.0);
        private RouteWeights balanced = new RouteWeights(0.3, 0.2, 0.5);
        private RouteWeights safest = new RouteWeights(0.1, 0.1, 0.8);
        private RouteWeights soloTraveller = new RouteWeights(0.15, 0.15, 0.7);
        private RouteWeights tourist = new RouteWeights(0.3, 0.3, 0.4);
        private RouteWeights elderly = new RouteWeights(0.1, 0.4, 0.5);
    }

    /**
     * Resolves the configured and validated weights for a given RoutePreference.
     *
     * @param preference route preference enum
     * @return validated RouteWeights
     */
    public RouteWeights getWeightsForPreference(RoutePreference preference) {
        if (preference == null) {
            return (weights.getBalanced() != null) ? weights.getBalanced().validate() : new RouteWeights(0.3, 0.2, 0.5);
        }
        RouteWeights w;
        switch (preference) {
            case FASTEST:
                w = weights.getFastest();
                break;
            case SAFEST:
                w = weights.getSafest();
                break;
            case SOLO_TRAVELLER:
                w = weights.getSoloTraveller();
                break;
            case TOURIST:
                w = weights.getTourist();
                break;
            case ELDERLY:
                w = weights.getElderly();
                break;
            case STANDARD:
            case BALANCED:
            default:
                w = weights.getBalanced();
                break;
        }
        return (w != null) ? w.validate() : new RouteWeights(0.3, 0.2, 0.5);
    }
}
