import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import type { MessageType } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { getTransaction, type TransactionNavState, type TransactionView } from './api';
import { useFieldFocus } from './useFieldFocus';
import './transactions.css';

const MSG_EMPTY = 'Tran ID can NOT be empty...';

const FIELDS: Array<[keyof TransactionView, string]> = [
  ['tranId', 'Transaction ID:'],
  ['cardNum', 'Card Number:'],
  ['typeCd', 'Type CD:'],
  ['catCd', 'Category CD:'],
  ['source', 'Source:'],
  ['description', 'Description:'],
  ['amount', 'Amount:'],
  ['origDate', 'Orig Date:'],
  ['procDate', 'Proc Date:'],
  ['merchantId', 'Merchant ID:'],
  ['merchantName', 'Merchant Name:'],
  ['merchantCity', 'Merchant City:'],
  ['merchantZip', 'Merchant Zip:'],
];

/** COTRN01C (CT01) - View Transaction. */
export function TransactionViewPage() {
  const navigate = useNavigate();
  const nav = (useLocation().state ?? {}) as TransactionNavState;
  const { focus, register } = useFieldFocus('tranId');
  const [tranId, setTranId] = useState(nav.tranId ?? '');
  const [view, setView] = useState<TransactionView | null>(null);
  const [message, setMessage] = useState<{ text?: string; type?: MessageType }>({});

  const fetchTran = useCallback(
    async (id: string) => {
      // PROCESS-ENTER-KEY: TRNIDINI = SPACES (checked before any read, fields are kept)
      if (id.trim() === '') {
        setMessage({ text: MSG_EMPTY, type: 'ERROR' });
        focus('tranId');
        return;
      }
      setView(null);
      try {
        const res = await getTransaction(id);
        setView(res.data ?? null);
        setMessage({ text: res.message, type: res.messageType });
        focus(res.field);
      } catch (e) {
        if (!(e instanceof ApiError)) throw e;
        if (e.status === 401) return navigate(programPath('COSGN00C'));
        setMessage({ text: e.message, type: 'ERROR' });
        focus(e.response.field);
      }
    },
    [focus, navigate],
  );

  useEffect(() => {
    if (nav.tranId) void fetchTran(nav.tranId);
  }, [nav.tranId, fetchTran]);

  const onEnter = (e: FormEvent) => {
    e.preventDefault();
    void fetchTran(tranId);
  };

  const pfKeys = [
    { key: 'F3', label: 'Back', onPress: () => navigate(programPath(nav.from ?? 'COMEN01C')) },
    {
      key: 'F4',
      label: 'Clear',
      onPress: () => {
        setTranId('');
        setView(null);
        setMessage({});
        focus('tranId');
      },
    },
    { key: 'F5', label: 'Browse Tran.', onPress: () => navigate(programPath('COTRN00C'), { state: { from: 'COTRN01C' } }) },
  ];

  return (
    <Screen tranId="CT01" program="COTRN01C" title="View Transaction" message={message.text} messageType={message.type} pfKeys={pfKeys}>
      <form onSubmit={onEnter} aria-label="View Transaction">
        <div className="trn-key">
          <label htmlFor="tranId">Enter Tran ID:</label>
          <input id="tranId" ref={register('tranId')} maxLength={16} value={tranId} onChange={(e) => setTranId(e.target.value)} />
        </div>
        <hr className="trn-rule" />
        <dl className="trn-fields">
          {FIELDS.map(([name, label]) => (
            <div key={name} style={{ display: 'contents' }}>
              <dt>{label}</dt>
              <dd className="trn-value" data-testid={`view-${name}`}>
                {view?.[name] ?? ''}
              </dd>
            </div>
          ))}
        </dl>
        <button type="submit">ENTER=Fetch</button>
      </form>
    </Screen>
  );
}
