import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import { getBlockedUsers } from "@/api/user-block-api";
import { useAuthStore } from "@/stores/auth-store";

/** 차단 목록 페이지네이션 조회에 쓰는 쿼리 키 */
export const BLOCKED_USERS_KEY = ["blocked-users"] as const;
/** 메뉴에서 차단 여부 판단에 쓰는 경량 조회 키 */
export const BLOCKED_USER_IDS_KEY = ["blocked-user-ids"] as const;

/**
 * 현재 로그인 사용자가 차단한 상대 id 집합.
 * 메뉴에서 "차단하기 / 차단 해제" 토글 라벨을 정하는 데 쓴다.
 */
export function useBlockedUserIds() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const query = useQuery({
    queryKey: BLOCKED_USER_IDS_KEY,
    queryFn: () => getBlockedUsers({ page: 0, size: 200 }),
    enabled: isAuthenticated,
    staleTime: 60_000,
  });

  const blockedUserIds = useMemo(
    () => new Set((query.data?.content ?? []).map((user) => user.userId)),
    [query.data],
  );

  return {
    blockedUserIds,
    isBlocked: (userId?: number | null) =>
      userId != null && blockedUserIds.has(userId),
  };
}
