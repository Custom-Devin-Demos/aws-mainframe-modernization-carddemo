import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { AdminMenuPage, MainMenuPage } from './MenuPage';
import { SignonPage } from './SignonPage';

/** Sign-on (COSGN00C) and menus (COMEN01C, COADM01C). COSGN00C is public (outside RequireSession); see App.tsx. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COSGN00C, element: <SignonPage /> },
  { path: PROGRAM_PATHS.COMEN01C, element: <MainMenuPage /> },
  { path: PROGRAM_PATHS.COADM01C, element: <AdminMenuPage /> },
];
