import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import type { KeysetPage, MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { listTransactions, type ListQuery, type TransactionListRow, type TransactionNavState } from './api';
import { useFieldFocus } from './useFieldFocus';
import './transactions.css';

const ROWS = 10;
const emptySelections = () => Array.from({ length: ROWS }, () => '');

/** COTRN00C (CT00) - List Transactions. */
export function TransactionListPage() {
  const navigate = useNavigate();
  const { focus, register } = useFieldFocus('startTranId');
  const [startTranId, setStartTranId] = useState('');
  const [rows, setRows] = useState<TransactionListRow[]>([]);
  const [page, setPage] = useState<Pick<KeysetPage<unknown>, 'pageNumber' | 'firstKey' | 'lastKey'>>({ pageNumber: 0 });
  const [selections, setSelections] = useState<string[]>(emptySelections);
  const [message, setMessage] = useState<{ text?: string; type?: MessageType }>({});

  const run = useCallback(
    async (query: ListQuery, selectedTranId?: string) => {
      let res: ScreenResponse<KeysetPage<TransactionListRow>>;
      try {
        res = await listTransactions(query);
      } catch (e) {
        if (!(e instanceof ApiError)) throw e;
        if (e.status === 401) return navigate(programPath('COSGN00C'));
        setMessage({ text: e.message, type: 'ERROR' });
        focus(e.response.field);
        return;
      }
      if (res.nextProgram) {
        const state: TransactionNavState = { tranId: selectedTranId, from: 'COTRN00C' };
        navigate(programPath(res.nextProgram), { state });
        return;
      }
      if (res.data) {
        setRows(res.data.items);
        setPage({ pageNumber: res.data.pageNumber, firstKey: res.data.firstKey, lastKey: res.data.lastKey });
        setSelections(emptySelections());
        if (query.before === undefined) setStartTranId('');
      }
      setMessage({ text: res.message, type: res.messageType });
      focus(res.field);
    },
    [navigate, focus],
  );

  useEffect(() => {
    void run({});
  }, [run]);

  const onEnter = (e: FormEvent) => {
    e.preventDefault();
    const idx = selections.findIndex((s) => s.trim() !== '');
    const query: ListQuery = { startTranId };
    let selectedTranId: string | undefined;
    if (idx >= 0) {
      selectedTranId = rows[idx]?.tranId ?? '';
      query.selection = selections[idx];
      query.selectedTranId = selectedTranId;
    }
    void run(query, selectedTranId);
  };

  const pfKeys = [
    { key: 'F3', label: 'Back', onPress: () => navigate(programPath('COMEN01C')) },
    { key: 'F7', label: 'Backward', onPress: () => void run({ before: page.firstKey ?? '', page: page.pageNumber }) },
    { key: 'F8', label: 'Forward', onPress: () => void run({ after: page.lastKey ?? '', page: page.pageNumber }) },
  ];

  return (
    <Screen tranId="CT00" program="COTRN00C" title="List Transactions" message={message.text} messageType={message.type} pfKeys={pfKeys}>
      <form onSubmit={onEnter} aria-label="List Transactions">
        <div className="trn-search">
          <span>
            <label htmlFor="startTranId">Search Tran ID:</label>{' '}
            <input
              id="startTranId"
              ref={register('startTranId')}
              maxLength={16}
              value={startTranId}
              onChange={(e) => setStartTranId(e.target.value)}
            />
          </span>
          <span>Page: {page.pageNumber || ''}</span>
        </div>
        <table className="trn-table">
          <thead>
            <tr>
              <th>Sel</th>
              <th>Transaction ID</th>
              <th>Date</th>
              <th>Description</th>
              <th className="trn-amount">Amount</th>
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: ROWS }, (_, i) => {
              const row = rows[i];
              return (
                <tr key={i}>
                  <td>
                    <input
                      aria-label={`Select row ${i + 1}`}
                      maxLength={1}
                      value={selections[i]}
                      onChange={(e) => {
                        const next = [...selections];
                        next[i] = e.target.value;
                        setSelections(next);
                      }}
                    />
                  </td>
                  <td>{row?.tranId}</td>
                  <td>{row?.date}</td>
                  <td>{row?.description}</td>
                  <td className="trn-amount">{row?.amount}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <p>Type &apos;S&apos; to View Transaction details from the list</p>
        <button type="submit">ENTER=Continue</button>
      </form>
    </Screen>
  );
}
