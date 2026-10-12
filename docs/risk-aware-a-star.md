# SafePath AI — Risk-Aware A* Routing Specification

## 1. Overview & Algorithm Model

SafePath AI implements a **multi-objective A* search algorithm** for pedestrian navigation. The primary goal is to provide users with transparent routing choices that balance transit duration, walking distance, and predicted street segment risk.

### Graph Representation
The road network is modeled as a directed, walkable graph $G = (V, E)$:
- **Vertices ($V$):** Road intersections and network nodes identified by unique numeric identifiers (`Long id`).
- **Edges ($E$):** Walkable road segments (`RoadSegment`) with length in meters, walk duration in seconds, lighting attribute, and one-way constraints.
- **Directionality:** Two-way streets generate bidirectional graph edges ($u \to v$ and $v \to u$), while one-way streets enforce directed traversal from `start_node_id` to `end_node_id`.

---

## 2. Multi-Objective Cost Function

For any edge $e \in E$, the multi-criteria traversal cost is defined as:

$$\text{cost}(e) = \alpha \cdot \text{normalized\_time}(e) + \beta \cdot \text{normalized\_distance}(e) + \gamma \cdot \text{normalized\_risk}(e)$$

### Mathematical Constraints & Design Choices
- **Non-negativity & Sum-to-One:**
  $$\alpha, \beta, \gamma \ge 0 \quad \text{and} \quad \alpha + \beta + \gamma = 1.0$$
- **Every edge cost is finite and strictly non-negative:**
  $$\text{cost}(e) \ge 0 \quad \forall e \in E$$
- **Implementation Choice:** The weights $\alpha, \beta, \gamma$ and normalization factors are **application design choices** configured in `application.yml` and managed by `RoutingProperties`. They are not presented as universal scientific constants.

### Component Normalization Scales
To prevent any single attribute with large physical magnitudes (such as meters) from dominating dimensionless values (such as relative risk), consistent scaling is applied identically across all edges in the graph:

| Component | Scaling Formula | Interpretation |
|---|---|---|
| `normalized_time` | $\frac{\text{walk\_time\_seconds}}{3600.0}$ | Walking transit time expressed in hours. If unpopulated, estimated using standard average pedestrian walking speed ($1.4\text{ m/s}$). |
| `normalized_distance` | $\frac{\text{length\_meters}}{10000.0}$ | Edge physical distance scaled to 10-kilometer units. |
| `normalized_risk` | $\text{score} \in [0.0, 1.0]$ | Uncalibrated relative risk score provided by ML inference (or documented fallback). |

---

## 3. Mathematical Nature of the Risk Component

> **Scientific Honesty Notice:** The risk score $r(e) \in [0.0, 1.0]$ represents an **uncalibrated relative risk score** derived from machine learning inference on feature vectors (or lighting/activity defaults when unobserved).

- **Non-probabilistic Interpretation:** The risk value does **NOT** represent an absolute physical probability of crime occurrence, nor does summing edge costs along a path represent the probability of an incident along that path. Probabilities do not add linearly across series of road segments.
- **Utility Penalty Interpretation:** Rather than a compound probability, the cumulative path cost:
  $$C(P) = \sum_{e \in P} \text{cost}(e)$$
  represents a **multi-criteria utility penalty function**. Each street traversed incurs an incremental penalty proportional to the transit time, physical detour distance, and adverse environmental conditions (poor lighting, elevated historical incident density).

---

## 4. Heuristic Admissibility & Optimality

In standard A*, the evaluation function is $f(n) = g(n) + h(n)$, where $g(n)$ is the exact cost from origin to $n$, and $h(n)$ is the heuristic estimate of the cost from $n$ to the destination.

### Admissible Zero Heuristic ($h(n) = 0.0$)
SafePath AI employs a **formally admissible zero heuristic**:
$$h(n) = 0.0 \quad \forall n \in V$$

### Proof of Admissibility & Monotonicity
1. **Admissibility:** A heuristic is admissible if $h(n) \le h^*(n)$ for all $n$, where $h^*(n)$ is the true minimal remaining cost to destination. Since every edge cost is non-negative ($\text{cost}(e) \ge 0$), the true remaining cost $h^*(n) \ge 0$. Therefore:
   $$h(n) = 0.0 \le h^*(n) \quad \forall n \in V$$
   Admissibility is rigorously satisfied.
