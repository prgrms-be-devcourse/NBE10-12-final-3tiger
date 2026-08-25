package com.back.comment.repository;

import com.back.comment.domain.CommentUpvote;
import com.back.comment.domain.CommentUpvoteId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentUpvoteRepository extends JpaRepository<CommentUpvote, CommentUpvoteId> {
    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);
    void deleteByComment_IdAndUser_Id(Long commentId, Long userId);
}
