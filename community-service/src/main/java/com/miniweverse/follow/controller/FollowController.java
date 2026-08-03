package com.miniweverse.follow.controller;

import com.miniweverse.follow.dto.FollowRequest;
import com.miniweverse.follow.dto.FollowedArtistResponse;
import com.miniweverse.follow.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "팔로우", description = "아티스트 팔로우/언팔로우")
@SecurityRequirement(name = "bearerAuth")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @Operation(summary = "팔로우한 아티스트 목록 조회")
    @GetMapping("/follows")
    public ResponseEntity<List<FollowedArtistResponse>> getFollowedArtists(
            @AuthenticationPrincipal Long followerId
    ) {
        return ResponseEntity.ok(followService.getFollowedArtists(followerId));
    }

    @Operation(summary = "아티스트 팔로우")
    @PostMapping("/follows")
    public ResponseEntity<Void> follow(
            @AuthenticationPrincipal Long followerId,
            @Valid @RequestBody FollowRequest request
    ) {
        followService.follow(followerId, request.artistId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "아티스트 언팔로우")
    @DeleteMapping("/follows/{artistId}")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal Long followerId,
            @PathVariable Long artistId
    ) {
        followService.unfollow(followerId, artistId);
        return ResponseEntity.noContent().build();
    }
}
