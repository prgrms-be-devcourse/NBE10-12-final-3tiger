package com.back.post.service;

import com.back.post.domain.Post;
import com.back.post.domain.PostLike;
import com.back.post.repository.PostLikeRepository;
import com.back.user.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostLikeWriter {
    private final PostLikeRepository postLikes;
    public PostLikeWriter(PostLikeRepository postLikes) {
        this.postLikes = postLikes;
    }

    // 별도(REQUIRES_NEW) 트랜잭션으로 격리: saveAndFlush가 유니크 제약 위반으로 실패해도
    // 호출한 쪽의 바깥 트랜잭션까지 rollback-only로 오염되지 않도록 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySaveLike(Post post, User user) {
        postLikes.saveAndFlush(new PostLike(post, user));
    }
}
