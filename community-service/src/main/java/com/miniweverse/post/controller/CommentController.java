package com.miniweverse.post.controller;

import com.miniweverse.post.dto.CommentCreateRequest;
import com.miniweverse.post.dto.CommentResponse;
import com.miniweverse.post.dto.CommentUpdateRequest;
import com.miniweverse.post.entity.Comment;
import com.miniweverse.post.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
    public ResponseEntity<List<CommentResponse>> list(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        List<CommentResponse> comments = commentService.getByPost(postId, page, size).stream()
                .map(CommentResponse::from)
                .toList();
        return ResponseEntity.ok(comments);
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
