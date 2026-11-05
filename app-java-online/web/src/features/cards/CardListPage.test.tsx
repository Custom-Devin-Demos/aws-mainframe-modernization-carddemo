import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockFetch } from '../../test/renderWithSession';
import { CardListPage } from './CardListPage';
import { renderPage } from './testUtils';

const rows = Array.from({ length: 7 }, (_, i) => ({
  acctId: `0000000000${i}`,
  cardNum: `100000000000000${i}`,
  activeStatus: 'Y',
}));
const page1 = {
  data: {
    page: { items: rows, pageNumber: 1, firstKey: rows[0].cardNum, lastKey: rows[6].cardNum, hasPrevPage: false, hasNextPage: true },
    infoMessage: 'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD',
    errorRows: [],
  },
  message: 'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD',
  messageType: 'INFO',
};

function render() {
  return renderPage(<CardListPage />, '/cards', '/cards');
}

describe('CardListPage (COCRDLIC)', () => {
  it('loads the first page with the action prompt', async () => {
    const calls = mockFetch([200, page1]);
    render();
    expect(await screen.findByText('1000000000000006')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/cards');
    expect(screen.getByText('TYPE S FOR DETAIL, U TO UPDATE ANY RECORD')).toBeInTheDocument();
    expect(screen.getByText('Page 1')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('');
  });

  it('shows a filter error and puts the cursor on the flagged field', async () => {
    const calls = mockFetch([200, page1], [
      400,
      {
        message: 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER',
        messageType: 'ERROR',
        field: 'acctId',
        data: { page: { items: [], pageNumber: 1, hasPrevPage: false, hasNextPage: false }, errorRows: [] },
      },
    ]);
    render();
    await screen.findByText('1000000000000006');
    await userEvent.type(screen.getByLabelText(/Account Number/), '12AB{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER');
    expect(calls[1].url).toContain('acctId=12AB');
    expect(screen.getByLabelText(/Account Number/)).toHaveFocus();
    expect(screen.queryByText('1000000000000006')).not.toBeInTheDocument();
  });

  it('routes an S selection to the card detail screen', async () => {
    const calls = mockFetch([200, page1], [
      200,
      { data: { ...page1.data, selectedAcctId: '00000000002', selectedCardNum: '1000000000000002' }, nextProgram: 'COCRDSLC' },
    ]);
    render();
    await screen.findByText('1000000000000006');
    await userEvent.type(screen.getByLabelText('Select row 3'), 'S{Enter}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/cards/view?acctId=00000000002&cardNum=1000000000000002');
    const q = new URLSearchParams(calls[1].url.split('?')[1]);
    expect(q.get('selections')).toBe(',,S,,,,');
    expect(q.get('start')).toBe('1000000000000000');
    expect(q.get('page')).toBe('1');
  });

  it('routes a U selection to the card update screen', async () => {
    mockFetch([200, page1], [
      200,
      { data: { ...page1.data, selectedAcctId: '00000000000', selectedCardNum: '1000000000000000' }, nextProgram: 'COCRDUPC' },
    ]);
    render();
    await screen.findByText('1000000000000006');
    await userEvent.type(screen.getByLabelText('Select row 1'), 'U{Enter}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/cards/update?acctId=00000000000&cardNum=1000000000000000');
  });

  it('flags rows on a multiple-selection error and keeps the codes', async () => {
    mockFetch([200, page1], [
      400,
      {
        message: 'PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE',
        messageType: 'ERROR',
        field: 'select2',
        data: { ...page1.data, infoMessage: undefined, errorRows: [2, 4] },
      },
    ]);
    render();
    await screen.findByText('1000000000000006');
    await userEvent.type(screen.getByLabelText('Select row 2'), 'S');
    await userEvent.type(screen.getByLabelText('Select row 4'), 'U{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE');
    expect(screen.getByLabelText('Select row 2')).toHaveFocus();
    expect(screen.getByLabelText('Select row 2')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Select row 4')).toHaveValue('U');
  });

  it('pages with F8 / F7 and exits with F3', async () => {
    const page2 = { ...page1, data: { ...page1.data, page: { ...page1.data.page, pageNumber: 2, hasPrevPage: true } } };
    const calls = mockFetch([200, page1], [200, page2], [
      200,
      { data: page1.data, message: 'NO PREVIOUS PAGES TO DISPLAY', messageType: 'ERROR' },
    ]);
    render();
    await screen.findByText('1000000000000006');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(await screen.findByText('Page 2')).toBeInTheDocument();
    let q = new URLSearchParams(calls[1].url.split('?')[1]);
    expect(q.get('after')).toBe('1000000000000006');
    expect(q.get('start')).toBe('1000000000000000');
    expect(q.get('page')).toBe('1');
    fireEvent.keyDown(window, { key: 'F7' });
    expect(await screen.findByText('NO PREVIOUS PAGES TO DISPLAY')).toBeInTheDocument();
    q = new URLSearchParams(calls[2].url.split('?')[1]);
    expect(q.get('before')).toBe('1000000000000000');
    expect(q.get('page')).toBe('2');
    await userEvent.click(screen.getByRole('button', { name: 'F3=Exit' }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/menu'));
  });
});
