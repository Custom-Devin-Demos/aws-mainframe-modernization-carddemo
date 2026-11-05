import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { ReportsPage } from './ReportsPage';

/** Transaction report request (CORPT00C). */
export const routes: RouteObject[] = [{ path: PROGRAM_PATHS.CORPT00C, element: <ReportsPage /> }];
