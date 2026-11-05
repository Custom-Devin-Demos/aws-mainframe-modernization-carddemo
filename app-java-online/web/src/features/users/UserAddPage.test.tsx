import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { UserAddPage } from './UserAddPage';
import { body, renderPage } from './testRoutes';

async function fill(values: Record<string, string>) {
  for (const [label, v] of Object.entries(values)) await userEvent.type(screen.getByLabelText(label), v);
}

describe('UserAddPage (COUSR01C)', () => {
  it('adds the user and clears the form', async () => {
    const calls = mockFetch([201, { data: { userId: 'NEWUSER1' }, message: 'User NEWUSER1 has been added ...', messageType: 'SUCCESS', field: 'firstName' }]);
    renderPage('/users/add', <UserAddPage />);
    expect(screen.getByText('Tran: CU01')).toBeInTheDocument();
    await fill({ 'First Name:': 'JANE', 'Last Name:': 'DOE', 'User ID:': 'NEWUSER1', 'Password:': 'SECRET', 'User Type:': 'U' });
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User NEWUSER1 has been added ...'));
    expect(calls[0].url).toBe('/api/users');
    expect(calls[0].init?.method).toBe('POST');
    expect(body(calls[0].init)).toEqual({ firstName: 'JANE', lastName: 'DOE', userId: 'NEWUSER1', password: 'SECRET', userType: 'U' });
    expect(screen.getByLabelText('User ID:')).toHaveValue('');
    expect(screen.getByLabelText('First Name:')).toHaveFocus();
  });

  it('shows the server validation error, keeps the input and focuses the field', async () => {
    mockFetch([409, { message: 'User ID already exist...', messageType: 'ERROR', field: 'userId' }]);
    renderPage('/users/add', <UserAddPage />);
    await fill({ 'First Name:': 'JANE', 'Last Name:': 'DOE', 'User ID:': 'ADMIN001', 'Password:': 'X', 'User Type:': 'A' });
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Add User' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User ID already exist...'));
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText('User ID:')).toHaveValue('ADMIN001');
    expect(screen.getByLabelText('User ID:')).toHaveFocus();
  });

  it('focuses the password field for an empty password', async () => {
    mockFetch([400, { message: 'Password can NOT be empty...', messageType: 'ERROR', field: 'password' }]);
    renderPage('/users/add', <UserAddPage />);
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByLabelText('Password:')).toHaveFocus());
  });

  it('F4 clears, F12 is an invalid key and F3 goes back to the admin menu', async () => {
    mockFetch();
    renderPage('/users/add', <UserAddPage />);
    await fill({ 'First Name:': 'JANE' });
    await userEvent.keyboard('{F4}');
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
    await userEvent.keyboard('{F12}');
    expect(screen.getByRole('status')).toHaveTextContent('Invalid key pressed. Please see below...');
    await userEvent.keyboard('{F3}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/admin');
  });

  it('limits inputs to the map field lengths', () => {
    mockFetch();
    renderPage('/users/add', <UserAddPage />);
    expect(screen.getByLabelText('User ID:')).toHaveAttribute('maxLength', '8');
    expect(screen.getByLabelText('Password:')).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('User Type:')).toHaveAttribute('maxLength', '1');
  });
});
