package com.back.comment.repository;

import com.back.comment.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 원댓글(parent == null)만 페이징. 정렬은 Pageable 로 받는다(최신순/공감순).
    // 답글은 findVisibleReplies 로 별도 일괄 조회 (N+1 방지).
    // 숨김(hidden) 댓글과 차단 관계 사용자의 댓글은 제외한다.
    // excludedUserIds 는 절대 비어 있으면 안 된다(빈 in 절 방지). 호출 측에서 sentinel 을 넣는다.
    @EntityGraph(attributePaths = {"user"})
    @Query("select c from Comment c where c.post.id = :postId and c.parent is null "
            + "and c.hidden = false and c.user.id not in :excludedUserIds")
    Page<Comment> findVisibleParents(@Param("postId") Long postId,
            @Param("excludedUserIds") Collection<Long> excludedUserIds, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("select c from Comment c where c.parent.id in :parentIds "
            + "and c.hidden = false and c.user.id not in :excludedUserIds order by c.createdAt asc")
    List<Comment> findVisibleReplies(@Param("parentIds") Collection<Long> parentIds,
            @Param("excludedUserIds") Collection<Long> excludedUserIds);

    boolean existsByParent_Id(Long parentId);

    long countByPost_Id(Long postId);

    @Query("""
            select c.post.id as postId, count(c.id) as commentCount
            from Comment c
            where c.post.id in :postIds
            group by c.post.id
            """)
    List<PostCommentCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Comment c where c.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.upvoteCount = c.upvoteCount + 1 WHERE c.id = :id")
    int increaseUpvote(@Param("id") Long commentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.upvoteCount = c.upvoteCount - 1 WHERE c.id = :id AND c.upvoteCount > 0")
    int decreaseUpvote(@Param("id") Long commentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.hidden = true WHERE c.id = :id AND c.hidden = false")
    int hide(@Param("id") Long commentId);

    interface PostCommentCount {
        Long getPostId();
        long getCommentCount();
    }
}
