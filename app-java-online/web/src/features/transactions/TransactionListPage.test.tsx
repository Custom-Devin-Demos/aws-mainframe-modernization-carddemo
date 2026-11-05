import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { renderTransactions } from './testRoutes';

const row = (n: number) => ({
  tranId: String(n).padStart(16, '0'),
  date: '06/10/22',
  description: `Purchase ${n}`,
  amount: '+00000050.47',
});
const page = (from: number, count: number, pageNumber: number, hasNextPage = true) => {
  const items = Array.from({ length: count }, (_, i) => row(from + i));
  return {
    data: {
      items,
      pageNumber,
      firstKey: items[0]?.tranId,
      lastKey: items[items.length - 1]?.tranId,
      hasPrevPage: pageNumber > 1,
      hasNextPage,
    },
    field: 'startTranId',
  };
};

describe('TransactionListPage (COTRN00C)', () => {
  it('lists the first page on entry', async () => {
    const calls = mockFetch([200, page(1, 10, 1)]);
    renderTransactions('/transactions');
    expect(await screen.findByText('0000000000000001')).toBeInTheDocument();
    expect(screen.getByText('Purchase 10')).toBeInTheDocument();
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
    expect(screen.getByText('Tran: CT00')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/transactions');
    expect(screen.getByLabelText('Search Tran ID:')).toHaveFocus();
  });

  it('sends the search Tran ID and shows the server validation message', async () => {
    const calls = mockFetch([200, page(1, 10, 1)], [400, { message: 'Tran ID must be Numeric ...', field: 'startTranId' }]);
    renderTransactions('/transactions');
    await screen.findByText('Purchase 1');
    await userEvent.type(screen.getByLabelText('Search Tran ID:'), '12AB{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('Tran ID must be Numeric ...');
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(calls[1].url).toBe('/api/transactions?startTranId=12AB');
    expect(screen.getByLabelText('Search Tran ID:')).toHaveFocus();
    expect(screen.getByText('Purchase 1')).toBeInTheDocument();
  });

  it("selecting a row with 'S' transfers to COTRN01C with the Tran ID", async () => {
    const calls = mockFetch([200, page(1, 10, 1)], [200, { nextProgram: 'COTRN01C' }], [200, {}]);
    renderTransactions('/transactions');
    await screen.findByText('Purchase 3');
    await userEvent.type(screen.getByLabelText('Select row 3'), 'S{Enter}');
    await waitFor(() => expect(calls).toHaveLength(3));
    expect(calls[1].url).toBe('/api/transactions?startTranId=&selection=S&selectedTranId=0000000000000003');
    expect(calls[2].url).toBe('/api/transactions/0000000000000003');
    expect(screen.getByText('Tran: CT01')).toBeInTheDocument();
    expect(screen.getByLabelText('Enter Tran ID:')).toHaveValue('0000000000000003');
  });

  it('shows the invalid selection message from the server', async () => {
    mockFetch([200, page(1, 10, 1)], [200, { ...page(1, 10, 1), message: 'Invalid selection. Valid value is S', messageType: 'INFO' }]);
    renderTransactions('/transactions');
    await screen.findByText('Purchase 1');
    await userEvent.type(screen.getByLabelText('Select row 1'), 'X{Enter}');
    expect(await screen.findByText('Invalid selection. Valid value is S')).toBeInTheDocument();
    expect(screen.getByLabelText('Select row 1')).toHaveValue('');
  });

  it('PF8 and PF7 page with the last/first key and current page number', async () => {
    const calls = mockFetch(
      [200, page(1, 10, 1)],
      [200, page(11, 10, 2)],
      [200, { ...page(1, 10, 1), message: 'You have reached the top of the page...', messageType: 'INFO' }],
    );
    renderTransactions('/transactions');
    await screen.findByText('Purchase 1');
    await userEvent.keyboard('{F8}');
    expect(await screen.findByText('Purchase 20')).toBeInTheDocument();
    expect(calls[1].url).toBe('/api/transactions?after=0000000000000010&page=1');
    expect(screen.getByText('Page: 2')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'F7=Backward' }));
    expect(await screen.findByText('You have reached the top of the page...')).toBeInTheDocument();
    expect(calls[2].url).toBe('/api/transactions?before=0000000000000011&page=2');
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
  });

  it('keeps the current rows when the server re-sends the map without data', async () => {
    mockFetch([200, page(291, 10, 30, false)], [200, { message: 'You are already at the bottom of the page...', messageType: 'INFO' }]);
    renderTransactions('/transactions');
    await screen.findByText('Purchase 300');
    await userEvent.keyboard('{F8}');
    expect(await screen.findByText('You are already at the bottom of the page...')).toBeInTheDocument();
    expect(screen.getByText('Purchase 300')).toBeInTheDocument();
    expect(screen.getByText('Page: 30')).toBeInTheDocument();
  });

  it('PF3 returns to the main menu', async () => {
    mockFetch([200, page(1, 10, 1)]);
    renderTransactions('/transactions');
    await screen.findByText('Purchase 1');
    await userEvent.keyboard('{F3}');
    expect(screen.getByTestId('landed')).toHaveTextContent('/menu');
  });
});
