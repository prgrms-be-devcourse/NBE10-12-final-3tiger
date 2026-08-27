# POST / BOOKMARK 연동 안내

## 현재 구현 결정

- 코스 저장은 `bookmark(user_id, course_id, created_at)` 독립 테이블을 사용한다.
- 게시물 좋아요는 `share_post_like(post_id, user_id, created_at)` 독립 테이블을 사용한다.
- 게시물 사진은 `share_post.photo_url`에 완성된 URL을 저장한다.
- 사진 업로드 URL 발급은 `PhotoStorage` 인터페이스로 로컬과 S3 구현을 분리한다.

## 현재 연동 상태

### 인증

인증이 필요한 요청은 `Authorization: Bearer <access-token>`을 사용한다.
JWT 필터가 검증한 사용자 ID를 SecurityContext에 저장하고 `CurrentUserIdResolver`가 이를 사용한다.
`X-User-Id` 헤더와 개발용 기본 사용자는 더 이상 지원하지 않는다.

### User / Course

POST와 BOOKMARK는 정식 `User`, `Course` 엔티티를 참조한다. `Course`의 PK는 실제 스키마의
`course_id` 컬럼에 매핑한다.

### 사진 저장소

기본값인 `STORAGE_TYPE=local`에서는 인증된 PUT 요청으로 `build/local-uploads`에 파일을 저장한다.
운영에서 `STORAGE_TYPE=s3`로 설정하면 AWS SDK가 5분 유효한 presigned PUT URL을 발급하고,
클라이언트가 애플리케이션 서버를 거치지 않고 S3에 직접 업로드한다.

S3 모드에는 `S3_BUCKET`, `AWS_REGION`, `S3_PUBLIC_BASE_URL`과 AWS SDK 기본 자격 증명 체인이
인식할 자격 증명이 필요하다. 운영에서는 액세스 키보다 IAM Role 사용을 권장한다.
`S3_PUBLIC_BASE_URL`에서 객체를 읽을 수 있도록 CloudFront OAC 또는 버킷 정책을 별도로 설정해야 한다.
Expo 웹에서도 업로드한다면 버킷 CORS에 프런트엔드 Origin의 `PUT`과 `Content-Type` 헤더를 허용한다.

## 요청 호환성

게시물 작성 요청의 사진 필드는 `photoUrl`이다.
내 게시글 API에는 명세에 빠진 `page`, `size` 파라미터를 다른 목록 API와 동일한 기본값(0, 20)으로 지원한다.

게시물 작성 요청은 `title`, `content`를 사용한다. 기존 DB의 `caption` 컬럼은 내용 저장 컬럼으로
유지하며 `post-title-migration.sql`이 `title` 컬럼을 추가한다.
