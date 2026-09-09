package com.back.location.controller;

import com.back.global.api.ApiResponse;
import com.back.location.dto.ReverseGeocodeResponse;
import com.back.location.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Validated
@Tag(name = "location", description = "좌표와 주소 변환 API")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/reverse-geocode")
    @Operation(
            summary = "현재 위치 주소 조회",
            description = "위도와 경도를 네이버 Maps의 도로명·지번주소로 변환합니다."
    )
    public ApiResponse<ReverseGeocodeResponse> reverseGeocode(
            @RequestParam
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude
    ) {
        return ApiResponse.ok(
                "현재 위치 주소 조회 성공",
                locationService.reverseGeocode(latitude, longitude)
        );
    }
}
