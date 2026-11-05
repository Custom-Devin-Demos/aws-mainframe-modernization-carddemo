import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { mockFetch, renderWithSession } from '../../test/renderWithSession';
import { AccountUpdatePage } from './AccountUpdatePage';
import { ACCOUNT_1 } from './testData';

function renderPage() {
  return renderWithSession(
    <Routes>
      <Route path="/accounts/update" element={<AccountUpdatePage />} />
      <Route path="/menu" element={<p>menu page</p>} />
    </Routes>,
    { user: { userId: 'USER0001', userType: 'U' }, route: '/accounts/update' },
  );
}

const FETCHED = [200, { data: ACCOUNT_1, message: 'Update account details presented above.', messageType: 'INFO' }] as [
  number,
  unknown,
];

async function fetchAccount1() {
  await userEvent.type(screen.getByLabelText('Account Number :'), '00000000001{Enter}');
  await screen.findByDisplayValue('Immanuel');
}

const body = (init?: RequestInit) => JSON.parse(String(init?.body));

describe('AccountUpdatePage (COACTUPC)', () => {
  it('prompts for the account id and only offers PF3 before a fetch', () => {
    renderPage();
    expect(screen.getByText('Tran: CAUP')).toBeInTheDocument();
    expect(screen.getByText('Prog: COACTUPC')).toBeInTheDocument();
    expect(screen.getByTestId('info-message')).toHaveTextContent('Enter or update id of account to update');
    expect(screen.getByRole('button', { name: 'F3=Exit' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'F12=Cancel' })).not.toBeInTheDocument();
  });

  it('shows "No input received" for a blank account id', async () => {
    const calls = mockFetch([400, { message: 'No input received', messageType: 'ERROR', field: 'acctId' }]);
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: 'ENTER' }));
    expect(await screen.findByRole('status')).toHaveTextContent('No input received');
    expect(calls[0].url).toBe('/api/accounts?mode=update');
    expect(screen.getByLabelText('Account Number :')).toHaveFocus();
  });

  it('fetches the account into the split BMS fields', async () => {
    const calls = mockFetch(FETCHED);
    renderPage();
    await fetchAccount1();
    expect(calls[0].url).toBe('/api/accounts/00000000001?mode=update');
    expect(screen.getByLabelText('Opened year')).toHaveValue('2014');
    expect(screen.getByLabelText('Opened month')).toHaveValue('11');
    expect(screen.getByLabelText('Credit Limit')).toHaveValue('2020.00');
    expect(screen.getByLabelText('SSN part 1')).toHaveValue('020');
    expect(screen.getByLabelText('SSN part 3')).toHaveValue('3888');
    expect(screen.getByLabelText('Phone 2 area code')).toHaveValue('373');
    expect(screen.getByLabelText('Customer id')).toHaveTextContent('000000001');
    expect(screen.getByLabelText('Account Number :')).toHaveAttribute('readonly');
    expect(screen.getByTestId('info-message')).toHaveTextContent('Update account details presented above.');
    expect(screen.getByRole('button', { name: 'F12=Cancel' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
  });

  it('ENTER validates, then PF5 saves', async () => {
    const saved = { ...ACCOUNT_1, ficoScore: '750', version: 'v2' };
    const calls = mockFetch(
      FETCHED,
      [200, { message: 'Changes validated.Press F5 to save', messageType: 'INFO' }],
      [200, { data: saved, message: 'Changes committed to database', messageType: 'SUCCESS' }],
    );
    renderPage();
    await fetchAccount1();
    const fico = screen.getByLabelText('FICO Score');
    await userEvent.clear(fico);
    await userEvent.type(fico, '750{Enter}');

    await screen.findByRole('button', { name: 'F5=Save' });
    expect(calls[1].url).toBe('/api/accounts/00000000001?validateOnly=true');
    expect(calls[1].init?.method).toBe('PUT');
    expect(body(calls[1].init)).toMatchObject({ ficoScore: '750', version: 'v1', phone1a: '908', dobYear: '1961' });
    expect(screen.getByTestId('info-message')).toHaveTextContent('Changes validated.Press F5 to save');

    await userEvent.keyboard('{F5}');
    expect(await screen.findByRole('status')).toHaveTextContent('Changes committed to database');
    expect(screen.getByRole('status')).toHaveClass('screen-message-success');
    expect(calls[2].url).toBe('/api/accounts/00000000001?validateOnly=false');
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
  });

  it('editing after validation withdraws PF5', async () => {
    mockFetch(FETCHED, [200, { message: 'Changes validated.Press F5 to save', messageType: 'INFO' }]);
    renderPage();
    await fetchAccount1();
    await userEvent.type(screen.getByLabelText('Last Name'), '{Enter}');
    await screen.findByRole('button', { name: 'F5=Save' });
    await userEvent.type(screen.getByLabelText('Last Name'), 'x');
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
  });

  it('shows a field edit error and puts the cursor on that field', async () => {
    mockFetch(FETCHED, [
      400,
      { message: 'FICO Score: should be between 300 and 850', messageType: 'ERROR', field: 'ficoScore' },
    ]);
    renderPage();
    await fetchAccount1();
    await userEvent.click(screen.getByRole('button', { name: 'ENTER' }));
    expect(await screen.findByRole('status')).toHaveTextContent('FICO Score: should be between 300 and 850');
    expect(screen.getByLabelText('FICO Score')).toHaveFocus();
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
  });

  it('reports no change detected on the status field', async () => {
    mockFetch(FETCHED, [
      200,
      { data: ACCOUNT_1, message: 'No change detected with respect to values fetched.', messageType: 'INFO', field: 'acctStatus' },
    ]);
    renderPage();
    await fetchAccount1();
    await userEvent.click(screen.getByRole('button', { name: 'ENTER' }));
    expect(await screen.findByRole('status')).toHaveTextContent('No change detected with respect to values fetched.');
    await waitFor(() => expect(screen.getByLabelText('Active Y/N')).toHaveFocus());
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
  });

  it('reloads the current record when someone else changed it', async () => {
    const fresh = { ...ACCOUNT_1, creditLimit: 9999, version: 'v9' };
    const calls = mockFetch(
      FETCHED,
      [200, { message: 'Changes validated.Press F5 to save', messageType: 'INFO' }],
      [409, { message: 'Record changed by some one else. Please review', field: 'acctId', data: fresh }],
    );
    renderPage();
    await fetchAccount1();
    await userEvent.click(screen.getByRole('button', { name: 'ENTER' }));
    await userEvent.click(await screen.findByRole('button', { name: 'F5=Save' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Record changed by some one else. Please review');
    expect(screen.getByLabelText('Credit Limit')).toHaveValue('9999.00');
    expect(screen.queryByRole('button', { name: 'F5=Save' })).not.toBeInTheDocument();
    expect(calls).toHaveLength(3);
  });

  it('PF12 cancels changes by re-reading the account', async () => {
    const calls = mockFetch(FETCHED, FETCHED);
    renderPage();
    await fetchAccount1();
    const last = screen.getByLabelText('Last Name');
    await userEvent.clear(last);
    await userEvent.type(last, 'Changed');
    await userEvent.keyboard('{F12}');
    await waitFor(() => expect(screen.getByLabelText('Last Name')).toHaveValue('Kessler'));
    expect(calls[1].url).toBe('/api/accounts/00000000001?mode=update');
  });

  it('PF3 returns to the main menu', async () => {
    renderPage();
    await userEvent.keyboard('{F3}');
    expect(screen.getByText('menu page')).toBeInTheDocument();
  });
});
