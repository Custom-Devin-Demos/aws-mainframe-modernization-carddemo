import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { UserDeletePage } from './UserDeletePage';
import { renderPage } from './testRoutes';

const USER = { userId: 'USER0005', firstName: 'SNEHA', lastName: 'GUPTA', userType: 'U' };
const FETCHED = [200, { data: USER, message: 'Press PF5 key to delete this user ...', messageType: 'INFO', field: 'userId' }] as [number, unknown];

describe('UserDeletePage (COUSR03C)', () => {
  it('fetches the selected user and deletes it with F5', async () => {
    const calls = mockFetch(FETCHED, [200, { message: 'User USER0005 has been deleted ...', messageType: 'SUCCESS', field: 'userId' }]);
    renderPage('/users/delete', <UserDeletePage />, '/users/delete?userId=USER0005&from=COUSR00C');
    await waitFor(() => expect(screen.getByTestId('firstName')).toHaveTextContent('SNEHA'));
    expect(screen.getByText('Tran: CU03')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/users/USER0005?mode=delete');
    expect(screen.getByRole('status')).toHaveTextContent('Press PF5 key to delete this user ...');
    await userEvent.keyboard('{F5}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User USER0005 has been deleted ...'));
    expect(calls[1].url).toBe('/api/users/USER0005');
    expect(calls[1].init?.method).toBe('DELETE');
    expect(screen.getByTestId('firstName')).toHaveTextContent('');
    expect(screen.getByLabelText('Enter User ID:')).toHaveValue('');
  });

  it('shows the empty user id error from ENTER', async () => {
    const calls = mockFetch([400, { message: 'User ID can NOT be empty...', messageType: 'ERROR', field: 'userId' }]);
    renderPage('/users/delete', <UserDeletePage />);
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Fetch' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User ID can NOT be empty...'));
    expect(calls[0].url).toBe('/api/users/%20?mode=delete');
    expect(screen.getByLabelText('Enter User ID:')).toHaveFocus();
  });

  it('keeps the screen on a failed delete', async () => {
    mockFetch(FETCHED, [404, { message: 'User ID NOT found...', messageType: 'ERROR', field: 'userId' }]);
    renderPage('/users/delete', <UserDeletePage />, '/users/delete?userId=USER0005');
    await waitFor(() => expect(screen.getByTestId('firstName')).toHaveTextContent('SNEHA'));
    await userEvent.click(screen.getByRole('button', { name: 'F5=Delete' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('User ID NOT found...'));
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText('Enter User ID:')).toHaveValue('USER0005');
  });

  it('F3 returns to the calling program, F4 clears, F12 goes to the admin menu', async () => {
    mockFetch(FETCHED);
    renderPage('/users/delete', <UserDeletePage />, '/users/delete?userId=USER0005&from=COUSR00C');
    await waitFor(() => expect(screen.getByTestId('firstName')).toHaveTextContent('SNEHA'));
    await userEvent.keyboard('{F4}');
    expect(screen.getByTestId('firstName')).toHaveTextContent('');
    await userEvent.keyboard('{F3}');
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/users$/);
  });

  it('F12 goes to the admin menu', async () => {
    mockFetch();
    renderPage('/users/delete', <UserDeletePage />);
    await userEvent.keyboard('{F12}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/admin');
  });
});
