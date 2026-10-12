package com.safepath.backend.dto;

import com.safepath.backend.entity.RoutePreference;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequestDto {
    @NotNull
    private Double startLat;
    @NotNull
    private Double startLon;
    
    @NotNull
    private Double endLat;
    @NotNull
    private Double endLon;
    
    private RoutePreference preference = RoutePreference.STANDARD;
}
