import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { mockFetch, renderWithSession } from '../../test/renderWithSession';
import { ReportsPage } from './ReportsPage';

const USER = { userId: 'USER0001', userType: 'U' as const };

function Where() {
  const location = useLocation();
  return <p>At {location.pathname}</p>;
}

function renderPage() {
  return renderWithSession(
    <Routes>
      <Route path="/reports" element={<ReportsPage />} />
      <Route path="*" element={<Where />} />
    </Routes>,
    { user: USER, route: '/reports' },
  );
}

const confirmInput = () => screen.getByLabelText(/Please confirm:/);
const bodyOf = (call: { init?: RequestInit }) => JSON.parse(String(call.init?.body));

describe('ReportsPage (CORPT00C)', () => {
  it('renders the CORPT00 map with the cursor on Monthly', () => {
    renderPage();
    expect(screen.getByText('Tran: CR00')).toBeInTheDocument();
    expect(screen.getByText('Prog: CORPT00C')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Transaction Reports' })).toBeInTheDocument();
    expect(screen.getByLabelText('Monthly (Current Month)')).toHaveFocus();
    expect(screen.getByLabelText('Start Date Year')).toHaveAttribute('maxLength', '4');
  });

  it('submits a monthly report and clears the form', async () => {
    const calls = mockFetch([
      200,
      {
        data: { requestId: 7, reportName: 'Monthly', startDate: '2026-11-01', endDate: '2026-11-30', status: 'SUBMITTED' },
        message: 'Monthly report submitted for printing ...',
        messageType: 'SUCCESS',
        field: 'reportType',
      },
    ]);
    renderPage();
    await userEvent.click(screen.getByLabelText('Monthly (Current Month)'));
    await userEvent.type(confirmInput(), 'Y{Enter}');

    const status = await screen.findByRole('status');
    await waitFor(() => expect(status).toHaveTextContent('Monthly report submitted for printing ...'));
    expect(status).toHaveClass('screen-message-success');
    expect(calls[0].url).toBe('/api/reports');
    expect(calls[0].init?.method).toBe('POST');
    expect(bodyOf(calls[0])).toEqual({ reportType: 'MONTHLY', startDate: null, endDate: null, confirm: 'Y' });
    expect(screen.getByLabelText('Submitted report request')).toHaveTextContent(
      'Request 7: Monthly 2026-11-01 to 2026-11-30 (SUBMITTED)',
    );
    expect(screen.getByLabelText('Monthly (Current Month)')).not.toBeChecked();
    expect(confirmInput()).toHaveValue('');
    expect(screen.getByLabelText('Monthly (Current Month)')).toHaveFocus();
  });

  it('sends the custom date range as typed', async () => {
    const calls = mockFetch([400, { message: 'Please confirm to print the Custom report...', messageType: 'ERROR', field: 'confirm' }]);
    renderPage();
    await userEvent.click(screen.getByLabelText('Custom (Date Range)'));
    await userEvent.type(screen.getByLabelText('Start Date Month'), '1');
    await userEvent.type(screen.getByLabelText('Start Date Day'), '05');
    await userEvent.type(screen.getByLabelText('Start Date Year'), '2022');
    await userEvent.type(screen.getByLabelText('End Date Month'), '12');
    await userEvent.type(screen.getByLabelText('End Date Day'), '31');
    await userEvent.type(screen.getByLabelText('End Date Year'), '2022{Enter}');

    expect(await screen.findByText('Please confirm to print the Custom report...')).toBeInTheDocument();
    expect(bodyOf(calls[0])).toEqual({
      reportType: 'CUSTOM',
      startDate: { month: '1', day: '05', year: '2022' },
      endDate: { month: '12', day: '31', year: '2022' },
      confirm: '',
    });
    expect(confirmInput()).toHaveFocus();
    expect(screen.getByLabelText('Start Date Month')).toHaveValue('1');
  });

  it.each([
    ['startDate.month', 'Start Date - Month can NOT be empty...', 'Start Date Month'],
    ['startDate.day', 'Start Date - Not a valid Day...', 'Start Date Day'],
    ['startDate.year', 'Start Date - Not a valid Year...', 'Start Date Year'],
    ['endDate.month', 'End Date - Not a valid date...', 'End Date Month'],
    ['endDate.day', 'End Date - Day can NOT be empty...', 'End Date Day'],
    ['endDate.year', 'End Date - Year can NOT be empty...', 'End Date Year'],
  ])('focuses %s for "%s"', async (field, message, label) => {
    mockFetch([400, { message, messageType: 'ERROR', field }]);
    renderPage();
    await userEvent.click(screen.getByLabelText('Custom (Date Range)'));
    await userEvent.type(confirmInput(), 'Y{Enter}');
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
    expect(screen.getByLabelText(label)).toHaveFocus();
    expect(screen.getByLabelText('Custom (Date Range)')).toBeChecked();
  });

  it('shows "Select a report type" with the cursor back on Monthly', async () => {
    const calls = mockFetch([400, { message: 'Select a report type to print report...', messageType: 'ERROR', field: 'reportType' }]);
    renderPage();
    await userEvent.type(confirmInput(), 'Y{Enter}');
    expect(await screen.findByText('Select a report type to print report...')).toBeInTheDocument();
    expect(bodyOf(calls[0]).reportType).toBeNull();
    expect(screen.getByLabelText('Monthly (Current Month)')).toHaveFocus();
  });

  it('clears the form when the request is declined (N)', async () => {
    mockFetch([200, { field: 'reportType' }]);
    renderPage();
    await userEvent.click(screen.getByLabelText('Yearly (Current Year)'));
    await userEvent.type(confirmInput(), 'N{Enter}');
    await waitFor(() => expect(screen.getByLabelText('Yearly (Current Year)')).not.toBeChecked());
    expect(confirmInput()).toHaveValue('');
    expect(screen.getByRole('status')).toHaveTextContent('');
  });

  it('PF3 returns to the main menu', async () => {
    renderPage();
    await userEvent.keyboard('{F3}');
    expect(screen.getByText('At /menu')).toBeInTheDocument();
  });

  it('follows nextProgram from the server (expired session)', async () => {
    mockFetch([401, { message: 'Please sign on ...', messageType: 'ERROR', nextProgram: 'COSGN00C' }]);
    renderPage();
    await userEvent.click(screen.getByLabelText('Monthly (Current Month)'));
    await userEvent.type(confirmInput(), 'Y{Enter}');
    expect(await screen.findByText('At /signon')).toBeInTheDocument();
  });
});
