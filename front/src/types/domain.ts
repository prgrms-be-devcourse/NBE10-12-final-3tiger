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

export type CourseScoreBars = {
  flatness?: number | null;
  avgSlopeDegree?: number | null;
  shade?: number | null;
  surfaceTemp?: number | null;
  amenity?: number | null;
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
  isBookmarked?: boolean;
  imageUrl?: string;
  summary?: string;
  bookmarkedAt?: string;
};

export type CourseDetail = Course & {
  elevationGainM?: number | null;
  elevationLossM?: number | null;
  source?: string | null;
  scoreBars?: CourseScoreBars | null;
  scoreWalker?: number | null;
  scoreSenior?: number | null;
  scoreStroller?: number | null;
  scoreDog?: number | null;
  surfaceType?: string | null;
};

export type BookmarkedCourse = Course & {
  isBookmarked: boolean;
  rating?: number | null;
  usageCount?: number;
  lastUsedAt?: string | null;
};

export type CourseUsageLog = {
  usageLogId: number;
  courseId: number;
  usedAt: string;
};

export type Post = {
  postId: number;
  courseId: number;
  title?: string;
  userId?: number;
  nickname?: string;
  content: string;
  photoUrl?: string;
  likeCount: number;
  commentCount?: number;
  walkedAt: string;
  likedAt?: string;
  isLiked?: boolean;
  isBookmarked?: boolean;
  profileImageUrl?: string;
};

export type PostFeedItem = Post & {
  title: string;
  userId: number;
  isLiked: boolean;
  isBookmarked: boolean;
  isMine: boolean;
};

export type PersonalUserMemo = {
  targetUserId: number;
  tags: string[];
  memo?: string | null;
  updatedAt: string;
};

export type LikedPostItem = Post & {
  isBookmarked: boolean;
  isMine: boolean;
};

export type PostComment = {
  commentId: number;
  userId: number;
  nickname: string;
  /** 서버 응답에는 아직 포함되지 않는 필드 (항상 undefined) */
  profileImageUrl?: string | null;
  content: string;
  upvoteCount: number;
  /** 현재 로그인한 사용자가 공감했는지 여부 */
  isUpvoted: boolean;
  /** 소프트 삭제된 원댓글이면 true. content는 "삭제된 댓글입니다."로 마스킹되어 옴 */
  isDeleted: boolean;
  createdAt: string;
  /** 원댓글에만 채워짐 (createdAt ASC 고정 정렬). 답글의 replies는 항상 빈 배열 (depth 1) */
  replies: PostComment[];
};

export type Region = {
  regionCode: string;
  name: string;
  centerLat: number;
  centerLng: number;
  bbox: string;
  courseCount: number;
};

export type GridOverlay = {
  gridId: number;
  regionCode: string;
  centroidLat: number;
  centroidLng: number;
  flatness: number | null;
  shadeSummer: number | null;
  shadeWinterSun: number | null;
  trafficLow: number | null;
  wheelchair: number | null;
  surfaceNatural: number | null;
  benchDensity: number | null;
  restroomProximity: number | null;
  waterFacility: number | null;
};

export type GeoJsonLineString = {
  type?: string;
  coordinates: [number, number][];
};

export type GenerateCandidate = {
  path: GeoJsonLineString;
  totalM: number;
  avgScore: number;
  errorPct: number | null;
  regionCode: string;
};

export type GenerateResponse = {
  candidates: GenerateCandidate[];
  requestedCount: number;
  returnedCount: number;
};
