package com.miniweverse.post.controller;

import com.miniweverse.post.dto.CommentCreateRequest;
import com.miniweverse.post.dto.CommentResponse;
import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(
            @AuthenticationPrincipal Long authorId,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        Comment comment = commentService.create(authorId, postId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> list(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long postId
    ) {
        List<CommentResponse> comments = commentService.getByPost(viewerId, postId).stream()
                .map(CommentResponse::from)
                .toList();
        return ResponseEntity.ok(comments);
    }
}
