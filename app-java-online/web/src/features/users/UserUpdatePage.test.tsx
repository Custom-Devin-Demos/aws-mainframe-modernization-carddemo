import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { UserUpdatePage } from './UserUpdatePage';
import { body, renderPage } from './testRoutes';

const USER = { userId: 'USER0001', firstName: 'LAWRENCE', lastName: 'THOMAS', userType: 'U' };
const FETCHED = [200, { data: USER, message: 'Press PF5 key to save your updates ...', messageType: 'INFO', field: 'userId' }] as [number, unknown];

describe('UserUpdatePage (COUSR02C)', () => {
  it('fetches the user selected on the list and leaves the password blank', async () => {
    const calls = mockFetch(FETCHED);
    renderPage('/users/update', <UserUpdatePage />, '/users/update?userId=USER0001&from=COUSR00C');
    await waitFor(() => expect(screen.getByLabelText('First Name:')).toHaveValue('LAWRENCE'));
    expect(screen.getByText('Prog: COUSR02C')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/users/USER0001?mode=update');
    expect(screen.getByLabelText('Password:')).toHaveValue('');
    expect(screen.getByRole('status')).toHaveTextContent('Press PF5 key to save your updates ...');
  });

  it('fetches with ENTER and shows not found on the user id', async () => {
    mockFetch([404, { message: 'User ID NOT found...', messageType: 'ERROR', field: 'userId' }]);
    renderPage('/users/update', <UserUpdatePage />);
    await userEvent.type(screen.getByLabelText('Enter User ID:'), 'NOBODY{Enter}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User ID NOT found...'));
    expect(screen.getByLabelText('Enter User ID:')).toHaveFocus();
  });

  it('saves with F5 sending the re-entered password', async () => {
    const calls = mockFetch(FETCHED, [200, { data: USER, message: 'User USER0001 has been updated ...', messageType: 'SUCCESS' }]);
    renderPage('/users/update', <UserUpdatePage />, '/users/update?userId=USER0001');
    await waitFor(() => expect(screen.getByLabelText('First Name:')).toHaveValue('LAWRENCE'));
    await userEvent.clear(screen.getByLabelText('Last Name:'));
    await userEvent.type(screen.getByLabelText('Last Name:'), 'TOMAS');
    await userEvent.type(screen.getByLabelText('Password:'), 'PASSWORD');
    await userEvent.keyboard('{F5}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User USER0001 has been updated ...'));
    expect(calls[1].url).toBe('/api/users/USER0001');
    expect(calls[1].init?.method).toBe('PUT');
    expect(body(calls[1].init)).toEqual({ firstName: 'LAWRENCE', lastName: 'TOMAS', password: 'PASSWORD', userType: 'U' });
    expect(screen.getByLabelText('Last Name:')).toHaveValue('TOMAS');
  });

  it('shows the no-change and validation messages with field focus', async () => {
    mockFetch(
      FETCHED,
      [400, { message: 'Please modify to update ...', messageType: 'ERROR', field: 'firstName' }],
      [400, { message: 'Password can NOT be empty...', messageType: 'ERROR', field: 'password' }],
    );
    renderPage('/users/update', <UserUpdatePage />, '/users/update?userId=USER0001');
    await waitFor(() => expect(screen.getByLabelText('First Name:')).toHaveValue('LAWRENCE'));
    await userEvent.click(screen.getByRole('button', { name: 'F5=Save' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Please modify to update ...'));
    expect(screen.getByLabelText('First Name:')).toHaveFocus();
    await userEvent.keyboard('{F5}');
    await waitFor(() => expect(screen.getByLabelText('Password:')).toHaveFocus());
  });

  it('F3 saves and returns to the calling list; F12 cancels to the admin menu', async () => {
    const calls = mockFetch(FETCHED, [200, { data: USER, message: 'User USER0001 has been updated ...' }]);
    renderPage('/users/update', <UserUpdatePage />, '/users/update?userId=USER0001&from=COUSR00C');
    await waitFor(() => expect(screen.getByLabelText('First Name:')).toHaveValue('LAWRENCE'));
    await userEvent.keyboard('{F3}');
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/users$/);
    expect(calls[1].init?.method).toBe('PUT');
  });

  it('F12 cancels to the admin menu without saving and F4 clears', async () => {
    const calls = mockFetch(FETCHED);
    renderPage('/users/update', <UserUpdatePage />, '/users/update?userId=USER0001');
    await waitFor(() => expect(screen.getByLabelText('First Name:')).toHaveValue('LAWRENCE'));
    await userEvent.keyboard('{F4}');
    expect(screen.getByLabelText('Enter User ID:')).toHaveValue('');
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
    await userEvent.keyboard('{F12}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/admin');
    expect(calls).toHaveLength(1);
  });
});
