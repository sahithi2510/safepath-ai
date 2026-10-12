package com.safepath.backend.service;

import com.safepath.backend.dto.RouteRequestDto;
import com.safepath.backend.dto.RouteResponseDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.entity.RouteRequest;
import com.safepath.backend.entity.RouteResult;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.MlServiceException;
import com.safepath.backend.exception.MlServiceUnavailableException;
import com.safepath.backend.repository.RoadSegmentRepository;
import com.safepath.backend.repository.RouteRequestRepository;
import com.safepath.backend.repository.RouteResultRepository;
import com.safepath.backend.repository.UserRepository;
import com.safepath.backend.service.ml.MlServiceClient;
import com.safepath.backend.service.ml.RoadSegmentFeatureService;
import com.safepath.backend.service.routing.AStarRoutingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private final RouteRequestRepository routeRequestRepository;
    private final RouteResultRepository routeResultRepository;
    private final UserRepository userRepository;
    private final RoadSegmentRepository roadSegmentRepository;
    private final GeometryFactory geometryFactory;
    private final AStarRoutingEngine aStarRoutingEngine;
    private final MlServiceClient mlServiceClient;
    private final RoadSegmentFeatureService roadSegmentFeatureService;

    @Transactional
    public RouteResponseDto planRoute(String userEmail, RouteRequestDto requestDto) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Point startPoint = geometryFactory.createPoint(new Coordinate(requestDto.getStartLon(), requestDto.getStartLat()));
        startPoint.setSRID(4326);
        
        Point endPoint = geometryFactory.createPoint(new Coordinate(requestDto.getEndLon(), requestDto.getEndLat()));
        endPoint.setSRID(4326);

        RouteRequest request = new RouteRequest();
        request.setUser(user);
        request.setStartPoint(startPoint);
        request.setEndPoint(endPoint);
        request.setPreference(requestDto.getPreference());
        
        request = routeRequestRepository.save(request);

        // Fetch local road network (within e.g. 5000m of start) to build graph
        List<RoadSegment> localSegments = roadSegmentRepository.findWalkableSegmentsWithinRadius(startPoint, 5000.0);
        
        // Build graph
        Map<Long, List<RoadSegment>> graph = new HashMap<>();
        for (RoadSegment segment : localSegments) {
            graph.computeIfAbsent(segment.getStartNodeId(), k -> new ArrayList<>()).add(segment);
            if (!segment.getIsOneway()) {
                graph.computeIfAbsent(segment.getEndNodeId(), k -> new ArrayList<>()).add(segment);
            }
        }

        RouteResult result = new RouteResult();
        result.setRouteRequest(request);

        boolean isMlScored = false;
        Boolean isSyntheticDemonstration = null;
        String modelVersion = null;
        String riskLevel = "UNSCORED";
        String notes = null;
        Double calculatedSafetyScore = null;

        try {
            // Find closest nodes to start and end points
            RoadSegment closestToStart = roadSegmentRepository.findNearestSegment(startPoint);
            RoadSegment closestToEnd = roadSegmentRepository.findNearestSegment(endPoint);

            if (closestToStart == null || closestToEnd == null || localSegments.isEmpty()) {
                throw new RuntimeException("No road network found in this area.");
            }

            // Retrieve ML risk predictions for local road segments
            Map<String, Double> riskScores = new HashMap<>();
            Map<String, PredictRiskResponseDto> predictionsMap = Collections.emptyMap();

            try {
                List<PredictRiskRequestDto> batchRequests = roadSegmentFeatureService.prepareBatchRequests(localSegments, LocalDateTime.now());
                predictionsMap = mlServiceClient.predictRiskBatch(batchRequests);

                for (Map.Entry<String, PredictRiskResponseDto> entry : predictionsMap.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().getRiskScore() != null) {
                        riskScores.put(entry.getKey(), entry.getValue().getRiskScore());
                        isSyntheticDemonstration = entry.getValue().getIsSyntheticDemonstration();
                        modelVersion = entry.getValue().getModelVersion();
                    }
                }
                isMlScored = !riskScores.isEmpty();
            } catch (MlServiceException e) {
                if (requestDto.getPreference() == RoutePreference.SAFEST) {
                    // Strict integrity constraint: do not claim to offer a SAFEST route if risk predictions are unavailable
                    log.error("SAFEST route requested but ML service failed: {}", e.getMessage());
                    if (e instanceof MlServiceUnavailableException) {
                        throw (MlServiceUnavailableException) e;
                    }
                    throw new MlServiceUnavailableException("ML service failed during SAFEST route calculation: " + e.getMessage(), e);
                }
                log.warn("ML risk predictions unavailable for preference {}: {}. Falling back to non-ML edge weights.",
                        requestDto.getPreference(), e.getMessage());
            }

            // Run A*
            List<RoadSegment> routeSegments = aStarRoutingEngine.findRoute(
                    closestToStart.getStartNodeId(), 
                    closestToEnd.getEndNodeId(), 
                    requestDto.getPreference(), 
                    graph,
                    riskScores);

            // Construct LineString from segments
            List<Coordinate> pathCoords = new ArrayList<>();
            double totalDistance = 0;
            double totalDuration = 0;
            double weightedSafetySum = 0.0;
            double totalScoredLength = 0.0;

            for (RoadSegment segment : routeSegments) {
                Coordinate[] coords = segment.getGeometry().getCoordinates();
                for (Coordinate coord : coords) {
                    if (pathCoords.isEmpty() || !pathCoords.get(pathCoords.size() - 1).equals2D(coord)) {
                        pathCoords.add(coord);
                    }
                }
                double segLength = segment.getLengthMeters();
                totalDistance += segLength;
                totalDuration += segment.getEstimatedWalkTimeS() != null ? segment.getEstimatedWalkTimeS() : (segLength / 1.4);

                String segKey = roadSegmentFeatureService.resolveSegmentId(segment);
                PredictRiskResponseDto pred = predictionsMap.get(segKey);
                if (pred != null && pred.getSafetyScore() != null) {
                    weightedSafetySum += pred.getSafetyScore() * segLength;
                    totalScoredLength += segLength;
                }
            }

            LineString routeLine = geometryFactory.createLineString(pathCoords.toArray(new Coordinate[0]));
            routeLine.setSRID(4326);

            result.setRouteLine(routeLine);
            result.setTotalDistanceMeters(totalDistance);
            result.setEstimatedDurationSeconds(totalDuration);

            if (isMlScored && totalScoredLength > 0) {
                // Length-weighted aggregate safety score in [0.0, 100.0]
                double avgSafety = weightedSafetySum / totalScoredLength;
                calculatedSafetyScore = Math.round(avgSafety * 1000.0) / 10.0;
                result.setSafetyScore(calculatedSafetyScore);
                riskLevel = deriveRiskCategory(avgSafety);
                notes = "Scored using ML model " + (modelVersion != null ? modelVersion : "v1") +
                        (Boolean.TRUE.equals(isSyntheticDemonstration) ? " (synthetic demonstration model)" : "");
            } else {
                result.setSafetyScore(null);
                riskLevel = "UNSCORED";
                notes = requestDto.getPreference() == RoutePreference.FASTEST
                        ? "FASTEST route prioritized travel time independently of risk scoring."
                        : "ML risk predictions unavailable; route generated without safety scoring.";
            }

        } catch (MlServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Fallback for empty database (Milestone testing)
            Coordinate[] coordinates = new Coordinate[]{
                    new Coordinate(requestDto.getStartLon(), requestDto.getStartLat()),
                    new Coordinate(requestDto.getEndLon(), requestDto.getEndLat())
            };
            LineString routeLine = geometryFactory.createLineString(coordinates);
            routeLine.setSRID(4326);

            result.setRouteLine(routeLine);
            result.setTotalDistanceMeters(1000.0);
            result.setEstimatedDurationSeconds(600.0);
            result.setSafetyScore(null);
            riskLevel = "UNSCORED";
            notes = "Fallback straight-line path; road network unavailable.";
        }
        
        result = routeResultRepository.save(result);

        return mapToDto(result, isMlScored, isSyntheticDemonstration, riskLevel, modelVersion, notes);
    }

    private String deriveRiskCategory(double safetyScoreFraction) {
        double riskFraction = 1.0 - safetyScoreFraction;
        if (riskFraction < 0.2) return "VERY_LOW";
        if (riskFraction < 0.4) return "LOW";
        if (riskFraction < 0.6) return "MODERATE";
        if (riskFraction < 0.8) return "HIGH";
        return "VERY_HIGH";
    }

    private RouteResponseDto mapToDto(
            RouteResult result, 
            boolean isMlScored, 
            Boolean isSyntheticDemonstration, 
            String riskLevel, 
            String modelVersion, 
            String notes) {

        Coordinate[] coords = result.getRouteLine().getCoordinates();
        List<RouteResponseDto.CoordinateDto> coordDtos = Arrays.stream(coords)
                .map(c -> RouteResponseDto.CoordinateDto.builder()
                        .longitude(c.getX())
                        .latitude(c.getY())
                        .build())
                .collect(Collectors.toList());

        return RouteResponseDto.builder()
                .routeId(result.getId().toString())
                .coordinates(coordDtos)
                .distanceMeters(result.getTotalDistanceMeters())
                .durationSeconds(result.getEstimatedDurationSeconds())
                .safetyScore(result.getSafetyScore())
                .isMlScored(isMlScored)
                .isSyntheticDemonstration(isSyntheticDemonstration)
                .riskLevel(riskLevel)
                .modelVersion(modelVersion)
                .notes(notes)
                .build();
    }
}
