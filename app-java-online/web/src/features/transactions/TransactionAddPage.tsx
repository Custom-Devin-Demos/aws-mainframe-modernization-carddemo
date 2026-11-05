import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { addTransaction, copyLastTransaction, type TransactionForm, type TransactionNavState } from './api';
import { useFieldFocus } from './useFieldFocus';
import './transactions.css';

const EMPTY: TransactionForm = {
  acctId: '',
  cardNum: '',
  typeCd: '',
  catCd: '',
  source: '',
  description: '',
  amount: '',
  origDate: '',
  procDate: '',
  merchantId: '',
  merchantName: '',
  merchantCity: '',
  merchantZip: '',
  confirm: '',
};

/** COTRN02 input fields: name, label, BMS length, format hint. */
const DATA_FIELDS: Array<[keyof TransactionForm, string, number, string?]> = [
  ['typeCd', 'Type CD:', 2],
  ['catCd', 'Category CD:', 4],
  ['source', 'Source:', 10],
  ['description', 'Description:', 60],
  ['amount', 'Amount:', 12, '(-99999999.99)'],
  ['origDate', 'Orig Date:', 10, '(YYYY-MM-DD)'],
  ['procDate', 'Proc Date:', 10, '(YYYY-MM-DD)'],
  ['merchantId', 'Merchant ID:', 9],
  ['merchantName', 'Merchant Name:', 30],
  ['merchantCity', 'Merchant City:', 25],
  ['merchantZip', 'Merchant Zip:', 10],
];

/** COTRN02C (CT02) - Add Transaction. */
export function TransactionAddPage() {
  const navigate = useNavigate();
  const nav = (useLocation().state ?? {}) as TransactionNavState;
  const { focus, register } = useFieldFocus('acctId');
  const [form, setForm] = useState<TransactionForm>(EMPTY);
  const [message, setMessage] = useState<{ text?: string; type?: MessageType }>({});

  const submit = async (call: (f: TransactionForm) => Promise<ScreenResponse<{ tranId: string }>>) => {
    try {
      const res = await call(form);
      // WRITE-TRANSACT-FILE NORMAL: INITIALIZE-ALL-FIELDS, green message with the new Tran ID
      setForm(EMPTY);
      setMessage({ text: res.message, type: res.messageType });
      focus(res.field);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      if (e.status === 401) return navigate(programPath('COSGN00C'));
      const data = e.response.data as Partial<TransactionForm> | undefined;
      if (data) setForm({ ...EMPTY, ...data });
      setMessage({ text: e.message, type: 'ERROR' });
      focus(e.response.field);
    }
  };

  const onEnter = (e: FormEvent) => {
    e.preventDefault();
    void submit(addTransaction);
  };

  const input = (name: keyof TransactionForm, maxLength: number) => (
    <input
      id={name}
      ref={register(name)}
      maxLength={maxLength}
      value={form[name]}
      onChange={(e) => setForm((f) => ({ ...f, [name]: e.target.value }))}
    />
  );

  const pfKeys = [
    { key: 'F3', label: 'Back', onPress: () => navigate(programPath(nav.from ?? 'COMEN01C')) },
    {
      key: 'F4',
      label: 'Clear',
      onPress: () => {
        setForm(EMPTY);
        setMessage({});
        focus('acctId');
      },
    },
    { key: 'F5', label: 'Copy Last Tran.', onPress: () => void submit(copyLastTransaction) },
  ];

  return (
    <Screen tranId="CT02" program="COTRN02C" title="Add Transaction" message={message.text} messageType={message.type} pfKeys={pfKeys}>
      <form onSubmit={onEnter} aria-label="Add Transaction">
        <div className="trn-key">
          <label htmlFor="acctId">Enter Acct #:</label>
          {input('acctId', 11)}
          <span>(or)</span>
          <label htmlFor="cardNum">Card #:</label>
          {input('cardNum', 16)}
        </div>
        <hr className="trn-rule" />
        <div className="trn-fields">
          {DATA_FIELDS.map(([name, label, len, hint]) => (
            <div key={name} style={{ display: 'contents' }}>
              <label htmlFor={name}>{label}</label>
              <span>
                {input(name, len)}
                {hint && <span className="trn-hint">{hint}</span>}
              </span>
            </div>
          ))}
        </div>
        <div className="trn-confirm">
          <label htmlFor="confirm">You are about to add this transaction. Please confirm :</label> {input('confirm', 1)}{' '}
          <span className="trn-hint">(Y/N)</span>
        </div>
        <button type="submit">ENTER=Continue</button>
      </form>
    </Screen>
  );
}
