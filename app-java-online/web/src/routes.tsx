import type { RouteObject } from 'react-router-dom';
import { routes as signon } from './features/signon/routes';
import { routes as accounts } from './features/accounts/routes';
import { routes as cards } from './features/cards/routes';
import { routes as transactions } from './features/transactions/routes';
import { routes as billpay } from './features/billpay/routes';
import { routes as reports } from './features/reports/routes';
import { routes as users } from './features/users/routes';
import { PROGRAM_PATHS } from './programs';

/** Pages reachable without a session (only the sign-on screen). */
export const PUBLIC_PATHS: string[] = [PROGRAM_PATHS.COSGN00C];

/** Pages restricted to admin users (COADM01C and the user-admin programs). */
export const ADMIN_PATHS: string[] = [
  PROGRAM_PATHS.COADM01C,
  PROGRAM_PATHS.COUSR00C,
  PROGRAM_PATHS.COUSR01C,
  PROGRAM_PATHS.COUSR02C,
  PROGRAM_PATHS.COUSR03C,
];

export const featureRoutes: RouteObject[] = [
  ...signon,
  ...accounts,
  ...cards,
  ...transactions,
  ...billpay,
  ...reports,
  ...users,
];
