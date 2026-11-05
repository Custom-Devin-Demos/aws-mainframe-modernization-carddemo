import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { SessionProvider } from '../../session/SessionContext';
import { saveSession } from '../../session/sessionStore';
import { routes } from './routes';

/** Shows where a PF key / XCTL navigated to, with the location state it carried. */
function Landing() {
  const location = useLocation();
  return <div data-testid="landed">{`${location.pathname} ${JSON.stringify(location.state ?? null)}`}</div>;
}

/** Renders the transaction routes at `path` (with optional location state) for a signed-on user. */
export function renderTransactions(path: string, state?: unknown) {
  saveSession('test-token', { userId: 'USER0001', userType: 'U' });
  return render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <SessionProvider>
        <Routes>
          {routes.map((r) => (
            <Route key={r.path} path={r.path} element={r.element} />
          ))}
          <Route path="*" element={<Landing />} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}
