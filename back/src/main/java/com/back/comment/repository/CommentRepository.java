package com.back.comment.repository;

import com.back.comment.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = {"user"})
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.upvoteCount = c.upvoteCount + 1 WHERE c.id = :id")
    int increaseUpvote(@Param("id") Long commentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.upvoteCount = c.upvoteCount - 1 WHERE c.id = :id AND c.upvoteCount > 0")
    int decreaseUpvote(@Param("id") Long commentId);

    interface PostCommentCount {
        Long getPostId();
        long getCommentCount();
    }
}
