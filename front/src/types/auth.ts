export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
  isNewUser?: boolean;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type SignupRequest = LoginRequest & {
  nickname: string;
};
