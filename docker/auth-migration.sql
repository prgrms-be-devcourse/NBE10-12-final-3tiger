-- Auth domain schema migration
-- 기존 "user" 테이블에 인증 기능에 필요한 컬럼/제약 추가
-- IF NOT EXISTS / DO 블록으로 멱등 처리 (재실행 안전)

-- 1. 로컬 가입 유저 비밀번호 (소셜 유저는 NULL)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) NULL;

-- 2. 소프트 삭제 지원
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

-- 3. Spring Data Auditing @LastModifiedDate 매핑 대상 (BaseEntity.updatedAt → updated_at)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 4. 로컬 가입 유저는 provider_uid 없음 → nullable로 변경
ALTER TABLE "user" ALTER COLUMN provider_uid DROP NOT NULL;

-- 5. 이메일 UNIQUE 제약 추가 (멱등)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_user_email') THEN
        ALTER TABLE "user" ADD CONSTRAINT uk_user_email UNIQUE (email);
    END IF;
END $$;
