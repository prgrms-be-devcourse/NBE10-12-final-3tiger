package com.back.notification.event;

public record CommentUpvotedEvent(Long receiverId, Long actorId, String actorNickname, String actorProfileImageUrl,
                                  Long postId, Long commentId) {}
