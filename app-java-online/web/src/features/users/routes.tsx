import type { RouteObject } from 'react-router-dom';
import { NotMigrated } from '../../components/NotMigrated';
import { PROGRAM_PATHS } from '../../programs';

/** User admin (COUSR00C-COUSR03C); admin only. Replace each placeholder element with the real page. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COUSR00C, element: <NotMigrated program="COUSR00C" /> },
  { path: PROGRAM_PATHS.COUSR01C, element: <NotMigrated program="COUSR01C" /> },
  { path: PROGRAM_PATHS.COUSR02C, element: <NotMigrated program="COUSR02C" /> },
  { path: PROGRAM_PATHS.COUSR03C, element: <NotMigrated program="COUSR03C" /> },
];
