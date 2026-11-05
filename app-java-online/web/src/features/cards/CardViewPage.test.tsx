import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockFetch } from '../../test/renderWithSession';
import { CardViewPage } from './CardViewPage';
import { renderPage } from './testUtils';

const detail = {
  data: {
    acctId: '00000000050',
    cardNum: '0500024453765740',
    embossedName: 'Aniya Von',
    activeStatus: 'Y',
    expiryMonth: '03',
    expiryYear: '2023',
    expiryDay: '09',
    infoMessage: '   Displaying requested details',
  },
  message: '   Displaying requested details',
  messageType: 'INFO',
};

describe('CardViewPage (COCRDSLC)', () => {
  it('reads the card passed by the list and protects the keys', async () => {
    const calls = mockFetch([200, detail]);
    renderPage(<CardViewPage />, '/cards/view', '/cards/view?acctId=00000000050&cardNum=0500024453765740');
    expect(await screen.findByDisplayValue('Aniya Von')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/cards/0500024453765740?acctId=00000000050');
    expect(screen.getByLabelText('Expiry month')).toHaveValue('03');
    expect(screen.getByLabelText('Expiry year')).toHaveValue('2023');
    expect(screen.getByLabelText(/Account Number/)).toHaveAttribute('readonly');
    expect(screen.getByText('Displaying requested details')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'F3=Exit' }));
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/cards$/);
  });

  it('searches typed keys and shows server errors on the flagged field', async () => {
    const calls = mockFetch([
      400,
      {
        message: 'Card number not provided',
        messageType: 'ERROR',
        field: 'cardNum',
        data: { acctId: '00000000050', cardNum: '', infoMessage: 'Please enter Account and Card Number' },
      },
    ]);
    renderPage(<CardViewPage />, '/cards/view', '/cards/view');
    expect(screen.getByText('Please enter Account and Card Number')).toBeInTheDocument();
    expect(screen.getByLabelText(/Account Number/)).toHaveFocus();
    await userEvent.type(screen.getByLabelText(/Account Number/), '00000000050{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('Card number not provided');
    expect(calls[0].url).toBe('/api/cards/%20?acctId=00000000050');
    expect(screen.getByLabelText(/Card Number/)).toHaveFocus();
  });

  it('exits to the menu with F3 when not called from the list', async () => {
    mockFetch();
    renderPage(<CardViewPage />, '/cards/view', '/cards/view');
    await userEvent.keyboard('{F3}');
    expect(await screen.findByTestId('location')).toHaveTextContent('/menu');
  });
});
