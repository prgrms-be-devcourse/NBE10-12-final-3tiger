package com.back.notification.event;

public record CommentCreatedEvent(Long receiverId, Long actorId, String actorNickname, String actorProfileImageUrl,
                                  Long postId, Long commentId) {}
