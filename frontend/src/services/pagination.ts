export type SpringPagedModel<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};

export type Page<T> = {
  content: T[];
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
};

export function toPage<T>(payload: SpringPagedModel<T>): Page<T> {
  return {
    content: payload.content ?? [],
    size: payload.page?.size ?? 0,
    number: payload.page?.number ?? 0,
    totalElements: payload.page?.totalElements ?? 0,
    totalPages: payload.page?.totalPages ?? 0
  };
}
