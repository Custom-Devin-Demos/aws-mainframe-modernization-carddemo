import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { AccountUpdatePage } from './AccountUpdatePage';
import { AccountViewPage } from './AccountViewPage';

/** Account view/update (COACTVWC, COACTUPC). */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COACTVWC, element: <AccountViewPage /> },
  { path: PROGRAM_PATHS.COACTUPC, element: <AccountUpdatePage /> },
];
