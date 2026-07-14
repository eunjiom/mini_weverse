package com.miniweverse.artist.controller;

import com.miniweverse.artist.dto.ArtistProfileDetailResponse;
import com.miniweverse.artist.dto.ArtistSearchResponse;
import com.miniweverse.artist.service.ArtistService;
import com.miniweverse.common.response.CursorPageResponse;
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
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @GetMapping("/artists")
    public ResponseEntity<CursorPageResponse<ArtistSearchResponse>> search(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(artistService.search(name, cursor, size));
    }

    @GetMapping("/artists/{artistId}")
    public ResponseEntity<ArtistProfileDetailResponse> getProfile(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId
    ) {
        return ResponseEntity.ok(artistService.getProfile(viewerId, artistId));
    }
}
