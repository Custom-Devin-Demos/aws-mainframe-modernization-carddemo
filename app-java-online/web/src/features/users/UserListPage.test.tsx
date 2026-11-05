import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { UserListPage } from './UserListPage';
import { renderPage } from './testRoutes';

const users = (ids: string[]) => ids.map((userId) => ({ userId, firstName: `F${userId}`, lastName: `L${userId}`, userType: 'U' }));
const page = (ids: string[], pageNumber: number, hasNextPage: boolean) => ({
  items: users(ids),
  pageNumber,
  firstKey: ids[0],
  lastKey: ids[ids.length - 1],
  hasPrevPage: pageNumber > 1,
  hasNextPage,
});
const PAGE1 = ['A1', 'A2', 'A3', 'A4', 'A5', 'A6', 'A7', 'A8', 'A9', 'B0'];

describe('UserListPage (COUSR00C)', () => {
  it('loads the first page on entry', async () => {
    const calls = mockFetch([200, { data: page(PAGE1, 1, true), field: 'startUserId' }]);
    renderPage('/users', <UserListPage />);
    expect(await screen.findByText('A1')).toBeInTheDocument();
    expect(screen.getByText('Tran: CU00')).toBeInTheDocument();
    expect(screen.getByText('Prog: COUSR00C')).toBeInTheDocument();
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(11);
    expect(calls[0].url).toBe('/api/users');
  });

  it('searches from the entered user id and shows the bottom message', async () => {
    const calls = mockFetch(
      [200, { data: page(PAGE1, 1, true) }],
      [200, { data: page(['USER0003', 'USER0004'], 1, false), message: 'You have reached the bottom of the page...', messageType: 'INFO', field: 'startUserId' }],
    );
    renderPage('/users', <UserListPage />);
    await screen.findByText('A1');
    await userEvent.type(screen.getByLabelText('Search User ID:'), 'USER0003{Enter}');
    expect(await screen.findByText('USER0003')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('You have reached the bottom of the page...');
    expect(calls[1].url).toBe('/api/users?startUserId=USER0003');
    expect(screen.getByLabelText('Search User ID:')).toHaveFocus();
  });

  it('pages forward and backward with F8/F7 using the page keys', async () => {
    const calls = mockFetch(
      [200, { data: page(PAGE1, 1, true) }],
      [200, { data: page(['C1', 'C2'], 2, false), message: 'You have reached the bottom of the page...', messageType: 'INFO' }],
      [200, { message: 'You are already at the bottom of the page...', messageType: 'INFO' }],
      [200, { data: page(PAGE1, 1, true), message: 'You have reached the top of the page...', messageType: 'INFO' }],
    );
    renderPage('/users', <UserListPage />);
    await screen.findByText('A1');
    await userEvent.keyboard('{F8}');
    expect(await screen.findByText('C1')).toBeInTheDocument();
    expect(calls[1].url).toBe('/api/users?after=B0&page=1');
    expect(screen.getByText('Page: 2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'F8=Forward' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('You are already at the bottom of the page...'));
    expect(screen.getByText('C1')).toBeInTheDocument();
    await userEvent.keyboard('{F7}');
    expect(await screen.findByText('A1')).toBeInTheDocument();
    expect(calls[3].url).toBe('/api/users?before=C1&page=2');
    expect(screen.getByRole('status')).toHaveTextContent('You have reached the top of the page...');
  });

  it("transfers to update with the first 'U' selection", async () => {
    const calls = mockFetch([200, { data: page(PAGE1, 1, false) }], [200, { nextProgram: 'COUSR02C' }]);
    renderPage('/users', <UserListPage />);
    await screen.findByText('A1');
    await userEvent.type(screen.getByLabelText('Select A3'), 'u');
    await userEvent.type(screen.getByLabelText('Select A5'), 'D');
    await userEvent.keyboard('{Enter}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/users/update?userId=A3&from=COUSR00C');
    expect(calls[1].url).toBe('/api/users?sel=u&selUserId=A3');
  });

  it("transfers to delete with a 'D' selection", async () => {
    mockFetch([200, { data: page(PAGE1, 1, false) }], [200, { nextProgram: 'COUSR03C' }]);
    renderPage('/users', <UserListPage />);
    await screen.findByText('A1');
    await userEvent.type(screen.getByLabelText('Select A2'), 'D{Enter}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/users/delete?userId=A2&from=COUSR00C');
  });

  it('shows the invalid selection error and focuses the search field', async () => {
    mockFetch(
      [200, { data: page(PAGE1, 1, false) }],
      [400, { data: page(PAGE1, 1, false), message: 'Invalid selection. Valid values are U and D', messageType: 'ERROR', field: 'startUserId' }],
    );
    renderPage('/users', <UserListPage />);
    await screen.findByText('A1');
    await userEvent.type(screen.getByLabelText('Select A1'), 'X{Enter}');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Invalid selection. Valid values are U and D'));
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText('Search User ID:')).toHaveFocus();
    expect(screen.getByLabelText('Select A1')).toHaveValue('');
  });

  it('F3 returns to the admin menu', async () => {
    mockFetch([200, { data: page(PAGE1, 1, false) }]);
    renderPage('/users', <UserListPage />);
    await screen.findByText('A1');
    await userEvent.keyboard('{F3}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/admin');
  });
});
