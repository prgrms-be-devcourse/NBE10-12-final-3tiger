-- Persona enum 정합성: 기존 default 'general'는 Java Persona enum(dog/senior/stroller/walker)에 없어
-- 해당 값을 가진 유저의 데이터를 읽을 때 500 유발. 존재하는 값 walker로 통일 + 새 default도 walker.
-- 멱등: WHERE 필터로 중복 실행 시 no-op.

BEGIN;

UPDATE "user"
   SET persona_pref = 'walker'
 WHERE persona_pref = 'general';

ALTER TABLE "user"
    ALTER COLUMN persona_pref SET DEFAULT 'walker';

COMMIT;
