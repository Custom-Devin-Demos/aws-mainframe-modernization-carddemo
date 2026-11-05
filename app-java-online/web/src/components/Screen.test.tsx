import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Screen } from './Screen';

describe('Screen', () => {
  it('renders the BMS header, title and error message', () => {
    render(
      <Screen tranId="CC00" program="COSGN00C" title="Sign On" message="Wrong Password. Try again ..." messageType="ERROR">
        <p>body</p>
      </Screen>,
    );
    expect(screen.getByText('Tran: CC00')).toBeInTheDocument();
    expect(screen.getByText('Prog: COSGN00C')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Sign On' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Wrong Password. Try again ...');
    expect(screen.getByRole('status')).toHaveClass('screen-message-error');
  });

  it('invokes PF keys from the keyboard and the legend', async () => {
    const onBack = vi.fn();
    render(
      <Screen tranId="CM00" program="COMEN01C" title="Main Menu" pfKeys={[{ key: 'F3', label: 'Exit', onPress: onBack }]}>
        <p>body</p>
      </Screen>,
    );
    await userEvent.keyboard('{F3}');
    await userEvent.click(screen.getByRole('button', { name: 'F3=Exit' }));
    expect(onBack).toHaveBeenCalledTimes(2);
  });
});
