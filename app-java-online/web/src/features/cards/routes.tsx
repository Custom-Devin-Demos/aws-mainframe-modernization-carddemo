import type { RouteObject } from 'react-router-dom';
import { NotMigrated } from '../../components/NotMigrated';
import { PROGRAM_PATHS } from '../../programs';

/** Card list/detail/update (COCRDLIC, COCRDSLC, COCRDUPC). Replace each placeholder element with the real page. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COCRDLIC, element: <NotMigrated program="COCRDLIC" /> },
  { path: PROGRAM_PATHS.COCRDSLC, element: <NotMigrated program="COCRDSLC" /> },
  { path: PROGRAM_PATHS.COCRDUPC, element: <NotMigrated program="COCRDUPC" /> },
];
