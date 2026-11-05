import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from './App';
import { SessionProvider } from './session/SessionContext';
import { saveSession } from './session/sessionStore';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SessionProvider>
        <AppRoutes />
      </SessionProvider>
    </MemoryRouter>,
  );
}

describe('routing', () => {
  it('sends users without a session to sign-on', () => {
    renderAt('/accounts/view');
    expect(screen.getByText('Prog: COSGN00C')).toBeInTheDocument();
  });

  it('keeps regular users out of admin pages', () => {
    saveSession('t', { userId: 'USER0001', userType: 'U' });
    renderAt('/users');
    expect(screen.getByText('Prog: COMEN01C')).toBeInTheDocument();
  });
});
