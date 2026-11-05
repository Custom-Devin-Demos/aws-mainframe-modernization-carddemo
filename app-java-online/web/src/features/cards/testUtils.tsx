import { Route, Routes, useLocation } from 'react-router-dom';
import type { ReactElement } from 'react';
import type { SessionUser } from '../../api/types';
import { renderWithSession } from '../../test/renderWithSession';

export const USER: SessionUser = { userId: 'USER0001', userType: 'U' };

function Where() {
  const loc = useLocation();
  return <div data-testid="location">{loc.pathname + loc.search}</div>;
}

/** Renders `page` at `path` with a probe on every other route showing where navigation went. */
export function renderPage(page: ReactElement, path: string, route: string) {
  return renderWithSession(
    <Routes>
      <Route path={path} element={page} />
      <Route path="*" element={<Where />} />
    </Routes>,
    { user: USER, route },
  );
}
