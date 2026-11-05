import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { PROGRAM_PATHS, programPath } from '../../programs';
import { cardQuery, type CardListView } from './types';
import { useFieldFocus } from './useFieldFocus';
import './cards.css';

const MAX_SCREEN_LINES = 7;
const NO_SELECTIONS = Array<string>(MAX_SCREEN_LINES).fill('');

type Nav = { start?: string; after?: string; before?: string; page?: number; selections?: string[] };

/** COCRDLIC / COCRDLI: list credit cards, 7 per page; S = view, U = update. */
export function CardListPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const initial = new URLSearchParams(location.search);
  const [acctId, setAcctId] = useState(initial.get('acctId') ?? '');
  const [cardNum, setCardNum] = useState(initial.get('cardNum') ?? '');
  const [view, setView] = useState<CardListView | undefined>();
  const [selections, setSelections] = useState<string[]>(NO_SELECTIONS);
  const [message, setMessage] = useState<string | undefined>();
  const [messageType, setMessageType] = useState<MessageType | undefined>();
  const [field, setField] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  useFieldFocus(field, version);

  const load = useCallback(
    async (filters: { acctId: string; cardNum: string }, nav: Nav = {}) => {
      const q = new URLSearchParams();
      if (filters.acctId) q.set('acctId', filters.acctId);
      if (filters.cardNum) q.set('cardNum', filters.cardNum);
      if (nav.start) q.set('start', nav.start);
      if (nav.after) q.set('after', nav.after);
      if (nav.before) q.set('before', nav.before);
      if (nav.page) q.set('page', String(nav.page));
      if (nav.selections && nav.selections.some((s) => s.trim() !== '')) q.set('selections', nav.selections.join(','));
      const qs = q.toString();
      let res: ScreenResponse<CardListView>;
      try {
        res = await api.get<CardListView>(`/api/cards${qs ? `?${qs}` : ''}`);
      } catch (e) {
        if (!(e instanceof ApiError)) throw e;
        res = (e as ApiError<CardListView>).response;
        if (e.status === 401 && res.nextProgram) {
          navigate(programPath(res.nextProgram));
          return;
        }
      }
      if (res.nextProgram && res.data?.selectedCardNum) {
        navigate(`${programPath(res.nextProgram)}?${cardQuery(res.data.selectedAcctId, res.data.selectedCardNum)}`);
        return;
      }
      if (res.data) {
        setView(res.data);
        if (!res.data.errorRows?.length) setSelections(NO_SELECTIONS);
      }
      // INFOMSG has its own line; only ERRMSG text goes to the message line.
      setMessage(res.messageType === 'INFO' ? undefined : res.message);
      setMessageType(res.messageType);
      setField(res.field);
      setVersion((v) => v + 1);
    },
    [navigate],
  );

  useEffect(() => {
    void load({ acctId, cardNum });
  }, []);

  const page = view?.page;
  const rows = page?.items ?? [];
  const errorRows = new Set(view?.errorRows ?? []);

  const onEnter = (e?: FormEvent) => {
    e?.preventDefault();
    void load({ acctId, cardNum }, { start: page?.firstKey, page: page?.pageNumber, selections });
  };
  const onBackward = () =>
    void load({ acctId, cardNum }, { before: page?.firstKey ?? ' ', page: page?.pageNumber ?? 1 });
  const onForward = () =>
    void load({ acctId, cardNum }, { after: page?.lastKey ?? ' ', start: page?.firstKey, page: page?.pageNumber ?? 1 });

  return (
    <Screen
      tranId="CCLI"
      program="COCRDLIC"
      title="List Credit Cards"
      message={message}
      messageType={messageType}
      pfKeys={[
        { key: 'F3', label: 'Exit', onPress: () => navigate(PROGRAM_PATHS.COMEN01C) },
        { key: 'F7', label: 'Backward', onPress: onBackward },
        { key: 'F8', label: 'Forward', onPress: onForward },
      ]}
    >
      <div className="cards-page">Page {page?.pageNumber ?? ''}</div>
      <form onSubmit={onEnter}>
        <div className="cards-form">
          <label htmlFor="acctId">Account Number    :</label>
          <input id="acctId" maxLength={11} value={acctId} onChange={(e) => setAcctId(e.target.value)} />
          <label htmlFor="cardNum">Credit Card Number:</label>
          <input id="cardNum" maxLength={16} value={cardNum} onChange={(e) => setCardNum(e.target.value)} />
        </div>
        <table className="cards-table">
          <thead>
            <tr>
              <th>Select</th>
              <th>Account Number</th>
              <th>Card Number</th>
              <th>Active</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr key={row.cardNum} className={errorRows.has(i + 1) ? 'cards-row-error' : undefined}>
                <td>
                  <input
                    id={`select${i + 1}`}
                    aria-label={`Select row ${i + 1}`}
                    aria-invalid={errorRows.has(i + 1)}
                    maxLength={1}
                    value={selections[i]}
                    onChange={(e) => setSelections((s) => s.map((v, j) => (j === i ? e.target.value : v)))}
                  />
                </td>
                <td>{row.acctId}</td>
                <td>{row.cardNum}</td>
                <td>{row.activeStatus}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="cards-info">{view?.infoMessage ?? ''}</div>
        <button type="submit" hidden>
          Enter
        </button>
      </form>
    </Screen>
  );
}
