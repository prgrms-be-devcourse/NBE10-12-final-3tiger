import { apiRequest } from "@/api/client";
import type { PageParams, PageResponse } from "@/types/api";
import type {
  NotificationItem,
  NotificationSetting,
  NotificationUnreadCount,
} from "@/types/notification";

export const getNotifications = (params: PageParams) =>
  apiRequest<PageResponse<NotificationItem>>({
    url: "/api/v1/notifications",
    params,
  });

export const getUnreadNotificationCount = () =>
  apiRequest<NotificationUnreadCount>({
    url: "/api/v1/notifications/unread-count",
  });

export const readNotification = (notificationId: number) =>
  apiRequest<void>({
    url: `/api/v1/notifications/${notificationId}/read`,
    method: "PATCH",
  });

export const readAllNotifications = () =>
  apiRequest<void>({
    url: "/api/v1/notifications/read-all",
    method: "PATCH",
  });

export const getNotificationSetting = () =>
  apiRequest<NotificationSetting>({ url: "/api/v1/notifications/setting" });

export const updateNotificationSetting = (enabled: boolean) =>
  apiRequest<NotificationSetting>({
    url: "/api/v1/notifications/setting",
    method: "PATCH",
    data: { enabled },
  });
