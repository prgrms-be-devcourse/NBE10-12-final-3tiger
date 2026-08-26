package com.back.comment.repository;

import com.back.comment.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPost_IdOrderByCreatedAtDesc(Long postId, Pageable pageable);
    long countByPost_Id(Long postId);

    @Query("""
            select c.post.id as postId, count(c.id) as commentCount
            from Comment c
            where c.post.id in :postIds
            group by c.post.id
            """)
    List<PostCommentCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Comment c where c.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);

    interface PostCommentCount {
        Long getPostId();
        long getCommentCount();
    }
}
