/** Mirrors com.carddemo.online.common.ScreenResponse. */
export type MessageType = 'INFO' | 'SUCCESS' | 'ERROR';

export interface ScreenResponse<T> {
  data?: T;
  message?: string;
  messageType?: MessageType;
  /** Input field the COBOL program put the cursor on (MOVE -1 TO xxxL). */
  field?: string;
  /** Program the CICS code would XCTL to; route with programPath(). */
  nextProgram?: string;
}

/** Mirrors com.carddemo.online.common.KeysetPage (browse screens, PF7/PF8). */
export interface KeysetPage<T> {
  items: T[];
  pageNumber: number;
  firstKey?: string;
  lastKey?: string;
  hasPrevPage: boolean;
  hasNextPage: boolean;
}

export interface SessionUser {
  userId: string;
  userType: 'A' | 'U';
}
