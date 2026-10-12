package com.safepath.backend.service.routing;

import com.safepath.backend.config.RoutingProperties;
import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.exception.NoRouteFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Multi-objective Risk-Aware A* Routing Engine for pedestrian pathfinding.
 *
 * Cost Function:
 *   cost = alpha * normalized_time + beta * normalized_distance + gamma * normalized_risk
 * where alpha + beta + gamma = 1.0 (non-negative weights).
 *
 * Normalization Scales (Implementation Choices):
 *   normalized_time     = walk_time_seconds / 3600.0 (transit duration in hours)
 *   normalized_distance = length_meters / 10000.0 (transit distance in 10-km units)
 *   normalized_risk     = uncalibrated relative risk score in [0.0, 1.0]
 *
 * Heuristic Admissibility:
 *   SafePath AI employs a formally admissible zero heuristic (h(n) = 0.0), reducing
 *   A* to Dijkstra's algorithm. Because edge costs integrate heterogeneous, dimensionless
 *   time, distance, and uncalibrated risk metrics, Euclidean distance cannot guarantee
 *   underestimation of total multi-objective cost. The zero heuristic guarantees optimal,
 *   monotonic path discovery without heuristic overestimation.
 */
@Service
public class AStarRoutingEngine {

    @Getter
    @Setter
    private RoutingProperties routingProperties;

    @Autowired(required = false)
    public AStarRoutingEngine(RoutingProperties routingProperties) {
        this.routingProperties = (routingProperties != null) ? routingProperties : new RoutingProperties();
    }

    public AStarRoutingEngine() {
        this(new RoutingProperties());
    }

    @Data
    @Builder
    public static class Node implements Comparable<Node> {
        private Long id;
        private double gScore; // exact accumulated cost from start
        private double fScore; // estimated total cost to destination (gScore + heuristic)
        private Node parent;
        private RoadSegment incomingSegment;

        @Override
        public int compareTo(Node other) {
            int cmp = Double.compare(this.fScore, other.fScore);
            if (cmp != 0) {
                return cmp;
            }
            // Deterministic tie-breaking:
            // 1) Prefer node with lower gScore (deeper progress towards goal)
            int gCmp = Double.compare(this.gScore, other.gScore);
            if (gCmp != 0) {
                return gCmp;
            }
            // 2) Deterministic tie-break by unique node identifier
            if (this.id != null && other.id != null) {
                return Long.compare(this.id, other.id);
            }
            return 0;
        }
    }

    /**
     * Implements multi-objective A* routing based on user preference using default/fallback risk scores.
     */
    public List<RoadSegment> findRoute(
            Long startNodeId, 
            Long endNodeId, 
            RoutePreference preference, 
            Map<Long, List<RoadSegment>> graph) {
        return findRoute(startNodeId, endNodeId, preference, graph, Collections.emptyMap());
    }

