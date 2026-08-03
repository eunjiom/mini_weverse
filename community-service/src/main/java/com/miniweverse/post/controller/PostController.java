package com.miniweverse.post.controller;

import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.post.dto.PostCreateRequest;
import com.miniweverse.post.dto.PostResponse;
import com.miniweverse.post.dto.PostUpdateRequest;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "게시글", description = "아티스트 커뮤니티 게시글 작성, 조회, 수정, 삭제")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "게시글 작성", description = "특정 아티스트 커뮤니티에 게시글을 작성합니다. membersOnly로 멤버십 전용 게시글 여부를 지정할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/artists/{artistId}/posts")
    public ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal Long authorId,
            @PathVariable Long artistId,
            @Valid @RequestBody PostCreateRequest request
    ) {
        Post post = postService.create(authorId, artistId, request.boardType(), request.content(), request.membersOnly());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    @Operation(summary = "게시글 목록 조회", description = "특정 아티스트의 게시판 타입별 게시글을 커서 기반 페이지네이션으로 조회합니다.")
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

    @Operation(summary = "게시글 단건 조회")
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> get(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(postService.getById(viewerId, postId));
    }

    @Operation(summary = "특정 유저 작성 게시글 목록 조회", description = "특정 유저가 작성한 게시글을 커서 기반 페이지네이션으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/users/{userId}/posts")
    public ResponseEntity<CursorPageResponse<PostResponse>> listByAuthor(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(postService.getByAuthor(viewerId, userId, cursor, size));
    }

    @Operation(summary = "게시글 수정", description = "작성자 본인만 게시글 내용을 수정할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> update(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        Post post = postService.update(postId, requesterId, request.content());
        return ResponseEntity.ok(PostResponse.from(post));
    }

    @Operation(summary = "게시글 삭제", description = "작성자 본인만 게시글을 삭제할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long postId
    ) {
        postService.delete(postId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
