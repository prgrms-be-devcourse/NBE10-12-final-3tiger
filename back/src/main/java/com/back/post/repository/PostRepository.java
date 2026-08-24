package com.back.post.repository;

import com.back.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByCourseRegionCode(String regionCode, Pageable pageable);
    Page<Post> findByUserId(Long userId, Pageable pageable);
}
