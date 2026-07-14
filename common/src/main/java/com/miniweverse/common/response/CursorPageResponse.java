package com.miniweverse.common.response;

import java.util.List;
import java.util.function.Function;

public record CursorPageResponse<T>(
        List<T> items,
        Long nextCursor,
        boolean hasNext
) {
    /**
     * 커서 조회는 항상 {@code size + 1}개를 가져오도록 호출한다 — 결과가 {@code size}개를 넘으면
     * 다음 페이지가 있다는 뜻이므로 마지막 1개는 잘라내고 {@code hasNext=true}로 표시한다.
     */
    public static <T> CursorPageResponse<T> of(List<T> fetched, int size, Function<T, Long> cursorExtractor) {
        boolean hasNext = fetched.size() > size;
        List<T> items = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? cursorExtractor.apply(items.get(items.size() - 1)) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }
}
