package com.safepath.backend.controller;

import com.safepath.backend.dto.RouteDto;
import com.safepath.backend.dto.RouteRequestDto;
import com.safepath.backend.dto.RouteResponseDto;
import com.safepath.backend.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<RouteResponseDto> planRoute(
            @Valid @RequestBody RouteRequestDto requestDto,
            Authentication authentication) {
        
        String email = authentication != null ? authentication.getName() : null;
        RouteResponseDto response = routeService.planRoute(email, requestDto);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search")
    public ResponseEntity<RouteDto.SearchResponse> searchRoutes(
            @Valid @RequestBody RouteDto.SearchRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        RouteDto.SearchResponse response = routeService.searchRoutes(email, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/compare")
    public ResponseEntity<RouteDto.SearchResponse> compareRoutes(
            @Valid @RequestBody RouteDto.SearchRequest request,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        RouteDto.SearchResponse response = routeService.searchRoutes(email, request);
        return ResponseEntity.ok(response);
    }
}

