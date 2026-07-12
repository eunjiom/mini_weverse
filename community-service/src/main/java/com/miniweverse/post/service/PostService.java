package com.miniweverse.post.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.post.entity.Post;
import com.miniweverse.post.enums.BoardType;
import com.miniweverse.post.repository.PostRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.ArtistProfileRepository;
import com.miniweverse.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final FollowRepository followRepository;

    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository,
            FollowRepository followRepository
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.followRepository = followRepository;
    }

    @Transactional
    public Post create(Long authorId, Long artistUserId, BoardType boardType, String content) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new InvalidRequestException("작성자 정보를 찾을 수 없습니다."));
        User artistUser = userRepository.findById(artistUserId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));
        ArtistProfile artistProfile = artistProfileRepository.findByUser(artistUser)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        if (boardType == BoardType.FEED && !followRepository.existsByFollowerAndArtist(author.getId(), artistUser.getId())) {
            throw new InvalidRequestException("팔로우한 아티스트의 피드 게시판에만 글을 작성할 수 있습니다.");
        }

        return postRepository.save(Post.create(author, artistProfile, boardType, content));
    }

    @Transactional(readOnly = true)
    public List<Post> getByArtistAndBoardType(Long artistUserId, BoardType boardType) {
        User artistUser = userRepository.findById(artistUserId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));
        ArtistProfile artistProfile = artistProfileRepository.findByUser(artistUser)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));
        return postRepository.findByArtistProfileAndBoardTypeOrderByCreatedAtDesc(artistProfile, boardType);
    }
}
