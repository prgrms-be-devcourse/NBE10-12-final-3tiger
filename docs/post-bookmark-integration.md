# POST / BOOKMARK 연동 안내

## 현재 구현 결정

- 코스 저장은 `bookmark(user_id, course_id, created_at)` 독립 테이블을 사용한다.
- 게시물 좋아요는 `share_post_like(post_id, user_id, created_at)` 독립 테이블을 사용한다.
- 게시물 사진은 `share_post.photo_url`에 완성된 URL을 저장한다.
- 사진 업로드 URL 발급은 `PhotoStorage` 인터페이스 뒤에 로컬 구현을 두었다. S3/R2 연동 시 구현체만 교체한다.

## 현재 연동 상태

### 인증

인증이 필요한 요청은 `Authorization: Bearer <access-token>`을 사용한다.
JWT 필터가 검증한 사용자 ID를 SecurityContext에 저장하고 `CurrentUserIdResolver`가 이를 사용한다.
`X-User-Id` 헤더와 개발용 기본 사용자는 더 이상 지원하지 않는다.

### User / Course

POST와 BOOKMARK는 정식 `User`, `Course` 엔티티를 참조한다. `Course`의 PK는 실제 스키마의
`course_id` 컬럼에 매핑한다.

### 사진 저장소

로컬 `LocalPhotoStorage`가 반환하는 URL은 계약 확인용이며 실제 파일 업로드를 수행하지 않는다.
운영에서는 R2/S3 SDK 기반 presigned PUT URL과 공개 `photoUrl`을 반환하는 구현체를 추가한다.

## 요청 호환성

게시물 작성 요청의 사진 필드는 `photoUrl`이다.
내 게시글 API에는 명세에 빠진 `page`, `size` 파라미터를 다른 목록 API와 동일한 기본값(0, 20)으로 지원한다.

게시물 작성 요청은 `title`, `content`를 사용한다. 기존 DB의 `caption` 컬럼은 내용 저장 컬럼으로
유지하며 `post-title-migration.sql`이 `title` 컬럼을 추가한다.
