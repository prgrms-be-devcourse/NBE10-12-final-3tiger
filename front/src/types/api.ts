export type ApiResponse<T> = {
  resultCode: string;
  message: string;
  data: T;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
};

export type PageParams = {
  page?: number;
  size?: number;
};

export class ApiError extends Error {
  status?: number;
  resultCode?: string;

  constructor(message: string, status?: number, resultCode?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.resultCode = resultCode;
  }
}
