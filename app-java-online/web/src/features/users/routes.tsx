import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { UserAddPage } from './UserAddPage';
import { UserDeletePage } from './UserDeletePage';
import { UserListPage } from './UserListPage';
import { UserUpdatePage } from './UserUpdatePage';

/** User admin (COUSR00C-COUSR03C); admin only. */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COUSR00C, element: <UserListPage /> },
  { path: PROGRAM_PATHS.COUSR01C, element: <UserAddPage /> },
  { path: PROGRAM_PATHS.COUSR02C, element: <UserUpdatePage /> },
  { path: PROGRAM_PATHS.COUSR03C, element: <UserDeletePage /> },
];
