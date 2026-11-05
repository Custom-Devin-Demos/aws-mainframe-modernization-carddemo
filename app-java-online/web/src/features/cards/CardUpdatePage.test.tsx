import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockFetch } from '../../test/renderWithSession';
import { CardUpdatePage } from './CardUpdatePage';
import { renderPage } from './testUtils';

const ROUTE = '/cards/update?acctId=00000000050&cardNum=0500024453765740';
const original = { embossedName: 'ANIYA VON', activeStatus: 'Y', expiryYear: '2023', expiryMonth: '03', expiryDay: '09' };
const card = (over: Record<string, string>) => ({
  acctId: '00000000050',
  cardNum: '0500024453765740',
  ...original,
  ...over,
});
const fetched = {
  data: { card: card({ infoMessage: 'Details of selected card shown above' }), original, state: 'SHOW_DETAILS' },
  message: 'Details of selected card shown above',
  messageType: 'INFO',
};

function body(call: { init?: RequestInit }) {
  return JSON.parse(String(call.init?.body));
}

describe('CardUpdatePage (COCRDUPC)', () => {
  it('fetches, validates, saves with F5 and returns to the list', async () => {
    const calls = mockFetch(
      [200, fetched],
      [
        200,
        {
          data: {
            card: card({ embossedName: 'ANIYA SMITH', infoMessage: 'Changes validated.Press F5 to save' }),
            original,
            state: 'CHANGES_OK_NOT_CONFIRMED',
          },
          message: 'Changes validated.Press F5 to save',
          messageType: 'INFO',
        },
      ],
      [
        200,
        {
          data: {
            card: card({ embossedName: 'ANIYA SMITH', infoMessage: 'Changes committed to database' }),
            original: { ...original, embossedName: 'ANIYA SMITH' },
            state: 'CHANGES_OKAYED_AND_DONE',
          },
          message: 'Changes committed to database',
          messageType: 'SUCCESS',
        },
      ],
    );
    renderPage(<CardUpdatePage />, '/cards/update', ROUTE);
    const name = await screen.findByDisplayValue('ANIYA VON');
    expect(calls[0].init?.method).toBe('PUT');
    expect(calls[0].url).toBe('/api/cards/0500024453765740');
    expect(body(calls[0])).toEqual({ acctId: '00000000050', original: null, confirm: false });
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();

    await userEvent.clear(name);
    await userEvent.type(name, 'ANIYA SMITH{Enter}');
    expect(await screen.findByText('Changes validated.Press F5 to save')).toBeInTheDocument();
    expect(body(calls[1])).toMatchObject({ embossedName: 'ANIYA SMITH', confirm: false, original });

    fireEvent.keyDown(window, { key: 'F5' });
    expect(await screen.findByRole('status')).toHaveTextContent('Changes committed to database');
    expect(body(calls[2])).toMatchObject({ confirm: true });
    expect(screen.getByLabelText(/Name on card/)).toHaveAttribute('readonly');

    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/cards$/);
  });

  it('shows field validation errors with the cursor on the field', async () => {
    mockFetch([200, fetched], [
      400,
      {
        message: 'Card expiry month must be between 1 and 12',
        messageType: 'ERROR',
        field: 'expiryMonth',
        data: { card: card({ expiryMonth: '13', infoMessage: 'Update card details presented above.' }), original, state: 'CHANGES_NOT_OK' },
      },
    ]);
    renderPage(<CardUpdatePage />, '/cards/update', ROUTE);
    await screen.findByDisplayValue('ANIYA VON');
    await userEvent.clear(screen.getByLabelText('Expiry month'));
    await userEvent.type(screen.getByLabelText('Expiry month'), '13{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('Card expiry month must be between 1 and 12');
    expect(screen.getByLabelText('Expiry month')).toHaveFocus();
    expect(screen.getByText('Update card details presented above.')).toBeInTheDocument();
  });

  it('shows the refreshed record when it was changed by someone else', async () => {
    mockFetch([200, fetched], [
      409,
      {
        message: 'Record changed by some one else. Please review',
        messageType: 'ERROR',
        field: 'embossedName',
        data: {
          card: card({ embossedName: 'SOMEONE ELSE', infoMessage: 'Details of selected card shown above' }),
          original: { ...original, embossedName: 'SOMEONE ELSE' },
          state: 'SHOW_DETAILS',
        },
      },
    ]);
    renderPage(<CardUpdatePage />, '/cards/update', ROUTE);
    const name = await screen.findByDisplayValue('ANIYA VON');
    await userEvent.type(name, 'X{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('Record changed by some one else. Please review');
    expect(screen.getByLabelText(/Name on card/)).toHaveValue('SOMEONE ELSE');
  });

  it('F12 re-reads the record and F3 exits to the list', async () => {
    const calls = mockFetch([200, fetched], [200, fetched]);
    renderPage(<CardUpdatePage />, '/cards/update', ROUTE);
    const name = await screen.findByDisplayValue('ANIYA VON');
    await userEvent.type(name, 'X');
    fireEvent.keyDown(window, { key: 'F12' });
    expect(await screen.findByDisplayValue('ANIYA VON')).toBeInTheDocument();
    expect(body(calls[1])).toMatchObject({ original: null });
    fireEvent.keyDown(window, { key: 'F3' });
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/cards$/);
  });

  it('prompts for keys and searches when opened from the menu', async () => {
    const calls = mockFetch([
      404,
      {
        message: 'Did not find cards for this search condition',
        messageType: 'ERROR',
        field: 'acctId',
        data: { card: { acctId: '00000000050', cardNum: '1111222233334444', infoMessage: 'Please enter Account and Card Number' }, state: 'DETAILS_NOT_FETCHED' },
      },
    ]);
    renderPage(<CardUpdatePage />, '/cards/update', '/cards/update');
    expect(screen.getByText('Please enter Account and Card Number')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Account Number/), '00000000050');
    await userEvent.type(screen.getByLabelText(/Card Number/), '1111222233334444{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('Did not find cards for this search condition');
    expect(calls[0].url).toBe('/api/cards/1111222233334444');
    expect(screen.getByLabelText(/Account Number/)).toHaveFocus();
    expect(screen.getByLabelText(/Name on card/)).toHaveAttribute('readonly');
  });
});
