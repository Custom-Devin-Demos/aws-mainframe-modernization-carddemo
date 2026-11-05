import { api, ApiError } from '../../api/client';
import type { KeysetPage, MessageType, ScreenResponse } from '../../api/types';

/** com.carddemo.online.user.UserView: a USRSEC row without the password. */
export interface UserView {
  userId: string;
  firstName: string;
  lastName: string;
  userType: string;
}

export type UserPage = KeysetPage<UserView>;

export interface ScreenMessage {
  text?: string;
  type?: MessageType;
}

/** CCDA-MSG-INVALID-KEY (CSMSG01Y). */
export const MSG_INVALID_KEY = 'Invalid key pressed. Please see below...';

export function query(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') q.set(k, String(v));
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

export const userApi = {
  list: (params: Record<string, string | number | undefined>) => api.get<UserPage>(`/api/users${query(params)}`),
  add: (body: UserView & { password: string }) => api.post<UserView>('/api/users', body),
  fetch: (userId: string, mode: 'update' | 'delete') =>
    api.get<UserView>(`/api/users/${encodeURIComponent(userId || ' ')}${query({ mode })}`),
  update: (userId: string, body: Omit<UserView, 'userId'> & { password: string }) =>
    api.put<UserView>(`/api/users/${encodeURIComponent(userId || ' ')}`, body),
  remove: (userId: string) => api.delete<UserView>(`/api/users/${encodeURIComponent(userId || ' ')}`),
};

/** Unwraps a ScreenResponse from either a successful call or an ApiError. */
export async function call<T>(p: Promise<ScreenResponse<T>>): Promise<{ ok: boolean; res: ScreenResponse<T> }> {
  try {
    return { ok: true, res: await p };
  } catch (e) {
    if (e instanceof ApiError) return { ok: false, res: e.response as ScreenResponse<T> };
    return { ok: false, res: { message: String(e), messageType: 'ERROR' } };
  }
}

/** Moves the cursor (MOVE -1 TO xxxL) to the input named by ScreenResponse.field. */
export function focusField(field: string | undefined) {
  if (!field) return;
  const el = document.getElementById(field);
  if (el instanceof HTMLInputElement) el.focus();
}
