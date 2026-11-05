import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockFetch } from '../../test/renderWithSession';
import { renderTransactions } from './testRoutes';

const fill = async (label: string, value: string) => userEvent.type(screen.getByLabelText(label), value);

async function fillValidForm() {
  await fill('Enter Acct #:', '50');
  await fill('Type CD:', '01');
  await fill('Category CD:', '0001');
  await fill('Source:', 'POS TERM');
  await fill('Description:', 'Test purchase');
  await fill('Amount:', '-00000012.34');
  await fill('Orig Date:', '2024-01-15');
  await fill('Proc Date:', '2024-01-16');
  await fill('Merchant ID:', '123456789');
  await fill('Merchant Name:', 'Test Merchant');
  await fill('Merchant City:', 'Seattle');
  await fill('Merchant Zip:', '98101');
}

describe('TransactionAddPage (COTRN02C)', () => {
  it('posts the form and clears it on success', async () => {
    const msg = 'Transaction added successfully.  Your Tran ID is 0000000996722788.';
    const calls = mockFetch([200, { data: { tranId: '0000000996722788' }, message: msg, messageType: 'SUCCESS', field: 'acctId' }]);
    renderTransactions('/transactions/add');
    expect(screen.getByLabelText('Enter Acct #:')).toHaveFocus();
    await fillValidForm();
    await fill('You are about to add this transaction. Please confirm :', 'Y{Enter}');
    expect(await screen.findByText(msg, { normalizer: (t) => t })).toBeInTheDocument();
    expect(screen.getByRole('status').textContent).toBe(msg);
    expect(screen.getByRole('status')).toHaveClass('screen-message-success');
    expect(calls[0].url).toBe('/api/transactions');
    expect(calls[0].init?.method).toBe('POST');
    expect(JSON.parse(String(calls[0].init?.body))).toMatchObject({
      acctId: '50',
      cardNum: '',
      amount: '-00000012.34',
      merchantZip: '98101',
      confirm: 'Y',
    });
    expect(screen.getByLabelText('Description:')).toHaveValue('');
    expect(screen.getByLabelText('Enter Acct #:')).toHaveFocus();
  });

  it('re-displays the map from the error response and focuses the field', async () => {
    mockFetch([
      400,
      {
        message: 'Confirm to add this transaction...',
        field: 'confirm',
        data: { acctId: '00000000050', cardNum: '0500024453765740', amount: '+00000000.00', confirm: '' },
      },
    ]);
    renderTransactions('/transactions/add');
    await fill('Enter Acct #:', '50{Enter}');
    expect(await screen.findByText('Confirm to add this transaction...')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText('Enter Acct #:')).toHaveValue('00000000050');
    expect(screen.getByLabelText('Card #:')).toHaveValue('0500024453765740');
    expect(screen.getByLabelText('Amount:')).toHaveValue('+00000000.00');
    expect(screen.getByLabelText('You are about to add this transaction. Please confirm :')).toHaveFocus();
  });

  it('focuses the field named by a validation error', async () => {
    mockFetch([400, { message: 'Amount should be in format -99999999.99', field: 'amount' }]);
    renderTransactions('/transactions/add');
    await fill('Amount:', '12.34{Enter}');
    expect(await screen.findByText('Amount should be in format -99999999.99')).toBeInTheDocument();
    expect(screen.getByLabelText('Amount:')).toHaveFocus();
    expect(screen.getByLabelText('Amount:')).toHaveValue('12.34');
  });

  it('PF5 copies the last transaction into the form', async () => {
    const calls = mockFetch([
      400,
      {
        message: 'Proc Date can NOT be empty...',
        field: 'procDate',
        data: { acctId: '00000000050', cardNum: '0500024453765740', description: 'Purchase at Kilback LLC', procDate: '' },
      },
    ]);
    renderTransactions('/transactions/add');
    await fill('Enter Acct #:', '50');
    await userEvent.keyboard('{F5}');
    expect(await screen.findByText('Proc Date can NOT be empty...')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/transactions/copy-last');
    expect(screen.getByLabelText('Description:')).toHaveValue('Purchase at Kilback LLC');
    expect(screen.getByLabelText('Proc Date:')).toHaveFocus();
  });

  it('enforces the BMS field lengths', () => {
    renderTransactions('/transactions/add');
    expect(screen.getByLabelText('Enter Acct #:')).toHaveAttribute('maxLength', '11');
    expect(screen.getByLabelText('Card #:')).toHaveAttribute('maxLength', '16');
    expect(screen.getByLabelText('Amount:')).toHaveAttribute('maxLength', '12');
    expect(screen.getByLabelText('Description:')).toHaveAttribute('maxLength', '60');
  });

  it('PF4 clears the screen and PF3 returns to the menu', async () => {
    renderTransactions('/transactions/add');
    await fill('Description:', 'abc');
    await userEvent.keyboard('{F4}');
    expect(screen.getByLabelText('Description:')).toHaveValue('');
    expect(screen.getByLabelText('Enter Acct #:')).toHaveFocus();
    await userEvent.keyboard('{F3}');
    expect(screen.getByTestId('landed')).toHaveTextContent('/menu');
  });
});
