import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { mockFetch, renderWithSession } from '../../test/renderWithSession';
import { BillPayPage } from './BillPayPage';

const USER = { userId: 'USER0001', userType: 'U' as const };

function Where() {
  const location = useLocation();
  return <p>At {location.pathname}</p>;
}

function renderPage(route = '/billpay') {
  return renderWithSession(
    <Routes>
      <Route path="/billpay" element={<BillPayPage />} />
      <Route path="*" element={<Where />} />
    </Routes>,
    { user: USER, route },
  );
}

const acctInput = () => screen.getByLabelText('Enter Acct ID:');
const confirmInput = () => screen.getByLabelText(/Please confirm:/);

describe('BillPayPage (COBIL00C)', () => {
  it('renders the COBIL00 map with the cursor on Acct ID', () => {
    renderPage();
    expect(screen.getByText('Tran: CB00')).toBeInTheDocument();
    expect(screen.getByText('Prog: COBIL00C')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Bill Payment' })).toBeInTheDocument();
    expect(acctInput()).toHaveFocus();
    expect(acctInput()).toHaveAttribute('maxLength', '11');
    expect(confirmInput()).toHaveAttribute('maxLength', '1');
  });

  it('posts the fields, shows the balance and moves the cursor to Confirm', async () => {
    const calls = mockFetch([
      200,
      {
        data: { acctId: '00000000001', currentBalance: 1194.5 },
        message: 'Confirm to make a bill payment...',
        messageType: 'INFO',
        field: 'confirm',
      },
    ]);
    renderPage();
    await userEvent.type(acctInput(), '00000000001{Enter}');

    expect(await screen.findByText('Confirm to make a bill payment...')).toBeInTheDocument();
    expect(screen.getByLabelText('Current balance')).toHaveTextContent('1,194.50');
    expect(confirmInput()).toHaveFocus();
    expect(acctInput()).toHaveValue('00000000001');
    expect(calls[0].url).toBe('/api/bill-payments');
    expect(calls[0].init?.method).toBe('POST');
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ acctId: '00000000001', confirm: '' });
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
  });

  it('renders server validation errors and focuses the field', async () => {
    mockFetch([400, { message: 'Invalid value. Valid values are (Y/N)...', messageType: 'ERROR', field: 'confirm' }]);
    renderPage();
    await userEvent.type(acctInput(), '00000000001');
    await userEvent.type(confirmInput(), 'X{Enter}');

    const status = await screen.findByRole('status');
    await waitFor(() => expect(status).toHaveTextContent('Invalid value. Valid values are (Y/N)...'));
    expect(status).toHaveClass('screen-message-error');
    expect(confirmInput()).toHaveFocus();
  });

  it('keeps the balance shown with "You have nothing to pay..." and focuses Acct ID', async () => {
    mockFetch(
      [200, { data: { acctId: '00000000001', currentBalance: 10 }, message: 'Confirm to make a bill payment...', field: 'confirm' }],
      [400, { data: { acctId: '00000000001', currentBalance: 0 }, message: 'You have nothing to pay...', messageType: 'ERROR', field: 'acctId' }],
    );
    renderPage();
    await userEvent.type(acctInput(), '00000000001{Enter}');
    await screen.findByText('Confirm to make a bill payment...');
    await userEvent.type(confirmInput(), 'Y{Enter}');
    expect(await screen.findByText('You have nothing to pay...')).toBeInTheDocument();
    expect(screen.getByLabelText('Current balance')).toHaveTextContent('0.00');
    expect(acctInput()).toHaveFocus();
  });

  it('clears the screen after a successful payment', async () => {
    const calls = mockFetch([
      200,
      {
        data: { acctId: '00000000001', currentBalance: 0, tranId: '0000000000000301', amount: 194 },
        message: 'Payment successful.  Your Transaction ID is 0000000000000301.',
        messageType: 'SUCCESS',
        field: 'acctId',
      },
    ]);
    renderPage();
    await userEvent.type(acctInput(), '00000000001');
    await userEvent.type(confirmInput(), 'Y{Enter}');

    const status = await screen.findByRole('status');
    await waitFor(() =>
      expect(status).toHaveTextContent('Payment successful. Your Transaction ID is 0000000000000301.'),
    );
    expect(status).toHaveClass('screen-message-success');
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ acctId: '00000000001', confirm: 'Y' });
    expect(acctInput()).toHaveValue('');
    expect(confirmInput()).toHaveValue('');
    expect(screen.getByLabelText('Current balance')).toHaveTextContent('');
    expect(acctInput()).toHaveFocus();
  });

  it('clears the screen when the payment is declined (N)', async () => {
    mockFetch([200, { field: 'acctId' }]);
    renderPage();
    await userEvent.type(acctInput(), '00000000001');
    await userEvent.type(confirmInput(), 'N{Enter}');
    await waitFor(() => expect(acctInput()).toHaveValue(''));
    expect(screen.getByRole('status')).toHaveTextContent('');
  });

  it('loads the balance of a preselected account', async () => {
    const calls = mockFetch([
      200,
      { data: { acctId: '00000000002', currentBalance: 158 }, message: 'Confirm to make a bill payment...', field: 'confirm' },
    ]);
    renderPage('/billpay?acctId=00000000002');
    expect(await screen.findByText('Confirm to make a bill payment...')).toBeInTheDocument();
    expect(calls[0].url).toBe('/api/bill-payments/00000000002');
    expect(calls[0].init?.method).toBe('GET');
    expect(acctInput()).toHaveValue('00000000002');
    expect(screen.getByLabelText('Current balance')).toHaveTextContent('158.00');
  });

  it('PF4 clears the screen', async () => {
    mockFetch([400, { message: 'Account ID NOT found...', messageType: 'ERROR', field: 'acctId' }]);
    renderPage();
    await userEvent.type(acctInput(), '12345678901{Enter}');
    await screen.findByText('Account ID NOT found...');
    await userEvent.type(confirmInput(), 'Y');
    await userEvent.keyboard('{F4}');
    expect(acctInput()).toHaveValue('');
    expect(confirmInput()).toHaveValue('');
    expect(screen.getByRole('status')).toHaveTextContent('');
    expect(acctInput()).toHaveFocus();
  });

  it('PF3 returns to the main menu', async () => {
    renderPage();
    await userEvent.keyboard('{F3}');
    expect(screen.getByText('At /menu')).toBeInTheDocument();
  });

  it('follows nextProgram from the server (expired session)', async () => {
    mockFetch([401, { message: 'Please sign on ...', messageType: 'ERROR', nextProgram: 'COSGN00C' }]);
    renderPage();
    await userEvent.type(acctInput(), '00000000001{Enter}');
    expect(await screen.findByText('At /signon')).toBeInTheDocument();
  });
});
