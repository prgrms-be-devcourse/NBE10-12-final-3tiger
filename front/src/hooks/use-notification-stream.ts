import { type InfiniteData, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { AppState } from "react-native";
import EventSource, { type CustomEvent } from "react-native-sse";

import { API_BASE_URL } from "@/api/client";
import { useAuthStore } from "@/stores/auth-store";
import type { PageResponse } from "@/types/api";
import type {
  NotificationItem,
  NotificationUnreadCount,
} from "@/types/notification";

type NotificationStreamEvent = "connected" | "notification";

const notificationTypes = new Set(["LIKE", "COMMENT", "COMMENT_UPVOTE"]);

function isNotificationItem(value: unknown): value is NotificationItem {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;

  return (
    typeof item.id === "number" &&
    notificationTypes.has(String(item.type)) &&
    typeof item.postId === "number" &&
    (item.commentId === null || typeof item.commentId === "number") &&
    typeof item.actorId === "number" &&
    typeof item.actorNickname === "string" &&
    (item.actorProfileImageUrl === null ||
      typeof item.actorProfileImageUrl === "string") &&
    typeof item.read === "boolean" &&
    typeof item.createdAt === "string"
  );
}

export function useNotificationStream() {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [appState, setAppState] = useState(AppState.currentState);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", setAppState);
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      queryClient.removeQueries({ queryKey: ["notifications"] });
      queryClient.removeQueries({ queryKey: ["notification-unread-count"] });
    }
  }, [isAuthenticated, queryClient]);

  useEffect(() => {
    if (
      !isAuthenticated ||
      !accessToken ||
      !API_BASE_URL ||
      appState !== "active"
    )
      return;

    let source: EventSource<NotificationStreamEvent> | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let reconnectDelay = 1_000;
    let disposed = false;
    const receivedNotificationIds = new Set<number>();

    const synchronize = () => {
      void queryClient.invalidateQueries({
        queryKey: ["notification-unread-count"],
      });
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    };

    const connect = () => {
      if (disposed) return;
      const url =
        `${API_BASE_URL}/api/v1/notifications/subscribe` +
        `?token=${encodeURIComponent(accessToken)}`;

      source = new EventSource<NotificationStreamEvent>(url, {
        pollingInterval: 30_000,
        timeoutBeforeConnection: 0,
      });

      source.addEventListener("connected", () => {
        reconnectDelay = 1_000;
        synchronize();
      });

      source.addEventListener(
        "notification",
        (event: CustomEvent<"notification">) => {
          if (!event.data) return;
          try {
            const parsed: unknown = JSON.parse(event.data);
            if (!isNotificationItem(parsed)) {
              synchronize();
              return;
            }

            const cached = queryClient.getQueryData<
              InfiniteData<PageResponse<NotificationItem>, number>
            >(["notifications"]);
            const alreadyCached = cached?.pages.some((page) =>
              page.content.some((item) => item.id === parsed.id),
            );
            if (receivedNotificationIds.has(parsed.id) || alreadyCached) return;
            receivedNotificationIds.add(parsed.id);

            if (!parsed.read) {
              queryClient.setQueryData<NotificationUnreadCount>(
                ["notification-unread-count"],
                (previous) => ({ count: (previous?.count ?? 0) + 1 }),
              );
            }

            queryClient.setQueryData<
              InfiniteData<PageResponse<NotificationItem>, number>
            >(["notifications"], (previous) => {
              if (!previous) return previous;
              return {
                ...previous,
                pages: previous.pages.map((page, index) => ({
                  ...page,
                  content:
                    index === 0
                      ? [parsed, ...page.content].slice(0, page.size)
                      : page.content,
                  totalElements: page.totalElements + 1,
                })),
              };
            });

            void queryClient.invalidateQueries({
              queryKey: ["notifications"],
            });
            void queryClient.invalidateQueries({
              queryKey: ["notification-unread-count"],
            });
          } catch {
            synchronize();
          }
        },
      );

      source.addEventListener("error", () => {
        source?.close();
        source = null;
        if (disposed) return;
        reconnectTimer = setTimeout(() => {
          reconnectTimer = null;
          connect();
        }, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, 30_000);
      });
    };

    synchronize();
    connect();

    return () => {
      disposed = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      source?.removeAllEventListeners();
      source?.close();
    };
  }, [accessToken, appState, isAuthenticated, queryClient]);
}
