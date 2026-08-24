package com.back.post.repository;

import com.back.post.domain.PostLike;
import com.back.post.domain.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {}
