package com.back.post.repository;

import com.back.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Override
    @EntityGraph(attributePaths = {"user", "course"})
    Page<Post> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "course"})
    Page<Post> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "course"})
    Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // 피드 조회: 숨김(hidden) 게시물과 차단 관계 사용자의 게시물을 제외한다.
    // excludedUserIds 는 절대 비어 있으면 안 된다(빈 in 절 방지). 호출 측에서 sentinel 을 넣는다.
    @EntityGraph(attributePaths = {"user", "course"})
    @Query("select p from Post p where p.hidden = false and p.user.id not in :excludedUserIds")
    Page<Post> findFeed(@Param("excludedUserIds") Collection<Long> excludedUserIds, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "course"})
    @Query("select p from Post p where p.hidden = false and p.user.id not in :excludedUserIds "
            + "and lower(p.title) like lower(concat('%', :keyword, '%'))")
    Page<Post> searchFeed(@Param("keyword") String keyword,
            @Param("excludedUserIds") Collection<Long> excludedUserIds, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.hidden = true WHERE p.id = :postId AND p.hidden = false")
    int hide(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    int increaseLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    int decreaseLikeCount(@Param("postId") Long postId);
}
