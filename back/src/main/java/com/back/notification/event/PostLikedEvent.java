package com.back.notification.event;

public record PostLikedEvent(Long receiverId, Long actorId, String actorNickname, String actorProfileImageUrl, Long postId) {}
