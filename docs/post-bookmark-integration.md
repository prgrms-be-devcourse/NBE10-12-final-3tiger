# POST / BOOKMARK 임시 연동 안내

## 현재 구현 결정

- 코스 저장은 `bookmark(user_id, course_id, created_at)` 독립 테이블을 사용한다.
- 게시물 좋아요는 `share_post_like(post_id, user_id, created_at)` 독립 테이블을 사용한다.
- 게시물 사진은 `share_post.photo_url`에 완성된 URL을 저장한다.
- 사진 업로드 URL 발급은 `PhotoStorage` 인터페이스 뒤에 로컬 구현을 두었다. S3/R2 연동 시 구현체만 교체한다.

## 팀 코드 병합 시 교체할 임시 요소

### 인증

현재 인증 API가 없으므로 `app.auth.allow-dev-user=true`일 때 인증 헤더가 없는 요청은
`app.auth.dev-user-id`(기본값 1)의 임시 사용자로 처리한다. 다른 사용자를 시험할 때만
`X-User-Id` 헤더를 사용한다. 로컬 H2 실행 시 ID 1 사용자와 임시 코스도 자동 생성된다.

운영 설정에서는 반드시 `app.auth.allow-dev-user=false`로 변경해야 한다. JWT가 병합되면
`CurrentUserIdResolver`가 SecurityContext의 인증 사용자 ID를 반환하도록 교체하고
`SecurityConfig`의 `permitAll` 설정을 실제 접근 정책으로 변경해야 한다.

### User / Course

POST와 BOOKMARK의 FK 및 테스트를 위해 최소 필드만 가진 임시 `User`, `Course` 엔티티를 추가했다.
팀원의 정식 엔티티가 병합되면 패키지 import와 조회 Repository를 정식 도메인에 맞게 변경해야 한다.

### 사진 저장소

로컬 `LocalPhotoStorage`가 반환하는 URL은 계약 확인용이며 실제 파일 업로드를 수행하지 않는다.
운영에서는 R2/S3 SDK 기반 presigned PUT URL과 공개 `photoUrl`을 반환하는 구현체를 추가한다.

## 요청 호환성

게시물 작성 요청의 공식 필드는 `photoUrl`이다. 기존 명세의 `photoUri`도 `@JsonAlias`로 임시 허용한다.
내 게시글 API에는 명세에 빠진 `page`, `size` 파라미터를 다른 목록 API와 동일한 기본값(0, 20)으로 지원한다.
