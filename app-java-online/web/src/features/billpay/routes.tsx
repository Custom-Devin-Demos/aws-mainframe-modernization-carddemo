import type { RouteObject } from 'react-router-dom';
import { PROGRAM_PATHS } from '../../programs';
import { BillPayPage } from './BillPayPage';

/** Bill payment (COBIL00C). */
export const routes: RouteObject[] = [{ path: PROGRAM_PATHS.COBIL00C, element: <BillPayPage /> }];
