import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { mockFetch, renderWithSession } from '../../test/renderWithSession';
import { getToken, getUser } from '../../session/sessionStore';
import { SignonPage } from './SignonPage';

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function renderSignon() {
  return renderWithSession(
    <Routes>
      <Route path="/signon" element={<SignonPage />} />
      <Route path="*" element={<Where />} />
    </Routes>,
    { route: '/signon' },
  );
}

describe('SignonPage (COSGN00C)', () => {
  it('renders the COSGN00 map with the cursor on User ID', () => {
    renderSignon();
    expect(screen.getByText('Tran: CC00')).toBeInTheDocument();
    expect(screen.getByText('Prog: COSGN00C')).toBeInTheDocument();
    expect(screen.getByLabelText(/User ID/)).toHaveFocus();
    expect(screen.getByLabelText(/User ID/)).toHaveAttribute('maxLength', '8');
    expect(screen.getByLabelText(/Password/)).toHaveAttribute('type', 'password');
  });

  it('signs on, stores the session and routes to nextProgram', async () => {
    const calls = mockFetch([
      200,
      { data: { token: 'tok', user: { userId: 'ADMIN001', userType: 'A' } }, nextProgram: 'COADM01C' },
    ]);
    renderSignon();
    await userEvent.type(screen.getByLabelText(/User ID/), 'admin001');
    await userEvent.type(screen.getByLabelText(/Password/), 'password{Enter}');
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/admin'));
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('/api/auth/signon');
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ userId: 'admin001', password: 'password' });
    expect(getToken()).toBe('tok');
    expect(getUser()).toEqual({ userId: 'ADMIN001', userType: 'A' });
  });

  it('routes regular users to the main menu', async () => {
    mockFetch([200, { data: { token: 't', user: { userId: 'USER0001', userType: 'U' } }, nextProgram: 'COMEN01C' }]);
    renderSignon();
    await userEvent.type(screen.getByLabelText(/User ID/), 'USER0001');
    await userEvent.type(screen.getByLabelText(/Password/), 'PASSWORD');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Sign-on' }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/menu'));
  });

  it('shows the server message and moves the cursor to the password field', async () => {
    mockFetch([401, { message: 'Wrong Password. Try again ...', messageType: 'ERROR', field: 'password' }]);
    renderSignon();
    await userEvent.type(screen.getByLabelText(/User ID/), 'USER0001');
    await userEvent.type(screen.getByLabelText(/Password/), 'x{Enter}');
    expect(await screen.findByText('Wrong Password. Try again ...')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText(/Password/)).toHaveFocus();
    expect(getToken()).toBeNull();
  });

  it('moves the cursor back to User ID for user errors', async () => {
    mockFetch([404, { message: 'User not found. Try again ...', messageType: 'ERROR', field: 'userId' }]);
    renderSignon();
    await userEvent.type(screen.getByLabelText(/User ID/), 'NOBODY');
    await userEvent.type(screen.getByLabelText(/Password/), 'x');
    await userEvent.keyboard('{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('User not found. Try again ...');
    expect(screen.getByLabelText(/User ID/)).toHaveFocus();
  });

  it('sends blank input to the server, which owns the validation', async () => {
    const calls = mockFetch([400, { message: 'Please enter User ID ...', messageType: 'ERROR', field: 'userId' }]);
    renderSignon();
    await userEvent.keyboard('{Enter}');
    expect(await screen.findByText('Please enter User ID ...')).toBeInTheDocument();
    expect(calls).toHaveLength(1);
  });

  it('PF3 signs off with the thank-you text', async () => {
    const calls = mockFetch([200, { message: 'Thank you for using CardDemo application...', messageType: 'INFO' }]);
    renderSignon();
    await userEvent.keyboard('{F3}');
    expect(await screen.findByText('Thank you for using CardDemo application...')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/auth/signoff');
    await userEvent.click(screen.getByRole('button', { name: 'Sign on again' }));
    expect(screen.getByText('Prog: COSGN00C')).toBeInTheDocument();
  });
});
