package com.miniweverse.artist.controller;

import com.miniweverse.artist.dto.ArtistProfileDetailResponse;
import com.miniweverse.artist.dto.ArtistSearchResponse;
import com.miniweverse.artist.service.ArtistService;
import com.miniweverse.common.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "아티스트", description = "아티스트 검색 및 프로필 조회")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @Operation(summary = "아티스트 이름 검색", description = "아티스트 이름으로 검색하여 커서 기반 페이지네이션으로 결과를 조회합니다. 로그인 없이도 조회 가능합니다.")
    @GetMapping("/artists")
    public ResponseEntity<CursorPageResponse<ArtistSearchResponse>> search(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(artistService.search(name, cursor, size));
    }

    @Operation(summary = "아티스트 프로필 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/artists/{artistId}")
    public ResponseEntity<ArtistProfileDetailResponse> getProfile(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId
    ) {
        return ResponseEntity.ok(artistService.getProfile(viewerId, artistId));
    }
}
