package com.safepath.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 15-feature input vector for ML inference.
 * Field names strictly match the FastAPI RiskFeatures schema in snake_case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFeaturesDto {

    // Spatial crime density features
    @JsonProperty("crime_density_500m")
    private Double crimeDensity500m;

    @JsonProperty("crime_density_1km")
    private Double crimeDensity1km;

    @JsonProperty("severity_weighted_density")
    private Double severityWeightedDensity;

    @JsonProperty("recent_crime_density")
    private Double recentCrimeDensity;

    @JsonProperty("night_crime_density")
    private Double nightCrimeDensity;

    // Temporal features
    @JsonProperty("hour_of_day")
    private Integer hourOfDay;

    @JsonProperty("day_of_week")
    private Integer dayOfWeek;

    // Lighting proxy
    @JsonProperty("lighting_score")
    private Double lightingScore;

    // Activity proxy
    @JsonProperty("activity_proxy")
    private Double activityProxy;

    // Emergency services proximity (meters)
    @JsonProperty("nearest_police_m")
    private Double nearestPoliceM;

    @JsonProperty("nearest_hospital_m")
    private Double nearestHospitalM;

    @JsonProperty("nearest_transport_m")
    private Double nearestTransportM;

    // Weather
    @JsonProperty("weather_precipitation")
    private Double weatherPrecipitation;

    @JsonProperty("weather_visibility")
    private Double weatherVisibility;

    // Verified community reports
    @JsonProperty("verified_report_count")
    private Integer verifiedReportCount;
}
