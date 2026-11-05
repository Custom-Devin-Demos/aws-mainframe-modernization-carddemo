import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';

/** Mirrors com.carddemo.online.billpay.BillPayScreen. */
export interface BillPayScreen {
  acctId: string;
  currentBalance: number;
  tranId?: string;
  cardNum?: string;
  amount?: number;
}

/** Navigation state: the calling program (CDEMO-FROM-PROGRAM) and a preselected account (also `?acctId=`). */
export interface BillPayLocationState {
  fromProgram?: string;
  acctId?: string;
}

type Field = 'acctId' | 'confirm';

function formatBalance(value: number | undefined): string {
  if (value === undefined || value === null) return '';
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** COBIL00C / COBIL00: pay the account balance in full. */
export function BillPayPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as BillPayLocationState;
  const [params] = useSearchParams();
  const preselected = state.acctId ?? params.get('acctId') ?? undefined;

  const [acctId, setAcctId] = useState('');
  const [confirm, setConfirm] = useState('');
  const [balance, setBalance] = useState<number | undefined>();
  const [message, setMessage] = useState<string | undefined>();
  const [messageType, setMessageType] = useState<MessageType | undefined>();
  const [field, setField] = useState<Field>('acctId');
  const [focusTick, setFocusTick] = useState(0);
  const refs = { acctId: useRef<HTMLInputElement>(null), confirm: useRef<HTMLInputElement>(null) };

  useEffect(() => {
    refs[field].current?.focus();
  }, [field, focusTick]);

  const focus = (f: string | undefined) => {
    setField(f === 'confirm' ? 'confirm' : 'acctId');
    setFocusTick((t) => t + 1);
  };

  const apply = useCallback(
    (res: ScreenResponse<BillPayScreen>, success: boolean) => {
      if (res.nextProgram) {
        navigate(programPath(res.nextProgram));
        return;
      }
      const paid = success && res.messageType === 'SUCCESS';
      if (paid || (success && !res.data)) {
        // INITIALIZE-ALL-FIELDS / CLEAR-CURRENT-SCREEN
        setAcctId('');
        setBalance(undefined);
      } else if (res.data) {
        setBalance(res.data.currentBalance);
      }
      setConfirm('');
      setMessage(res.message);
      setMessageType(res.messageType);
      focus(res.field);
    },
    [navigate],
  );

  const send = useCallback(
    async (request: () => Promise<ScreenResponse<BillPayScreen>>) => {
      try {
        apply(await request(), true);
      } catch (e) {
        if (e instanceof ApiError) apply(e.response as ScreenResponse<BillPayScreen>, false);
        else {
          setMessage('Unexpected error. Please try again ...');
          setMessageType('ERROR');
        }
      }
    },
    [apply],
  );

  // First entry with an account preselected (CDEMO-CB00-TRN-SELECTED): show its balance.
  useEffect(() => {
    if (preselected) {
      setAcctId(preselected);
      void send(() => api.get<BillPayScreen>(`/api/bill-payments/${encodeURIComponent(preselected)}`));
    }
  }, []);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    void send(() => api.post<BillPayScreen>('/api/bill-payments', { acctId, confirm }));
  };

  const clearScreen = () => {
    setAcctId('');
    setConfirm('');
    setBalance(undefined);
    setMessage(undefined);
    setMessageType(undefined);
    focus('acctId');
  };

  return (
    <Screen
      tranId="CB00"
      program="COBIL00C"
      title="Bill Payment"
      message={message}
      messageType={messageType}
      pfKeys={[
        { key: 'F3', label: 'Back', onPress: () => navigate(programPath(state.fromProgram || 'COMEN01C')) },
        { key: 'F4', label: 'Clear', onPress: clearScreen },
      ]}
    >
      <form onSubmit={onSubmit} aria-label="Bill Payment">
        <p>
          <label htmlFor="billpay-acct-id">Enter Acct ID:</label>{' '}
          <input
            id="billpay-acct-id"
            ref={refs.acctId}
            value={acctId}
            maxLength={11}
            size={11}
            onChange={(e) => setAcctId(e.target.value)}
          />
        </p>
        <p>
          <span>Your current balance is:</span> <span aria-label="Current balance">{formatBalance(balance)}</span>
        </p>
        <p>
          <label htmlFor="billpay-confirm">Do you want to pay your balance now. Please confirm:</label>{' '}
          <input
            id="billpay-confirm"
            ref={refs.confirm}
            value={confirm}
            maxLength={1}
            size={1}
            onChange={(e) => setConfirm(e.target.value)}
          />{' '}
          (Y/N)
        </p>
        <button type="submit">ENTER=Continue</button>
      </form>
    </Screen>
  );
}
