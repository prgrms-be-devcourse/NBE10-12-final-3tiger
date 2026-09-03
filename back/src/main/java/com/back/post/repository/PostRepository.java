package com.back.post.repository;

import com.back.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Override
    @EntityGraph(attributePaths = {"user", "course"})
    Page<Post> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "course"})
    Page<Post> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "course"})
    Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    int increaseLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    int decreaseLikeCount(@Param("postId") Long postId);
}
