package com.safepath.backend.config;

import com.safepath.backend.entity.RoutePreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingPropertiesTest {

    @Test
    @DisplayName("RoutingProperties provides valid default weights for all profiles")
    void testDefaultWeightsValid() {
        RoutingProperties properties = new RoutingProperties();

        // FASTEST mode is independent of ML (gamma = 0.0)
        RoutingProperties.RouteWeights fastest = properties.getWeightsForPreference(RoutePreference.FASTEST);
        assertThat(fastest.getAlpha()).isEqualTo(0.8);
        assertThat(fastest.getBeta()).isEqualTo(0.2);
        assertThat(fastest.getGamma()).isEqualTo(0.0);
        assertThat(fastest.getAlpha() + fastest.getBeta() + fastest.getGamma()).isEqualTo(1.0);

        // BALANCED mode balances time, distance, and risk
        RoutingProperties.RouteWeights balanced = properties.getWeightsForPreference(RoutePreference.BALANCED);
        assertThat(balanced.getAlpha()).isEqualTo(0.3);
        assertThat(balanced.getBeta()).isEqualTo(0.2);
        assertThat(balanced.getGamma()).isEqualTo(0.5);

        // SAFEST mode prioritizes safety (gamma = 0.8)
        RoutingProperties.RouteWeights safest = properties.getWeightsForPreference(RoutePreference.SAFEST);
        assertThat(safest.getAlpha()).isEqualTo(0.1);
        assertThat(safest.getBeta()).isEqualTo(0.1);
        assertThat(safest.getGamma()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("RouteWeights normalizes weights if sum deviates from 1.0")
    void testWeightNormalization() {
        RoutingProperties.RouteWeights weights = new RoutingProperties.RouteWeights(2.0, 2.0, 6.0);
        RoutingProperties.RouteWeights validated = weights.validate();

        assertThat(validated.getAlpha()).isEqualTo(0.2);
        assertThat(validated.getBeta()).isEqualTo(0.2);
        assertThat(validated.getGamma()).isEqualTo(0.6);
        assertThat(validated.getAlpha() + validated.getBeta() + validated.getGamma()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("RouteWeights rejects negative weights with IllegalArgumentException")
    void testNegativeWeightsRejected() {
        RoutingProperties.RouteWeights weights = new RoutingProperties.RouteWeights(-0.1, 0.5, 0.6);

        assertThatThrownBy(weights::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("RouteWeights rejects non-finite weights with IllegalArgumentException")
    void testNonFiniteWeightsRejected() {
        RoutingProperties.RouteWeights nanWeights = new RoutingProperties.RouteWeights(Double.NaN, 0.5, 0.5);
        assertThatThrownBy(nanWeights::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be finite numbers");

        RoutingProperties.RouteWeights infWeights = new RoutingProperties.RouteWeights(0.5, Double.POSITIVE_INFINITY, 0.5);
        assertThatThrownBy(infWeights::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be finite numbers");
    }

    @Test
    @DisplayName("RouteWeights rejects zero or negative sum with IllegalArgumentException")
    void testZeroSumWeightsRejected() {
        RoutingProperties.RouteWeights zeroWeights = new RoutingProperties.RouteWeights(0.0, 0.0, 0.0);
        assertThatThrownBy(zeroWeights::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be strictly positive");
    }
}
