package com.safepath.backend.service.routing;

import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.entity.RoutePreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AStarRoutingEngineTest {

    private AStarRoutingEngine routingEngine;

    @BeforeEach
    void setUp() {
        routingEngine = new AStarRoutingEngine();
    }

    /**
     * Constructs a test road network with two divergent paths between Node 1 (Start) and Node 4 (End):
     *
     * Path A (Fast / Dangerous):
     *   Node 1 -> Node 2 -> Node 4
     *   Total Length: 200m, Duration: 140s
     *   Risk Score: 0.95 (High Risk)
     *
     * Path B (Slow / Safe):
     *   Node 1 -> Node 3 -> Node 4
     *   Total Length: 600m, Duration: 420s
     *   Risk Score: 0.05 (Low Risk)
     */
    private Map<Long, List<RoadSegment>> buildDivergentNetwork(
            RoadSegment seg12, RoadSegment seg24, RoadSegment seg13, RoadSegment seg34) {

        Map<Long, List<RoadSegment>> graph = new HashMap<>();
        graph.put(1L, List.of(seg12, seg13));
        graph.put(2L, List.of(seg24));
        graph.put(3L, List.of(seg34));
        graph.put(4L, Collections.emptyList());
        return graph;
    }

    private RoadSegment createSegment(UUID id, Long start, Long end, double lengthMeters, double walkTimeS) {
        RoadSegment seg = new RoadSegment();
        seg.setId(id);
        seg.setStartNodeId(start);
        seg.setEndNodeId(end);
        seg.setLengthMeters(lengthMeters);
        seg.setEstimatedWalkTimeS(walkTimeS);
        seg.setIsOneway(false);
        seg.setIsLit(true);
        return seg;
    }

    @Test
    @DisplayName("FASTEST route prioritizes travel time over safety, choosing faster high-risk path")
    void testFastestRoute_PrioritizesTravelTime() {
        UUID id12 = UUID.randomUUID();
        UUID id24 = UUID.randomUUID();
        UUID id13 = UUID.randomUUID();
        UUID id34 = UUID.randomUUID();

        RoadSegment seg12 = createSegment(id12, 1L, 2L, 100.0, 70.0);
        RoadSegment seg24 = createSegment(id24, 2L, 4L, 100.0, 70.0);
        RoadSegment seg13 = createSegment(id13, 1L, 3L, 300.0, 210.0);
        RoadSegment seg34 = createSegment(id34, 3L, 4L, 300.0, 210.0);

        Map<Long, List<RoadSegment>> graph = buildDivergentNetwork(seg12, seg24, seg13, seg34);

        Map<String, Double> riskScores = Map.of(
                id12.toString(), 0.95,
                id24.toString(), 0.95,
                id13.toString(), 0.05,
                id34.toString(), 0.05
        );

        List<RoadSegment> path = routingEngine.findRoute(1L, 4L, RoutePreference.FASTEST, graph, riskScores);

        // FASTEST should choose Path A (via Node 2) because travel time is 140s vs 420s
        assertThat(path).hasSize(2);
        assertThat(path.get(0).getStartNodeId()).isEqualTo(1L);
        assertThat(path.get(0).getEndNodeId()).isEqualTo(2L);
        assertThat(path.get(1).getStartNodeId()).isEqualTo(2L);
        assertThat(path.get(1).getEndNodeId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("SAFEST route prioritizes safety over travel time, choosing slower low-risk path")
    void testSafestRoute_PrioritizesSafetyOverTime() {
        UUID id12 = UUID.randomUUID();
        UUID id24 = UUID.randomUUID();
        UUID id13 = UUID.randomUUID();
        UUID id34 = UUID.randomUUID();

        RoadSegment seg12 = createSegment(id12, 1L, 2L, 100.0, 70.0);
        RoadSegment seg24 = createSegment(id24, 2L, 4L, 100.0, 70.0);
        RoadSegment seg13 = createSegment(id13, 1L, 3L, 300.0, 210.0);
        RoadSegment seg34 = createSegment(id34, 3L, 4L, 300.0, 210.0);

        Map<Long, List<RoadSegment>> graph = buildDivergentNetwork(seg12, seg24, seg13, seg34);

        Map<String, Double> riskScores = Map.of(
                id12.toString(), 0.95,
                id24.toString(), 0.95,
                id13.toString(), 0.05,
                id34.toString(), 0.05
        );

        List<RoadSegment> path = routingEngine.findRoute(1L, 4L, RoutePreference.SAFEST, graph, riskScores);

        // SAFEST should choose Path B (via Node 3) because risk is 0.05 vs 0.95 despite longer distance
        assertThat(path).hasSize(2);
        assertThat(path.get(0).getStartNodeId()).isEqualTo(1L);
        assertThat(path.get(0).getEndNodeId()).isEqualTo(3L);
        assertThat(path.get(1).getStartNodeId()).isEqualTo(3L);
        assertThat(path.get(1).getEndNodeId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("BALANCED route produces a measurable trade-off cost between time and safety")
    void testBalancedRoute_CalculatesMeasurableCompromise() {
        UUID segId = UUID.randomUUID();
        RoadSegment seg = createSegment(segId, 1L, 2L, 500.0, 360.0);

        Map<String, Double> riskScores = Map.of(segId.toString(), 0.50);

        double fastestCost = routingEngine.calculateCost(seg, RoutePreference.FASTEST, riskScores);
        double balancedCost = routingEngine.calculateCost(seg, RoutePreference.BALANCED, riskScores);
        double safestCost = routingEngine.calculateCost(seg, RoutePreference.SAFEST, riskScores);

        // Verify distinct weights per mode:
        // FASTEST: alpha=0.8, beta=0.2, gamma=0.0
        // BALANCED: alpha=0.3, beta=0.2, gamma=0.5
        // SAFEST: alpha=0.1, beta=0.1, gamma=0.8
        assertThat(fastestCost).isNotEqualTo(balancedCost);
        assertThat(balancedCost).isNotEqualTo(safestCost);
        assertThat(fastestCost).isLessThan(balancedCost);
    }

    @Test
    @DisplayName("calculateCost never assigns 0.0 (safe) to unscored segments")
    void testCalculateCost_UnscoredSegmentsNeverZeroRisk() {
        UUID segId = UUID.randomUUID();
        RoadSegment litSeg = createSegment(segId, 1L, 2L, 100.0, 70.0);
        litSeg.setIsLit(true);

        RoadSegment unlitSeg = createSegment(UUID.randomUUID(), 1L, 2L, 100.0, 70.0);
        unlitSeg.setIsLit(false);

        // When riskScoresBySegment is empty
        double costLit = routingEngine.calculateCost(litSeg, RoutePreference.SAFEST, Collections.emptyMap());
        double costUnlit = routingEngine.calculateCost(unlitSeg, RoutePreference.SAFEST, Collections.emptyMap());

        // Unscored unlit segment cost must be strictly higher than unscored lit segment cost
        // Neither can evaluate to 0.0 risk
        assertThat(costLit).isGreaterThan(0.0);
        assertThat(costUnlit).isGreaterThan(costLit);
    }

    @Test
    @DisplayName("findRoute throws exception when no path exists between disconnected nodes")
    void testFindRoute_DisconnectedNetwork() {
        RoadSegment seg12 = createSegment(UUID.randomUUID(), 1L, 2L, 100.0, 70.0);
        Map<Long, List<RoadSegment>> graph = Map.of(
                1L, List.of(seg12),
                2L, Collections.emptyList(),
                99L, Collections.emptyList()
        );

        assertThatThrownBy(() -> routingEngine.findRoute(1L, 99L, RoutePreference.FASTEST, graph))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No route found between nodes 1 and 99");
    }
}
