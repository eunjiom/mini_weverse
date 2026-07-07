package com.miniweverse.follow.dto;

import jakarta.validation.constraints.NotNull;

public record FollowRequest(
        @NotNull Long artistId
) {
}
