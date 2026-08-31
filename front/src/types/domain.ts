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
};

export type Post = {
  postId: number;
  courseId: number;
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
  isLiked: boolean;
  isBookmarked: boolean;
  isMine: boolean;
};

export type LikedPostItem = Post & {
  isBookmarked: boolean;
  isMine: boolean;
};

export type PostComment = {
  commentId: number;
  userId: number;
  nickname: string;
  profileImageUrl?: string | null;
  content: string;
  upvoteCount: number;
  /** 현재 로그인한 사용자가 공감했는지 여부 */
  upvoted?: boolean;
  createdAt: string;
};

export type Region = {
  regionCode: string;
  name: string;
  centerLat: number;
  centerLng: number;
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
  errorPct: number;
  regionCode: string;
};

export type GenerateResponse = {
  candidates: GenerateCandidate[];
  requestedCount: number;
  returnedCount: number;
};
