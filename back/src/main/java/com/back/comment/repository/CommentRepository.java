package com.back.comment.repository;

import com.back.comment.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPost_IdOrderByCreatedAtDesc(Long postId, Pageable pageable);
    long countByPost_Id(Long postId);
}
