import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactElement } from 'react';
import type { SessionUser } from '../api/types';
import { SessionProvider } from '../session/SessionContext';
import { saveSession } from '../session/sessionStore';

/** Renders a page inside a router and session, optionally signed on as `user`. */
export function renderWithSession(ui: ReactElement, opts: { user?: SessionUser; route?: string } = {}) {
  if (opts.user) saveSession('test-token', opts.user);
  return render(
    <MemoryRouter initialEntries={[opts.route ?? '/']}>
      <SessionProvider>{ui}</SessionProvider>
    </MemoryRouter>,
  );
}

/** Stubs global fetch with a queue of [status, body] responses and records the calls. */
export function mockFetch(...responses: Array<[number, unknown]>) {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const queue = [...responses];
  globalThis.fetch = (async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    const [status, body] = queue.shift() ?? [200, {}];
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  }) as typeof fetch;
  return calls;
}
