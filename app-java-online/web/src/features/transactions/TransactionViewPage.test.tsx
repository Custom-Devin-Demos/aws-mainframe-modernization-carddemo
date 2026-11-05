import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { renderTransactions } from './testRoutes';

const view = {
  tranId: '0000000000683580',
  cardNum: '4859452612877065',
  typeCd: '01',
  catCd: '0001',
  source: 'POS TERM',
  amount: '+00000504.77',
  description: 'Purchase at Abshire-Lowe',
  origDate: '2022-06-10',
  procDate: '',
  merchantId: '800000000',
  merchantName: 'Abshire-Lowe',
  merchantCity: 'North Enoshaven',
  merchantZip: '72112',
};

describe('TransactionViewPage (COTRN01C)', () => {
  it('fetches the transaction selected on the list screen', async () => {
    const calls = mockFetch([200, { data: view, field: 'tranId' }]);
    renderTransactions('/transactions/view', { tranId: view.tranId, from: 'COTRN00C' });
    expect(await screen.findByText('Purchase at Abshire-Lowe')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/transactions/0000000000683580');
    expect(screen.getByTestId('view-amount')).toHaveTextContent('+00000504.77');
    expect(screen.getByTestId('view-merchantCity')).toHaveTextContent('North Enoshaven');
    expect(screen.getByText('Prog: COTRN01C')).toBeInTheDocument();
  });

  it('rejects an empty Tran ID without calling the API', async () => {
    const calls = mockFetch();
    renderTransactions('/transactions/view');
    await userEvent.type(screen.getByLabelText('Enter Tran ID:'), '{Enter}');
    expect(screen.getByRole('status')).toHaveTextContent('Tran ID can NOT be empty...');
    expect(calls).toHaveLength(0);
    expect(screen.getByLabelText('Enter Tran ID:')).toHaveFocus();
  });

  it('shows NOT found from the server and clears the fields', async () => {
    mockFetch([200, { data: view }], [404, { message: 'Transaction ID NOT found...', field: 'tranId' }]);
    renderTransactions('/transactions/view', { tranId: view.tranId });
    await screen.findByText('Purchase at Abshire-Lowe');
    const input = screen.getByLabelText('Enter Tran ID:');
    await userEvent.clear(input);
    await userEvent.type(input, '0000000000000001{Enter}');
    expect(await screen.findByText('Transaction ID NOT found...')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(screen.queryByText('Purchase at Abshire-Lowe')).not.toBeInTheDocument();
    expect(input).toHaveFocus();
  });

  it('PF4 clears, PF5 browses transactions, PF3 returns to the calling program', async () => {
    mockFetch([200, { data: view }]);
    const { unmount } = renderTransactions('/transactions/view', { tranId: view.tranId, from: 'COADM01C' });
    await screen.findByText('Purchase at Abshire-Lowe');
    await userEvent.keyboard('{F4}');
    expect(screen.queryByText('Purchase at Abshire-Lowe')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Enter Tran ID:')).toHaveValue('');
    await userEvent.keyboard('{F3}');
    expect(screen.getByTestId('landed')).toHaveTextContent('/admin');
    unmount();

    const calls = mockFetch([200, { data: { items: [], pageNumber: 0, hasPrevPage: false, hasNextPage: false } }]);
    renderTransactions('/transactions/view');
    await userEvent.keyboard('{F5}');
    expect(await screen.findByText('Tran: CT00')).toBeInTheDocument();
    await waitFor(() => expect(calls[0].url).toBe('/api/transactions'));
  });

  it('PF3 defaults to the main menu', async () => {
    renderTransactions('/transactions/view');
    await userEvent.keyboard('{F3}');
    expect(screen.getByTestId('landed')).toHaveTextContent('/menu');
  });
});
