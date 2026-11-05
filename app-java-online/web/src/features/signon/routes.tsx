import type { RouteObject } from 'react-router-dom';
import { NotMigrated } from '../../components/NotMigrated';
import { PROGRAM_PATHS } from '../../programs';

/** Sign-on (COSGN00C) and menus (COMEN01C, COADM01C). COSGN00C must be public (outside RequireSession); see App.tsx. Replace each placeholder element with the real page. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COSGN00C, element: <NotMigrated program="COSGN00C" /> },
  { path: PROGRAM_PATHS.COMEN01C, element: <NotMigrated program="COMEN01C" /> },
  { path: PROGRAM_PATHS.COADM01C, element: <NotMigrated program="COADM01C" /> },
];
