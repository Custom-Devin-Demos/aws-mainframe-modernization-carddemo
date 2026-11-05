import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { TransactionAddPage } from './TransactionAddPage';
import { TransactionListPage } from './TransactionListPage';
import { TransactionViewPage } from './TransactionViewPage';

/** Transaction list/view/add (COTRN00C, COTRN01C, COTRN02C). */
export const routes: RouteObject[] = [
  { path: PROGRAM_PATHS.COTRN00C, element: <TransactionListPage /> },
  { path: PROGRAM_PATHS.COTRN01C, element: <TransactionViewPage /> },
  { path: PROGRAM_PATHS.COTRN02C, element: <TransactionAddPage /> },
];
