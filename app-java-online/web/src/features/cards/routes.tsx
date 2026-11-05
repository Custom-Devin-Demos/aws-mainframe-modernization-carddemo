import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { CardListPage } from './CardListPage';
import { CardUpdatePage } from './CardUpdatePage';
import { CardViewPage } from './CardViewPage';

/** Card list/detail/update (COCRDLIC, COCRDSLC, COCRDUPC). */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COCRDLIC, element: <CardListPage /> },
  { path: PROGRAM_PATHS.COCRDSLC, element: <CardViewPage /> },
  { path: PROGRAM_PATHS.COCRDUPC, element: <CardUpdatePage /> },
];
