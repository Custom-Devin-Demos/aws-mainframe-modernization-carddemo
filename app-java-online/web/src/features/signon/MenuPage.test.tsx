import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { mockFetch, renderWithSession } from '../../test/renderWithSession';
import { getToken } from '../../session/sessionStore';
import type { SessionUser } from '../../api/types';
import { AdminMenuPage, MainMenuPage } from './MenuPage';

const USER: SessionUser = { userId: 'USER0001', userType: 'U' };
const ADMIN: SessionUser = { userId: 'ADMIN001', userType: 'A' };

const MAIN = {
  options: [
    { number: 1, name: 'Account View', program: 'COACTVWC', userType: 'U' },
    { number: 11, name: 'Pending Authorization View', program: 'COPAUS0C', userType: 'U' },
  ],
};
const ADMIN_MENU = {
  options: [
    { number: 1, name: 'User List (Security)', program: 'COUSR00C' },
    { number: 5, name: 'Transaction Type List/Update (Db2)', program: 'COTRTLIC' },
  ],
};

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function renderAt(route: string, user: SessionUser) {
  return renderWithSession(
    <Routes>
      <Route path="/menu" element={<MainMenuPage />} />
      <Route path="/admin" element={<AdminMenuPage />} />
      <Route path="*" element={<Where />} />
    </Routes>,
    { route, user },
  );
}

describe('MainMenuPage (COMEN01C)', () => {
  it('lists the options from the server', async () => {
    const calls = mockFetch([200, { data: MAIN }]);
    renderAt('/menu', USER);
    expect(await screen.findByText('1. Account View')).toBeInTheDocument();
    expect(screen.getByText('11. Pending Authorization View')).toBeInTheDocument();
    expect(screen.getByText('Tran: CM00')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Main Menu' })).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/menu');
    expect(screen.getByLabelText(/Please select an option/)).toHaveAttribute('maxLength', '2');
  });

  it('routes to the selected program', async () => {
    const calls = mockFetch([200, { data: MAIN }], [200, { data: MAIN, nextProgram: 'COACTVWC' }]);
    renderAt('/menu', USER);
    await screen.findByText('1. Account View');
    await userEvent.type(screen.getByLabelText(/Please select an option/), '1{Enter}');
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/accounts/view'));
    expect(calls[1].url).toBe('/api/menu/select');
    expect(JSON.parse(String(calls[1].init?.body))).toEqual({ option: '1' });
  });

  it('shows validation errors with focus on the option field', async () => {
    mockFetch(
      [200, { data: MAIN }],
      [400, { data: MAIN, message: 'Please enter a valid option number...', messageType: 'ERROR', field: 'option' }],
    );
    renderAt('/menu', USER);
    await screen.findByText('1. Account View');
    await userEvent.type(screen.getByLabelText(/Please select an option/), '99');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Continue' }));
    expect(await screen.findByText('Please enter a valid option number...')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText(/Please select an option/)).toHaveFocus();
    expect(screen.getByText('1. Account View')).toBeInTheDocument();
  });

  it('shows the not-installed message and stays on the menu', async () => {
    mockFetch(
      [200, { data: MAIN }],
      [200, { data: MAIN, message: 'This option Pending Authorization View is not installed...', messageType: 'ERROR', field: 'option' }],
    );
    renderAt('/menu', USER);
    await screen.findByText('1. Account View');
    await userEvent.type(screen.getByLabelText(/Please select an option/), '11{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('This option Pending Authorization View is not installed...');
    expect(screen.queryByTestId('where')).not.toBeInTheDocument();
  });

  it('PF3 signs off and returns to sign-on', async () => {
    mockFetch([200, { data: MAIN }]);
    renderAt('/menu', USER);
    await screen.findByText('1. Account View');
    await userEvent.keyboard('{F3}');
    expect(screen.getByTestId('where')).toHaveTextContent('/signon');
    expect(getToken()).toBeNull();
  });

  it('goes back to sign-on when the session has expired', async () => {
    mockFetch([401, { message: 'Please sign on ...', messageType: 'ERROR', nextProgram: 'COSGN00C' }]);
    renderAt('/menu', USER);
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/signon'));
  });
});

describe('AdminMenuPage (COADM01C)', () => {
  it('lists the admin options and routes to user list', async () => {
    const calls = mockFetch([200, { data: ADMIN_MENU }], [200, { data: ADMIN_MENU, nextProgram: 'COUSR00C' }]);
    renderAt('/admin', ADMIN);
    expect(await screen.findByText('1. User List (Security)')).toBeInTheDocument();
    expect(screen.getByText('Tran: CA00')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Admin Menu' })).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/admin/menu');
    await userEvent.type(screen.getByLabelText(/Please select an option/), '1{Enter}');
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/users'));
    expect(calls[1].url).toBe('/api/admin/menu/select');
  });

  it('shows the not-installed message for Db2 options', async () => {
    mockFetch(
      [200, { data: ADMIN_MENU }],
      [200, { data: ADMIN_MENU, message: 'This option is not installed ...', messageType: 'INFO', field: 'option' }],
    );
    renderAt('/admin', ADMIN);
    await screen.findByText('1. User List (Security)');
    await userEvent.type(screen.getByLabelText(/Please select an option/), '5{Enter}');
    expect(await screen.findByText('This option is not installed ...')).toHaveClass('screen-message-info');
  });
});
