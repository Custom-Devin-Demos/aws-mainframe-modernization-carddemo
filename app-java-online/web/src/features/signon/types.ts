import type { SessionUser } from '../../api/types';

/** Mirrors com.carddemo.online.signon.SignonResult. */
export interface SignonResult {
  token: string;
  user: SessionUser;
}

/** Mirrors com.carddemo.online.signon.MenuOption (COMEN02Y / COADM02Y entry). */
export interface MenuOption {
  number: number;
  name: string;
  program: string;
  userType?: 'A' | 'U' | null;
}

export interface MenuScreen {
  options: MenuOption[];
}
