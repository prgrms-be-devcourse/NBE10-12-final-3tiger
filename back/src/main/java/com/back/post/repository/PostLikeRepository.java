package com.back.post.repository;

import com.back.post.domain.PostLike;
import com.back.post.domain.PostLikeId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);
    int deleteByPost_IdAndUser_Id(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "post.user", "post.course"})
    Page<PostLike> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select pl.post.id from PostLike pl where pl.user.id = :userId and pl.post.id in :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PostLike pl where pl.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);
}
