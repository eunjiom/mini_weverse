package com.miniweverse.artist.service;

import com.miniweverse.artist.dto.ArtistSearchResponse;
import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.user.repository.ArtistProfileRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistService {

    private final ArtistProfileRepository artistProfileRepository;

    public ArtistService(ArtistProfileRepository artistProfileRepository) {
        this.artistProfileRepository = artistProfileRepository;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ArtistSearchResponse> search(String name, Long cursor, int size) {
        List<ArtistSearchResponse> fetched = artistProfileRepository
                .findByChannelNameAndCursor(name, cursor, PageRequest.of(0, size + 1))
                .stream()
                .map(ArtistSearchResponse::from)
                .toList();
        return CursorPageResponse.of(fetched, size, ArtistSearchResponse::artistId);
    }
}
