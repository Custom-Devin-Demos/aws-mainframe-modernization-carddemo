import type { ReactElement } from 'react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { renderWithSession } from '../../test/renderWithSession';
import type { SessionUser } from '../../api/types';

export const ADMIN: SessionUser = { userId: 'ADMIN001', userType: 'A' };

function Where() {
  const loc = useLocation();
  return <div data-testid="location">{loc.pathname + loc.search}</div>;
}

/** Renders `page` at `path` (with `route` as the initial URL) and a probe showing where navigation went. */
export function renderPage(path: string, page: ReactElement, route: string = path) {
  return renderWithSession(
    <Routes>
      <Route path={path} element={page} />
      <Route path="*" element={<Where />} />
    </Routes>,
    { user: ADMIN, route },
  );
}

export function body(init?: RequestInit): unknown {
  return init?.body ? JSON.parse(String(init.body)) : undefined;
}
