# SafePath AI — Routing Algorithm

> **Last Updated:** 2026-10-08  
> **Status:** Design document (implementation in Milestone 10)

---

## 1. Overview

SafePath AI uses the **A\* algorithm** as its primary route optimization algorithm.
The key innovation is a **safety-aware cost function** that balances travel time, distance, and predicted road-segment risk.

---

## 2. Road Graph Model

The road network is represented as a **directed weighted graph**:

- **Nodes:** Road intersections and relevant road points (OSM nodes)
- **Edges:** Road segments (OSM ways, split at intersections)

### Node
```
Node {
    long    osmNodeId
    double  latitude
    double  longitude
}
```

### Edge
```
Edge {
    UUID    segmentId
    long    fromNodeId
    long    toNodeId
    double  lengthMeters
    double  estimatedWalkTimeSeconds
    double  predictedRisk      // 0.0 – 1.0, from ML service
    // Additional features available for context:
    double  lightingScore
    double  activityProxy
    boolean isWalkable
}
```

---

## 3. Cost Function

The edge traversal cost for A\* is:

```
cost(edge) = α * normalized_time(edge)
           + β * normalized_distance(edge)
           + γ * normalized_risk(edge)

where:
  α + β + γ = 1
  α, β, γ ≥ 0
```

**Normalization:** Each component is normalized to [0, 1] relative to the graph's min/max values to prevent scale dominance.

---

## 4. Route Mode Weights

> **IMPLEMENTATION CHOICE:** The default weights below are chosen heuristically to produce meaningfully different routes for each mode. They have not been empirically optimized. Future work could tune these via user studies or preference learning.

| Mode | α (time) | β (distance) | γ (risk) | Effect |
|------|----------|-------------|---------|--------|
| `FASTEST` | 0.7 | 0.2 | 0.1 | Strongly prioritizes speed; risk is a minor factor |
| `BALANCED` | 0.3 | 0.2 | 0.5 | Safety is the primary concern; time still matters |
| `SAFEST` | 0.1 | 0.1 | 0.8 | Safety dominates; time/distance are minor |

These weights are configurable via application properties.

---

## 5. A\* Algorithm

### Heuristic

The A\* heuristic estimates the minimum remaining cost from a node to the destination.

For the `FASTEST` and `BALANCED` modes, the heuristic uses straight-line (Euclidean) distance converted to estimated time.

For the `SAFEST` mode, the heuristic uses straight-line distance (conservative lower bound).

> **Note:** The heuristic must be **admissible** (never overestimates actual cost) to guarantee optimal A\* results. Using Euclidean distance as a lower bound for actual road distance satisfies this.

### Pseudocode

```
function aStar(graph, start, goal, weights):
    openSet = PriorityQueue()
    openSet.add(start, priority=0)
    
    cameFrom = {}
    gScore = {start: 0}
    fScore = {start: heuristic(start, goal, weights)}
    
    while openSet is not empty:
        current = openSet.pop_lowest_f()
        
        if current == goal:
            return reconstructPath(cameFrom, current)
        
        for each neighbor of current:
            edge = graph.edge(current, neighbor)
            tentative_g = gScore[current] + edgeCost(edge, weights)
            
            if tentative_g < gScore.get(neighbor, ∞):
                cameFrom[neighbor] = current
                gScore[neighbor] = tentative_g
                fScore[neighbor] = tentative_g + heuristic(neighbor, goal, weights)
                openSet.add(neighbor, priority=fScore[neighbor])
    
    return null  // No path found
```

### Edge Cost Calculation

```java
double edgeCost(Edge edge, RouteWeights weights) {
    double normTime = normalize(edge.estimatedWalkTimeSeconds, graph.minTime, graph.maxTime);
    double normDist = normalize(edge.lengthMeters, graph.minDist, graph.maxDist);
    double normRisk = edge.predictedRisk; // ML output already in [0,1]
    
    return weights.alpha * normTime
         + weights.beta  * normDist
         + weights.gamma * normRisk;
}
```

---

## 6. Dijkstra Baseline

Dijkstra's algorithm is implemented as:
- A fallback when the A\* heuristic cannot be computed
- A testing baseline to verify A\* correctness
- An alternative for very small graphs

Dijkstra = A\* with heuristic(n) = 0 for all nodes.

---

## 7. Route Post-Processing

After A\* finds a path:

1. **Geometry assembly:** Combine segment geometries into a LineString
2. **Distance calculation:** Sum of segment lengths
3. **Duration calculation:** Sum of estimated walk times
4. **Safety score aggregation:** Weighted average of segment safety scores
5. **Factor collection:** Identify notable positive/negative factors
6. **SHAP explanation:** Request explanation from ML service for the highest-risk segments

---

## 8. Nearest Node Mapping

Given an origin/destination latitude/longitude:
1. Query PostGIS for the nearest road network node using `ST_Distance`
2. Use that node as start/end of the A\* search

```sql
SELECT id, osm_node_id, ST_X(geometry) as lon, ST_Y(geometry) as lat
FROM road_nodes
ORDER BY geometry <-> ST_MakePoint(:lon, :lat)::geometry
LIMIT 1;
```

---

## 9. Graph Loading Strategy

**IMPLEMENTATION CHOICE:** Graph is loaded into memory at startup for the served city/area.

Rationale:
- Avoids per-request database queries for every edge traversal
- Acceptable for a single-city deployment
- Requires periodic refresh as road data changes

For large areas, a tile-based partial graph loading strategy could be implemented (documented as future work).

---

## 10. Risk Score Assignment

When the ML service is available:
1. Identify all edges in the region of interest
2. Load their feature snapshots from the database
3. Send as a batch to `POST /predict-risk/batch`
4. Receive predicted risk per segment
5. Assign to graph edges before running A\*

When the ML service is unavailable:
1. Use a default risk score (e.g., 0.5 = neutral/moderate)
2. Log the unavailability
3. Return routes with `mlServiceAvailable: false` in response

---

## 11. Tests Required

The following must be verified by automated tests:

| Test | Requirement |
|------|-------------|
| Graph construction | All edges have valid costs |
| A\* finds path | When path exists |
| A\* returns null | When no path exists |
| FASTEST vs SAFEST | FASTEST must have shorter duration; SAFEST must have lower aggregate risk |
| BALANCED | Must produce a measurable compromise between the two |
| Weight sensitivity | Increasing γ must increase safety score of chosen route |
| Dijkstra baseline | Produces same result as A\* on small test graphs |
| Heuristic admissibility | h(n) ≤ actual cost for all tested nodes |

---

## 12. Limitations

| Limitation | Type |
|-----------|------|
| Graph loaded at startup | IMPLEMENTATION CHOICE — not a limitation for single-city use |
| No real-time traffic | LIMITATION — walking speed assumed constant |
| Walk time estimated from distance | IMPLEMENTATION CHOICE — no real pedestrian speed data |
| Risk scores depend on ML model quality | LIMITATION — documented in ml-pipeline.md |
| Nearest-node snapping may differ from actual entry point | LIMITATION |

---

*SafePath AI — routing.md — 2026-10-08*
