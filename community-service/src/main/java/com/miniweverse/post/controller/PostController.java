package com.miniweverse.post.controller;

import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.post.dto.PostCreateRequest;
import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.dto.PostUpdateRequest;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/artists/{artistId}/posts")
    public ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal Long authorId,
            @PathVariable Long artistId,
            @Valid @RequestBody PostCreateRequest request
    ) {
        Post post = postService.create(authorId, artistId, request.boardType(), request.content(), request.membersOnly());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    @GetMapping("/artists/{artistId}/posts")
    public ResponseEntity<CursorPageResponse<PostResponse>> list(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId,
            @RequestParam BoardType boardType,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(postService.getByArtistAndBoardType(viewerId, artistId, boardType, cursor, size));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> get(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(postService.getById(viewerId, postId));
    }

    @PatchMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> update(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        Post post = postService.update(postId, requesterId, request.content());
        return ResponseEntity.ok(PostResponse.from(post));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long postId
    ) {
        postService.delete(postId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
