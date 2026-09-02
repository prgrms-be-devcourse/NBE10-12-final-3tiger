package com.back.hazard.dto;

public record HazardUpvoteResponse(
        boolean upvoted,
        int upvoteCount
) {
}
