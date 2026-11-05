/** COBOL program -> page path. ScreenResponse.nextProgram values are routed through this table. */
export const PROGRAM_PATHS = {
  COSGN00C: '/signon',
  COMEN01C: '/menu',
  COADM01C: '/admin',
  COACTVWC: '/accounts/view',
  COACTUPC: '/accounts/update',
  COCRDLIC: '/cards',
  COCRDSLC: '/cards/view',
  COCRDUPC: '/cards/update',
  COTRN00C: '/transactions',
  COTRN01C: '/transactions/view',
  COTRN02C: '/transactions/add',
  COBIL00C: '/billpay',
  CORPT00C: '/reports',
  COUSR00C: '/users',
  COUSR01C: '/users/add',
  COUSR02C: '/users/update',
  COUSR03C: '/users/delete',
} as const;

export type ProgramName = keyof typeof PROGRAM_PATHS;

export function programPath(program: string): string {
  return (PROGRAM_PATHS as Record<string, string>)[program] ?? '/signon';
}