2. **Monotonicity (Consistency):** For any step $n \to n'$ with edge cost $c(n, n')$:
   $$h(n) \le c(n, n') + h(n') \iff 0.0 \le c(n, n') + 0.0$$
   which holds trivially because $c(n, n') \ge 0$.
3. **Rationale over Euclidean Heuristics:** A Euclidean spatial heuristic ($\text{distance}(n, \text{goal}) / \text{speed}$) estimates physical space in meters or seconds, but has zero relationship with the cumulative risk component ($\gamma \cdot r(e)$). Without empirical lower bounds on risk along unknown future edges, spatial heuristics cannot guarantee underestimation across arbitrary multi-objective weight combinations. The zero heuristic guarantees that A* behaves as a Dijkstra-equivalent optimal path search without heuristic overestimation or false optimality claims.

---

## 5. Route Preference Profiles & Configurable Weights

Weights are defined in `RoutingProperties` and configurable via `application.yml`:

| Preference Profile | $\alpha$ (Time) | $\beta$ (Dist) | $\gamma$ (Risk) | Behavioral Characteristics |
|---|---|---|---|---|
| **`FASTEST`** | **0.80** | **0.20** | **0.00** | **Independent of ML predictions.** Prioritizes transit speed without requiring or waiting for ML risk scoring. |
| **`BALANCED`** | **0.30** | **0.20** | **0.50** | Measurable compromise between transit time and safety. |
| **`SAFEST`** | **0.10** | **0.10** | **0.80** | Strongly penalizes higher relative risk; selects longer detours through well-lit, lower-risk streets. |
| **`SOLO_TRAVELLER`** | 0.15 | 0.15 | 0.70 | Enhanced safety weight with high priority on lit and active streets. |
| **`TOURIST`** | 0.30 | 0.30 | 0.40 | Balanced travel along major walkable corridors. |
| **`ELDERLY`** | 0.10 | 0.40 | 0.50 | Penalizes excessive physical walking distance while maintaining safety. |

### Validation & Normalization Rules
`RoutingProperties.RouteWeights.validate()` enforces:
1. Rejection of negative weights ($\alpha < 0, \beta < 0, \gamma < 0$).
2. Rejection of non-finite weights (`NaN` or `Infinite`).
3. Rejection of non-positive sums ($\alpha + \beta + \gamma \le 0$).
4. Automatic normalization: if the sum deviates from 1.0, weights are normalized: $\alpha' = \alpha / \sum, \beta' = \beta / \sum, \gamma' = \gamma / \sum$.

---

## 6. Edge Cases, Determinism, & Error Handling

### 6.1 Closed Set & Time Complexity
- A* uses a `Set<Long> closedSet` to record settled vertices.
- Once a vertex is popped from the `PriorityQueue`, it is settled with optimal $g(n)$ cost. Any subsequent entries for that node popped from the queue are discarded in $O(1)$ time.
- **Worst-case Time Complexity:** $O((|V| + |E|) \log |V|)$ using binary heap priority queues.
- **Space Complexity:** $O(|V| + |E|)$ for node lookup maps and graph adjacency list.

### 6.2 Deterministic Tie-Breaking
When competing route alternatives have identical estimated total costs ($f_1 = f_2$), Java `Node` ordering breaks ties deterministically:
1. First, by lower $g(n)$ cost (prioritizes deeper concrete exploration from start).
2. Second, by unique node identifier: `Long.compare(id1, id2)`.
This guarantees that path selection is 100% reproducible across test runs and executions.

### 6.3 Cyclic Graphs & Self-Loops
Graph cycles (e.g. $1 \to 2 \to 3 \to 1$) are handled without infinite loops because all edge costs are non-negative ($c \ge 0$) and settled nodes are recorded in `closedSet`.

### 6.4 Missing Data & Fallback Policy
1. **Unscored Segments:** Never evaluated as zero risk (0.0). Missing segments receive a baseline penalty of $0.40$ if tagged lit or $0.70$ if unlit.
2. **Invalid / Non-Finite Scores:** Values of `NaN`, `Infinite`, or negative values in `riskScoresBySegment` are safely clamped to $[0.0, 1.0]$ or defaulted to neutral $0.50$.
3. **ML Service Outage during `SAFEST`:**
   `RouteService` throws `MlServiceUnavailableException`. The system strictly refuses to calculate or return a route claiming to be the safest when risk predictions cannot be obtained.
4. **ML Service Outage during `FASTEST`:**
   Route calculation proceeds independently since $\gamma = 0.0$. The route response is explicitly marked `isMlScored = false`, `safetyScore = null`, and `riskLevel = "UNSCORED"`.

### 6.5 Disconnected Graphs & Unreachable Destinations
If no path exists between the start and destination node, `AStarRoutingEngine` throws `NoRouteFoundException` (HTTP 404).

---

## 7. Limitations & Honest Engineering Disclaimers

1. **No Absolute Safety Guarantee:** SafePath AI does not claim that a route computed with `RoutePreference.SAFEST` is guaranteed safe or empirically safest in the physical world. Real-world incident occurrence is influenced by unpredictable dynamic human factors.
2. **Synthetic Training Baseline:** Current ML risk predictions derive from synthetic evaluation datasets (`is_synthetic_demonstration = true`).
3. **Static Street Network:** Road segment walk times assume an unhindered walking pace of $1.4\text{ m/s}$ and do not account for traffic signals or temporary sidewalk closures.
