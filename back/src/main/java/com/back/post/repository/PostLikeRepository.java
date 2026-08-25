package com.back.post.repository;

import com.back.post.domain.PostLike;
import com.back.post.domain.PostLikeId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);
    void deleteByPost_IdAndUser_Id(Long postId, Long userId);
    Page<PostLike> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
