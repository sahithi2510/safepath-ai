package com.safepath.backend.service.routing;

import com.safepath.backend.config.RoutingProperties;
import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.exception.NoRouteFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AStarRoutingEngineTest {

    private AStarRoutingEngine routingEngine;
    private RoutingProperties routingProperties;

    @BeforeEach
    void setUp() {
        routingProperties = new RoutingProperties();
        routingEngine = new AStarRoutingEngine(routingProperties);
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
        assertThat(balancedCost).isLessThan(safestCost);
    }

    @Test
    @DisplayName("Admissible zero heuristic guarantees non-overestimating monotonic lower bound")
    void testAdmissibleZeroHeuristic() {
        // Zero heuristic is formally admissible: 0.0 <= true_remaining_cost for any non-negative edge costs
        double h1 = routingEngine.heuristic(1L, 4L);
        double h2 = routingEngine.heuristic(100L, 999L);

        assertThat(h1).isEqualTo(0.0);
        assertThat(h2).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Deterministic tie-breaking produces consistent route when paths have equal costs")
    void testDeterministicTieBreaking_EqualCostRoutes() {
        // Two parallel paths between Node 1 and Node 4:
        // Path A: Node 1 -> Node 2 -> Node 4
        // Path B: Node 1 -> Node 3 -> Node 4
        // Both paths have identical length (200m), time (140s), and risk (0.3)
        UUID id12 = UUID.randomUUID();
        UUID id24 = UUID.randomUUID();
        UUID id13 = UUID.randomUUID();
        UUID id34 = UUID.randomUUID();

        RoadSegment seg12 = createSegment(id12, 1L, 2L, 100.0, 70.0);
        RoadSegment seg24 = createSegment(id24, 2L, 4L, 100.0, 70.0);
        RoadSegment seg13 = createSegment(id13, 1L, 3L, 100.0, 70.0);
        RoadSegment seg34 = createSegment(id34, 3L, 4L, 100.0, 70.0);

        Map<Long, List<RoadSegment>> graph = buildDivergentNetwork(seg12, seg24, seg13, seg34);
        Map<String, Double> riskScores = Map.of(
                id12.toString(), 0.3,
                id24.toString(), 0.3,
                id13.toString(), 0.3,
                id34.toString(), 0.3
        );

        // Run multiple times to verify 100% determinism
        List<RoadSegment> path1 = routingEngine.findRoute(1L, 4L, RoutePreference.BALANCED, graph, riskScores);
        List<RoadSegment> path2 = routingEngine.findRoute(1L, 4L, RoutePreference.BALANCED, graph, riskScores);

        assertThat(path1).isNotEmpty();
        assertThat(path1).hasSize(2);
        // Both invocations select the exact same path due to node-ID deterministic tie-breaking
        assertThat(path1.get(0).getEndNodeId()).isEqualTo(path2.get(0).getEndNodeId());
        // Since Node 2 has smaller ID than Node 3, Path A is deterministically preferred
        assertThat(path1.get(0).getEndNodeId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findRoute handles trivial path when startNodeId equals endNodeId")
    void testTrivialPath_StartEqualsEnd() {
        Map<Long, List<RoadSegment>> graph = Map.of(1L, Collections.emptyList());

        List<RoadSegment> route = routingEngine.findRoute(1L, 1L, RoutePreference.BALANCED, graph);
        assertThat(route).isEmpty();
    }

    @Test
    @DisplayName("findRoute avoids infinite loops on cyclic graphs via closedSet settlement")
    void testCyclicGraph_AvoidsInfiniteLoop() {
        // Cyclic graph: Node 1 -> Node 2 -> Node 3 -> Node 1, and Node 3 -> Node 4
        RoadSegment seg12 = createSegment(UUID.randomUUID(), 1L, 2L, 100.0, 70.0);
        RoadSegment seg23 = createSegment(UUID.randomUUID(), 2L, 3L, 100.0, 70.0);
        RoadSegment seg31 = createSegment(UUID.randomUUID(), 3L, 1L, 100.0, 70.0);
        RoadSegment seg34 = createSegment(UUID.randomUUID(), 3L, 4L, 100.0, 70.0);

        Map<Long, List<RoadSegment>> graph = new HashMap<>();
        graph.put(1L, List.of(seg12));
        graph.put(2L, List.of(seg23));
        graph.put(3L, List.of(seg31, seg34));
        graph.put(4L, Collections.emptyList());

        List<RoadSegment> path = routingEngine.findRoute(1L, 4L, RoutePreference.BALANCED, graph);

        assertThat(path).hasSize(3);
        assertThat(path.get(0).getStartNodeId()).isEqualTo(1L);
        assertThat(path.get(1).getStartNodeId()).isEqualTo(2L);
        assertThat(path.get(2).getStartNodeId()).isEqualTo(3L);
        assertThat(path.get(2).getEndNodeId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("calculateCost safely clamps invalid, negative, or non-finite edge risks")
    void testCalculateCost_NonFiniteAndOutOfRangeRisks() {
        UUID segId = UUID.randomUUID();
        RoadSegment seg = createSegment(segId, 1L, 2L, 100.0, 70.0);

        // Score > 1.0 clamped to 1.0
        double costHigh = routingEngine.calculateCost(seg, RoutePreference.SAFEST, Map.of(segId.toString(), 2.5));
        // Score < 0.0 clamped to 0.0
        double costLow = routingEngine.calculateCost(seg, RoutePreference.SAFEST, Map.of(segId.toString(), -0.5));
        // NaN defaults to neutral 0.5
        double costNan = routingEngine.calculateCost(seg, RoutePreference.SAFEST, Map.of(segId.toString(), Double.NaN));
        // Infinite defaults to neutral 0.5
        double costInf = routingEngine.calculateCost(seg, RoutePreference.SAFEST, Map.of(segId.toString(), Double.POSITIVE_INFINITY));

        assertThat(Double.isFinite(costHigh)).isTrue();
        assertThat(Double.isFinite(costLow)).isTrue();
        assertThat(Double.isFinite(costNan)).isTrue();
        assertThat(Double.isFinite(costInf)).isTrue();

        assertThat(costHigh).isGreaterThan(costLow);
        assertThat(costLow).isGreaterThanOrEqualTo(0.0);
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
    @DisplayName("findRoute throws NoRouteFoundException when destination is unreachable")
    void testFindRoute_DisconnectedNetwork() {
        RoadSegment seg12 = createSegment(UUID.randomUUID(), 1L, 2L, 100.0, 70.0);
        Map<Long, List<RoadSegment>> graph = Map.of(
                1L, List.of(seg12),
                2L, Collections.emptyList(),
                99L, Collections.emptyList()
        );

        assertThatThrownBy(() -> routingEngine.findRoute(1L, 99L, RoutePreference.FASTEST, graph))
                .isInstanceOf(NoRouteFoundException.class)
                .hasMessageContaining("No route found between nodes 1 and 99");
    }

    @Test
    @DisplayName("findRoute respects oneway road restrictions in reverse direction")
    void testFindRoute_OnewayRestriction() {
        RoadSegment onewaySeg = createSegment(UUID.randomUUID(), 1L, 2L, 100.0, 70.0);
        onewaySeg.setIsOneway(true);

        // In reverse (starting at 2, trying to reach 1)
        Map<Long, List<RoadSegment>> graph = Map.of(
                2L, List.of(onewaySeg),
                1L, Collections.emptyList()
        );

        assertThatThrownBy(() -> routingEngine.findRoute(2L, 1L, RoutePreference.FASTEST, graph))
                .isInstanceOf(NoRouteFoundException.class);
    }
}
