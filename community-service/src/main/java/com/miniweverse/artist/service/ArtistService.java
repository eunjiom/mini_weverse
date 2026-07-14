package com.miniweverse.artist.service;

import com.miniweverse.artist.dto.ArtistSearchResponse;
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
    public List<ArtistSearchResponse> search(String name, int page, int size) {
        return artistProfileRepository
                .findByChannelNameContainingIgnoreCaseOrderByIdDesc(name, PageRequest.of(page, size))
                .stream()
                .map(ArtistSearchResponse::from)
                .toList();
    }
}
