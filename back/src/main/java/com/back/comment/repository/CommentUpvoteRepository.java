package com.back.comment.repository;

import com.back.comment.domain.CommentUpvote;
import com.back.comment.domain.CommentUpvoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentUpvoteRepository extends JpaRepository<CommentUpvote, CommentUpvoteId> {
    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);
    long countByComment_Post_Id(Long postId);
    int deleteByComment_IdAndUser_Id(Long commentId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CommentUpvote cu where cu.comment.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);
}
