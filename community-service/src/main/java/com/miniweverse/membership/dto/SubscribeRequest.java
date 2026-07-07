package com.miniweverse.membership.dto;

import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(
        @NotNull Long artistId
) {
}
