package com.safepath.backend.controller;

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
        
        String email = authentication.getName();
        RouteResponseDto response = routeService.planRoute(email, requestDto);
        
        return ResponseEntity.ok(response);
    }
}
