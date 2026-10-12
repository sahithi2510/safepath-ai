package com.safepath.backend.service.routing;

import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.entity.RoutePreference;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AStarRoutingEngine {

    @Data
    @Builder
    public static class Node implements Comparable<Node> {
        private Long id;
        private double gScore; // cost from start
        private double fScore; // estimated total cost to end (gScore + heuristic)
        private Node parent;
        private RoadSegment incomingSegment;

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }

    /**
     * Implements multi-objective A* routing based on user preference using default/fallback risk scores.
     * Cost = alpha * normalized_time + beta * normalized_distance + gamma * normalized_risk
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
     */
    public List<RoadSegment> findRoute(
            Long startNodeId, 
            Long endNodeId, 
            RoutePreference preference, 
            Map<Long, List<RoadSegment>> graph,
            Map<String, Double> riskScoresBySegment) {
        
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Long, Node> allNodes = new HashMap<>();

        Node startNode = Node.builder()
                .id(startNodeId)
                .gScore(0.0)
                .fScore(heuristic(startNodeId, endNodeId))
                .build();

        openSet.add(startNode);
        allNodes.put(startNodeId, startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.getId().equals(endNodeId)) {
                return reconstructPath(current);
            }

            List<RoadSegment> neighbors = graph.getOrDefault(current.getId(), Collections.emptyList());
            for (RoadSegment segment : neighbors) {
                // Determine direction and target node
                Long neighborId = segment.getStartNodeId().equals(current.getId()) ? segment.getEndNodeId() : segment.getStartNodeId();

                // Skip if one-way and going the wrong way
                if (segment.getIsOneway() && !segment.getStartNodeId().equals(current.getId())) {
                    continue;
                }

                double cost = calculateCost(segment, preference, riskScoresBySegment);
                double tentativeGScore = current.getGScore() + cost;

                Node neighbor = allNodes.getOrDefault(neighborId, Node.builder().id(neighborId).gScore(Double.POSITIVE_INFINITY).build());

                if (tentativeGScore < neighbor.getGScore()) {
                    neighbor.setParent(current);
                    neighbor.setIncomingSegment(segment);
                    neighbor.setGScore(tentativeGScore);
                    neighbor.setFScore(tentativeGScore + heuristic(neighborId, endNodeId));

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                        allNodes.put(neighborId, neighbor);
                    } else {
                        // Re-insert to update priority
                        openSet.remove(neighbor);
                        openSet.add(neighbor);
                    }
                }
            }
        }

        throw new RuntimeException("No route found between nodes " + startNodeId + " and " + endNodeId);
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
     */
    public double calculateCost(
            RoadSegment segment, 
            RoutePreference preference, 
            Map<String, Double> riskScoresBySegment) {

        double normalizedTime = segment.getEstimatedWalkTimeS() != null 
                ? segment.getEstimatedWalkTimeS() / 3600.0 
                : segment.getLengthMeters() / 1.4 / 3600.0;
        double normalizedDistance = segment.getLengthMeters() / 10000.0; 
        
        // Extract normalized risk [0.0, 1.0]
        double normalizedRisk;
        String segmentKey = resolveSegmentKey(segment);
        if (riskScoresBySegment != null && riskScoresBySegment.containsKey(segmentKey)) {
            Double score = riskScoresBySegment.get(segmentKey);
            normalizedRisk = (score != null) ? Math.max(0.0, Math.min(1.0, score)) : 0.5;
        } else if (preference == RoutePreference.FASTEST) {
            // In FASTEST mode, risk is not evaluated; use neutral midpoint without asserting safety
            normalizedRisk = 0.5;
        } else {
            // Missing data fallback: unscored segments are never assumed safe (0.0)
            normalizedRisk = (segment.getIsLit() != null && segment.getIsLit()) ? 0.4 : 0.7;
        }

        double alpha, beta, gamma;

        RoutePreference effectivePref = preference != null ? preference : RoutePreference.BALANCED;
        switch (effectivePref) {
            case FASTEST:
                // Prioritize time and distance; risk weight is 0.0 (independent of ML)
                alpha = 0.8; beta = 0.2; gamma = 0.0;
                break;
            case SAFEST:
                // Prioritize safety and risk minimization over time
                alpha = 0.1; beta = 0.1; gamma = 0.8;
                break;
            case SOLO_TRAVELLER:
                alpha = 0.15; beta = 0.15; gamma = 0.7;
                break;
            case TOURIST:
                alpha = 0.3; beta = 0.3; gamma = 0.4;
                break;
            case ELDERLY:
                alpha = 0.1; beta = 0.4; gamma = 0.5;
                break;
            case STANDARD:
            case BALANCED:
            default:
                alpha = 0.3; beta = 0.2; gamma = 0.5;
                break;
        }

        return (alpha * normalizedTime) + (beta * normalizedDistance) + (gamma * normalizedRisk);
    }

    private String resolveSegmentKey(RoadSegment segment) {
        if (segment.getId() != null) {
            return segment.getId().toString();
        }
        return "node-" + segment.getStartNodeId() + "-" + segment.getEndNodeId();
    }

    private double heuristic(Long nodeId, Long targetId) {
        return 0.0;
    }
}
