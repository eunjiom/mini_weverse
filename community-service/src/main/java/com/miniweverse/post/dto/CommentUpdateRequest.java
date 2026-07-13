package com.miniweverse.post.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(
        @NotBlank String content
) {
}
