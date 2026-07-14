package com.miniweverse.post.dto;

import com.miniweverse.post.enums.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostCreateRequest(
        @NotNull BoardType boardType,
        @NotBlank String content,
        boolean membersOnly
) {
}