    /**
     * Implements multi-objective A* routing using ML-predicted segment risk scores.
     *
     * @param startNodeId          origin network node
     * @param endNodeId            destination network node
     * @param preference           routing preference profile (FASTEST, BALANCED, SAFEST, etc.)
     * @param graph                local walkable road network graph
     * @param riskScoresBySegment  ML-predicted relative risk scores in [0.0, 1.0] keyed by segment ID
     * @return sequence of road segments constituting the optimal route
     * @throws NoRouteFoundException if destination is unreachable or graph is disconnected
     */
    public List<RoadSegment> findRoute(
            Long startNodeId, 
            Long endNodeId, 
            RoutePreference preference, 
            Map<Long, List<RoadSegment>> graph,
            Map<String, Double> riskScoresBySegment) {

        if (startNodeId == null || endNodeId == null) {
            throw new IllegalArgumentException("Start node and end node IDs cannot be null");
        }

        // Trivial path: start and destination are the exact same network node
        if (startNodeId.equals(endNodeId)) {
            return Collections.emptyList();
        }

        if (graph == null || graph.isEmpty()) {
            throw new NoRouteFoundException("Graph is empty or unpopulated; cannot route between nodes " + startNodeId + " and " + endNodeId);
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Long, Node> allNodes = new HashMap<>();
        Set<Long> closedSet = new HashSet<>();

        Node startNode = Node.builder()
                .id(startNodeId)
                .gScore(0.0)
                .fScore(heuristic(startNodeId, endNodeId))
                .build();

        openSet.add(startNode);
        allNodes.put(startNodeId, startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            // Destination reached with optimal cost
            if (current.getId().equals(endNodeId)) {
                return reconstructPath(current);
            }

            // Skip node if already closed/settled with optimal cost
            if (closedSet.contains(current.getId())) {
                continue;
            }
            closedSet.add(current.getId());

            List<RoadSegment> neighbors = graph.getOrDefault(current.getId(), Collections.emptyList());
            for (RoadSegment segment : neighbors) {
                if (segment == null) {
                    continue;
                }

                // Determine direction and target neighbor node
                Long neighborId;
                if (segment.getStartNodeId().equals(current.getId())) {
                    neighborId = segment.getEndNodeId();
                } else if (segment.getEndNodeId().equals(current.getId())) {
                    neighborId = segment.getStartNodeId();
                } else {
                    continue; // Segment is not connected to current node
                }

                // Skip if one-way and traversing in reverse direction
                if (Boolean.TRUE.equals(segment.getIsOneway()) && !segment.getStartNodeId().equals(current.getId())) {
                    continue;
                }

                // Skip if neighbor has already been settled
                if (closedSet.contains(neighborId)) {
                    continue;
                }

                double edgeCost = calculateCost(segment, preference, riskScoresBySegment);
                double tentativeGScore = current.getGScore() + edgeCost;

                Node neighbor = allNodes.get(neighborId);
                if (neighbor == null || tentativeGScore < neighbor.getGScore()) {
                    Node updatedNeighbor = Node.builder()
                            .id(neighborId)
                            .parent(current)
                            .incomingSegment(segment)
                            .gScore(tentativeGScore)
                            .fScore(tentativeGScore + heuristic(neighborId, endNodeId))
                            .build();

                    allNodes.put(neighborId, updatedNeighbor);
                    openSet.add(updatedNeighbor);
                }
            }
        }

        throw new NoRouteFoundException("No route found between nodes " + startNodeId + " and " + endNodeId);
    }

    private List<RoadSegment> reconstructPath(Node current) {
        List<RoadSegment> path = new ArrayList<>();
        while (current.getParent() != null) {
            path.add(current.getIncomingSegment());
            current = current.getParent();
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Calculates multi-objective edge cost:
     * cost = alpha * normalized_time + beta * normalized_distance + gamma * normalized_risk
     * where alpha + beta + gamma = 1.0.
     *
     * Ensures all edge costs are finite and strictly non-negative.
     */
    public double calculateCost(
            RoadSegment segment, 
            RoutePreference preference, 
            Map<String, Double> riskScoresBySegment) {

        if (segment == null) {
            return Double.POSITIVE_INFINITY;
        }

        // 1. Time normalization: walking duration in hours
        double walkTimeS;
        if (segment.getEstimatedWalkTimeS() != null && Double.isFinite(segment.getEstimatedWalkTimeS()) && segment.getEstimatedWalkTimeS() >= 0.0) {
            walkTimeS = segment.getEstimatedWalkTimeS();
        } else if (segment.getLengthMeters() != null && Double.isFinite(segment.getLengthMeters()) && segment.getLengthMeters() >= 0.0) {
            walkTimeS = segment.getLengthMeters() / 1.4; // 1.4 m/s pedestrian speed
        } else {
            walkTimeS = 0.0;
        }
        double normalizedTime = walkTimeS / 3600.0;

        // 2. Distance normalization: length in 10-km units
        double lengthMeters = (segment.getLengthMeters() != null && Double.isFinite(segment.getLengthMeters()) && segment.getLengthMeters() >= 0.0)
                ? segment.getLengthMeters()
                : 0.0;
        double normalizedDistance = lengthMeters / 10000.0; 
        
        // 3. Risk normalization in [0.0, 1.0]
        double normalizedRisk;
        String segmentKey = resolveSegmentKey(segment);
        if (riskScoresBySegment != null && riskScoresBySegment.containsKey(segmentKey)) {
            Double score = riskScoresBySegment.get(segmentKey);
            if (score != null && Double.isFinite(score)) {
                normalizedRisk = Math.max(0.0, Math.min(1.0, score));
            } else {
                normalizedRisk = 0.5; // Neutral fallback for null or non-finite values
            }
        } else if (preference == RoutePreference.FASTEST) {
            // In FASTEST mode, risk is not evaluated; use neutral midpoint without asserting safety
            normalizedRisk = 0.5;
        } else {
            // Missing data fallback: unscored segments are never assumed safe (0.0).
            // Penalize unlit segments (0.7) relative to lit segments (0.4).
            normalizedRisk = (segment.getIsLit() != null && segment.getIsLit()) ? 0.4 : 0.7;
        }

        // 4. Multi-objective weights
        RoutingProperties.RouteWeights weights = (routingProperties != null)
                ? routingProperties.getWeightsForPreference(preference)
                : new RoutingProperties.RouteWeights(0.3, 0.2, 0.5);

        double cost = (weights.getAlpha() * normalizedTime) 
                    + (weights.getBeta() * normalizedDistance) 
                    + (weights.getGamma() * normalizedRisk);

        // Guarantee finite and non-negative edge cost
        if (!Double.isFinite(cost) || cost < 0.0) {
            return 0.0;
        }

        return cost;
    }

    public String resolveSegmentKey(RoadSegment segment) {
        if (segment.getId() != null) {
            return segment.getId().toString();
        }
        return "node-" + segment.getStartNodeId() + "-" + segment.getEndNodeId();
    }

    /**
     * Formally admissible zero heuristic (h(n) = 0.0).
     * Guarantees monotonicity, admissibility, and exact shortest-path discovery
     * under multi-objective non-negative edge costs.
     */
    public double heuristic(Long nodeId, Long targetId) {
        return 0.0;
    }
}
