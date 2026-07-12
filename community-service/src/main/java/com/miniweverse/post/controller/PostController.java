package com.miniweverse.post.controller;

import com.miniweverse.post.dto.PostCreateRequest;
import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.service.PostService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
        Post post = postService.create(authorId, artistId, request.boardType(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    @GetMapping("/artists/{artistId}/posts")
    public ResponseEntity<List<PostResponse>> list(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long artistId,
            @RequestParam BoardType boardType
    ) {
        List<PostResponse> posts = postService.getByArtistAndBoardType(viewerId, artistId, boardType);
        return ResponseEntity.ok(posts);
    }
}
