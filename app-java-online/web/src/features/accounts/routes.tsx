import type { RouteObject } from 'react-router-dom';
import { NotMigrated } from '../../components/NotMigrated';
import { PROGRAM_PATHS } from '../../programs';

/** Account view/update (COACTVWC, COACTUPC). Replace each placeholder element with the real page. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COACTVWC, element: <NotMigrated program="COACTVWC" /> },
  { path: PROGRAM_PATHS.COACTUPC, element: <NotMigrated program="COACTUPC" /> },
];
