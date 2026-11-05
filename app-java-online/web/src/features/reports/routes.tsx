import type { RouteObject } from 'react-router-dom';
import { NotMigrated } from '../../components/NotMigrated';
import { PROGRAM_PATHS } from '../../programs';

/** Transaction report request (CORPT00C). Replace each placeholder element with the real page. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.CORPT00C, element: <NotMigrated program="CORPT00C" /> },
];
