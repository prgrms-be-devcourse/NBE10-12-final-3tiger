export type NotificationType = "LIKE" | "COMMENT" | "COMMENT_UPVOTE";

export type NotificationItem = {
  id: number;
  type: NotificationType;
  postId: number;
  commentId: number | null;
  actorId: number;
  actorNickname: string;
  actorProfileImageUrl: string | null;
  read: boolean;
  createdAt: string;
};

export type NotificationUnreadCount = {
  count: number;
};
