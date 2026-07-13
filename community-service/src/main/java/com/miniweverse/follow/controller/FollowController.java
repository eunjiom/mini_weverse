package com.miniweverse.follow.controller;

import com.miniweverse.follow.dto.FollowRequest;
import com.miniweverse.follow.dto.FollowResponse;
import com.miniweverse.follow.dto.FollowedArtistResponse;
import com.miniweverse.follow.service.FollowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping("/follows")
    public ResponseEntity<List<FollowedArtistResponse>> getFollowedArtists(
            @AuthenticationPrincipal Long followerId
    ) {
        return ResponseEntity.ok(followService.getFollowedArtists(followerId));
    }

    @PostMapping("/follows")
    public ResponseEntity<FollowResponse> follow(
            @AuthenticationPrincipal Long followerId,
            @Valid @RequestBody FollowRequest request
    ) {
        followService.follow(followerId, request.artistId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new FollowResponse(request.artistId()));
    }

    @DeleteMapping("/follows/{artistId}")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal Long followerId,
            @PathVariable Long artistId
    ) {
        followService.unfollow(followerId, artistId);
        return ResponseEntity.noContent().build();
    }
}
