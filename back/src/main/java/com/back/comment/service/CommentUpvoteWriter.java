package com.back.comment.service;

import com.back.comment.domain.Comment;
import com.back.comment.domain.CommentUpvote;
import com.back.comment.repository.CommentUpvoteRepository;
import com.back.user.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CommentUpvoteWriter {
    private final CommentUpvoteRepository commentUpvotes;
    public CommentUpvoteWriter(CommentUpvoteRepository commentUpvotes) {
        this.commentUpvotes = commentUpvotes;
    }

    // 별도(REQUIRES_NEW) 트랜잭션으로 격리: saveAndFlush가 유니크 제약 위반으로 실패해도
    // 호출한 쪽의 바깥 트랜잭션까지 rollback-only로 오염되지 않도록 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySaveUpvote(Comment comment, User user) {
        commentUpvotes.saveAndFlush(new CommentUpvote(comment, user));
    }
}
