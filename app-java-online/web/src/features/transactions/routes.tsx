import type { RouteObject } from 'react-router-dom';
import { NotMigrated } from '../../components/NotMigrated';
import { PROGRAM_PATHS } from '../../programs';

/** Transaction list/view/add (COTRN00C, COTRN01C, COTRN02C). Replace each placeholder element with the real page. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COTRN00C, element: <NotMigrated program="COTRN00C" /> },
  { path: PROGRAM_PATHS.COTRN01C, element: <NotMigrated program="COTRN01C" /> },
  { path: PROGRAM_PATHS.COTRN02C, element: <NotMigrated program="COTRN02C" /> },
];
