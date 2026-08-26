export type UserProfile = {
  userId: number;
  nickname: string;
  email: string;
  loginType: string;
  profileImageUrl?: string | null;
  primaryPersona?: string | null;
  personaTags?: string[];
};

export type CourseScores = {
  flatness?: number;
  avgSlopeDegree?: number;
  shadeSummer?: number;
  windShelter?: number;
  wheelchair?: number;
  surfaceType?: string;
};

export type Course = {
  courseId: number;
  name: string;
  distanceM: number;
  estimatedMinutes?: number;
  isLoop?: boolean;
  startPoint?: { lat: number; lng: number };
  path?:
    { type?: string; coordinates?: [number, number][] } | [number, number][];
  scores?: CourseScores;
  surfaceTempC?: number;
  personaBadges?: string[];
  myFavorite?: boolean;
  imageUrl?: string;
  summary?: string;
  bookmarkedAt?: string;
};

export type Post = {
  postId: number;
  courseId: number;
  nickname?: string;
  caption: string;
  photoUrl?: string;
  likeCount: number;
  commentCount?: number;
  walkedAt: string;
  likedAt?: string;
  isLiked?: boolean;
  isBookmarked?: boolean;
  profileImageUrl?: string;
};

export type CourseComment = {
  commentId: number;
  userId: number;
  nickname: string;
  content: string;
  upvoteCount: number;
  createdAt: string;
};

export type Region = {
  regionCode: string;
  name: string;
  centerLat: number;
  centerLng: number;
  courseCount: number;
};
