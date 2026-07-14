package com.miniweverse.post.controller;

import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.post.dto.CommentCreateRequest;
import com.miniweverse.post.dto.CommentResponse;
import com.miniweverse.post.dto.CommentUpdateRequest;
import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.service.CommentService;
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
    public ResponseEntity<CursorPageResponse<CommentResponse>> list(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long postId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(commentService.getByPost(viewerId, postId, cursor, size));
    }

    @GetMapping("/users/{userId}/comments")
    public ResponseEntity<CursorPageResponse<CommentResponse>> listByAuthor(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(commentService.getByAuthor(viewerId, userId, cursor, size));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommentResponse> update(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        Comment comment = commentService.update(commentId, requesterId, request.content());
        return ResponseEntity.ok(CommentResponse.from(comment));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long commentId
    ) {
        commentService.delete(commentId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
