import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { mockFetch, renderWithSession } from '../../test/renderWithSession';
import { AccountViewPage } from './AccountViewPage';
import { ACCOUNT_1 } from './testData';

function renderPage() {
  return renderWithSession(
    <Routes>
      <Route path="/accounts/view" element={<AccountViewPage />} />
      <Route path="/menu" element={<p>menu page</p>} />
      <Route path="/signon" element={<p>signon page</p>} />
    </Routes>,
    { user: { userId: 'USER0001', userType: 'U' }, route: '/accounts/view' },
  );
}

describe('AccountViewPage (COACTVWC)', () => {
  it('renders the CAVW screen with the prompt', () => {
    renderPage();
    expect(screen.getByText('Tran: CAVW')).toBeInTheDocument();
    expect(screen.getByText('Prog: COACTVWC')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'View Account' })).toBeInTheDocument();
    expect(screen.getByText('Enter or update id of account to display')).toBeInTheDocument();
  });

  it('reads the account on ENTER and shows account and customer details', async () => {
    const calls = mockFetch([200, { data: ACCOUNT_1 }]);
    renderPage();
    await userEvent.type(screen.getByLabelText('Account Number :'), '00000000001{Enter}');
    expect(await screen.findByLabelText('First Name')).toHaveTextContent('Immanuel');
    expect(calls[0].url).toBe('/api/accounts/00000000001');
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
    expect(screen.getByLabelText('SSN')).toHaveTextContent('020-97-3888');
    expect(screen.getByLabelText('Credit Limit')).toHaveTextContent('2,020.00');
    expect(screen.getByLabelText('Phone 1')).toHaveTextContent('(908)119-8310');
    expect(screen.getByLabelText('Active Y/N')).toHaveTextContent('Y');
  });

  it('shows the COBOL error message and puts the cursor on the account number', async () => {
    mockFetch([
      400,
      { message: 'Account Filter must  be a non-zero 11 digit number', messageType: 'ERROR', field: 'acctId' },
    ]);
    renderPage();
    const input = screen.getByLabelText('Account Number :');
    await userEvent.type(input, '12{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('Account Filter must be a non-zero 11 digit number', {
      normalizeWhitespace: true,
    });
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(input).toHaveFocus();
  });

  it('still shows the account part when the customer is missing', async () => {
    mockFetch([
      404,
      {
        message: 'CustId:999999992 not found in customer master.Resp: 000000013  REAS:000000080',
        field: 'acctId',
        data: { ...ACCOUNT_1, acctId: '99999999992', firstName: undefined, custId: undefined },
      },
    ]);
    renderPage();
    await userEvent.type(screen.getByLabelText('Account Number :'), '99999999992{Enter}');
    expect(await screen.findByRole('status')).toHaveTextContent('CustId:999999992 not found in customer master.');
    expect(screen.getByLabelText('Opened')).toHaveTextContent('2014-11-20');
    expect(screen.getByLabelText('First Name')).toHaveTextContent('');
  });

  it('PF3 returns to the main menu', async () => {
    renderPage();
    await userEvent.keyboard('{F3}');
    expect(screen.getByText('menu page')).toBeInTheDocument();
  });

  it('goes to sign-on when the session has expired', async () => {
    mockFetch([401, { message: 'Please sign on ...', nextProgram: 'COSGN00C' }]);
    renderPage();
    await userEvent.type(screen.getByLabelText('Account Number :'), '00000000001{Enter}');
    expect(await screen.findByText('signon page')).toBeInTheDocument();
  });
});
