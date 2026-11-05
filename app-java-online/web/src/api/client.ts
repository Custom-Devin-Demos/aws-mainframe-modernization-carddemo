import { clearSession, getToken } from '../session/sessionStore';
import type { ScreenResponse } from './types';

/** Thrown for non-2xx responses; carries the COBOL error message envelope. */
export class ApiError<T = unknown> extends Error {
  constructor(
    readonly status: number,
    readonly response: ScreenResponse<T>,
  ) {
    super(response.message ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

export async function apiRequest<T>(method: Method, path: string, body?: unknown): Promise<ScreenResponse<T>> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let parsed: ScreenResponse<T> = {};
  if (text) {
    try {
      parsed = JSON.parse(text) as ScreenResponse<T>;
    } catch {
      parsed = { message: text, messageType: 'ERROR' };
    }
  }
  if (!res.ok) {
    if (res.status === 401) clearSession();
    throw new ApiError<T>(res.status, { messageType: 'ERROR', ...parsed });
  }
  return parsed;
}

export const api = {
  get: <T>(path: string) => apiRequest<T>('GET', path),
  post: <T>(path: string, body?: unknown) => apiRequest<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => apiRequest<T>('PUT', path, body),
  delete: <T>(path: string) => apiRequest<T>('DELETE', path),
};
