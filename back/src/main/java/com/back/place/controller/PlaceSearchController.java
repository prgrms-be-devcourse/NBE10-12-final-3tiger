package com.back.place.controller;

import com.back.global.api.ApiResponse;
import com.back.place.dto.PlaceSearchResult;
import com.back.place.service.PlaceSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Tag(name = "place", description = "장소 검색 결과 제공 API")
public class PlaceSearchController {

    private final PlaceSearchService service;

    @GetMapping("/search")
    @Operation(
            summary = "검색 결과 제공",
            description = "검색한 결과 리스트를 제공합니다."
    )
    public ApiResponse<PlaceSearchResult> search(
            @RequestParam
            @NotBlank
            @Size(max = 100) String query
    ) {
        return ApiResponse.ok(
                "장소 검색 성공",
                service.search(query)
        );
    }
}
