import { useCallback, useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen, type PfKey } from '../../components/Screen';
import { PROGRAM_PATHS, programPath } from '../../programs';
import { cardPath, type CardOriginal, type CardUpdateState, type CardUpdateView } from './types';
import { useFieldFocus } from './useFieldFocus';
import './cards.css';

const PROMPT_FOR_INPUT = 'Please enter Account and Card Number';

interface Fields {
  embossedName: string;
  activeStatus: string;
  expiryMonth: string;
  expiryYear: string;
}

const EMPTY: Fields = { embossedName: '', activeStatus: '', expiryMonth: '', expiryYear: '' };

/** COCRDUPC / COCRDUP: search a card, edit name/status/expiry, ENTER validates, F5 saves, F12 cancels. */
export function CardUpdatePage() {
  const navigate = useNavigate();
  const query = new URLSearchParams(useLocation().search);
  const fromList = Boolean(query.get('acctId') && query.get('cardNum'));
  const [acctId, setAcctId] = useState(query.get('acctId') ?? '');
  const [cardNum, setCardNum] = useState(query.get('cardNum') ?? '');
  const [fields, setFields] = useState<Fields>(EMPTY);
  const [original, setOriginal] = useState<CardOriginal | null>(null);
  const [state, setState] = useState<CardUpdateState>('DETAILS_NOT_FETCHED');
  const [info, setInfo] = useState<string | undefined>(PROMPT_FOR_INPUT);
  const [message, setMessage] = useState<string | undefined>();
  const [messageType, setMessageType] = useState<MessageType | undefined>();
  const [field, setField] = useState<string | undefined>(fromList ? undefined : 'acctId');
  const [version, setVersion] = useState(0);
  useFieldFocus(field, version);

  const send = useCallback(
    async (body: Record<string, unknown>, num: string) => {
      let res: ScreenResponse<CardUpdateView>;
      try {
        res = await api.put<CardUpdateView>(cardPath(num), body);
      } catch (e) {
        if (!(e instanceof ApiError)) throw e;
        res = (e as ApiError<CardUpdateView>).response;
        if (e.status === 401 && res.nextProgram) {
          navigate(programPath(res.nextProgram));
          return;
        }
      }
      const view = res.data;
      if (view) {
        setState(view.state);
        if (view.original !== undefined) setOriginal(view.original ?? null);
        const c = view.card;
        if (c && view.state !== 'DETAILS_NOT_FETCHED') {
          setFields({
            embossedName: c.embossedName ?? '',
            activeStatus: c.activeStatus ?? '',
            expiryMonth: c.expiryMonth ?? '',
            expiryYear: c.expiryYear ?? '',
          });
        }
        setInfo(c?.infoMessage);
      }
      setMessage(res.messageType === 'INFO' ? undefined : res.message);
      setMessageType(res.messageType);
      setField(res.field ?? (view && view.state !== 'DETAILS_NOT_FETCHED' ? 'embossedName' : undefined));
      setVersion((v) => v + 1);
    },
    [navigate],
  );

  const search = useCallback(
    (acct: string, num: string) => send({ acctId: acct, original: null, confirm: false }, num),
    [send],
  );

  useEffect(() => {
    if (fromList) void search(acctId, cardNum);
  }, []);

  const fetched = original !== null && state !== 'DETAILS_NOT_FETCHED';
  const editable = fetched && state !== 'CHANGES_OKAYED_AND_DONE';

  const submitChanges = (confirm: boolean) =>
    void send({ acctId, ...fields, original, confirm }, cardNum);

  const reset = () => {
    setAcctId('');
    setCardNum('');
    setFields(EMPTY);
    setOriginal(null);
    setState('DETAILS_NOT_FETCHED');
    setInfo(PROMPT_FOR_INPUT);
    setMessage(undefined);
    setField('acctId');
    setVersion((v) => v + 1);
  };

  const onEnter = (e?: FormEvent) => {
    e?.preventDefault();
    if (state === 'CHANGES_OKAYED_AND_DONE' || state === 'CHANGES_FAILED') {
      // 2000-DECIDE-ACTION: after a save, ENTER returns to the list or starts a new search
      if (fromList) navigate(PROGRAM_PATHS.COCRDLIC);
      else reset();
      return;
    }
    if (!fetched) void search(acctId, cardNum);
    else submitChanges(false);
  };

  const pfKeys: PfKey[] = [
    { key: 'Enter', label: 'Process', onPress: () => onEnter() },
    { key: 'F3', label: 'Exit', onPress: () => navigate(fromList ? PROGRAM_PATHS.COCRDLIC : PROGRAM_PATHS.COMEN01C) },
  ];
  if (state === 'CHANGES_OK_NOT_CONFIRMED') {
    pfKeys.push({ key: 'F5', label: 'Save', onPress: () => submitChanges(true) });
  }
  if (fetched) {
    // PF12: re-read the record, discarding the edits
    pfKeys.push({ key: 'F12', label: 'Cancel', onPress: () => void search(acctId, cardNum) });
  }

  const set = (k: keyof Fields) => (e: ChangeEvent<HTMLInputElement>) =>
    setFields((f) => ({ ...f, [k]: e.target.value }));

  return (
    <Screen
      tranId="CCUP"
      program="COCRDUPC"
      title="Update Credit Card Details"
      message={message}
      messageType={messageType}
      pfKeys={pfKeys}
    >
      <form onSubmit={onEnter}>
        <div className="cards-form">
          <label htmlFor="acctId">Account Number    :</label>
          <input
            id="acctId"
            maxLength={11}
            readOnly={fromList || fetched}
            value={acctId}
            onChange={(e) => setAcctId(e.target.value)}
          />
          <label htmlFor="cardNum">Card Number       :</label>
          <input
            id="cardNum"
            maxLength={16}
            readOnly={fromList || fetched}
            value={cardNum}
            onChange={(e) => setCardNum(e.target.value)}
          />
          <label htmlFor="embossedName">Name on card      :</label>
          <input id="embossedName" maxLength={50} readOnly={!editable} value={fields.embossedName} onChange={set('embossedName')} />
          <label htmlFor="activeStatus">Card Active Y/N   :</label>
          <input id="activeStatus" maxLength={1} readOnly={!editable} value={fields.activeStatus} onChange={set('activeStatus')} />
          <label htmlFor="expiryMonth">Expiry Date       :</label>
          <span>
            <input
              id="expiryMonth"
              aria-label="Expiry month"
              maxLength={2}
              size={2}
              readOnly={!editable}
              value={fields.expiryMonth}
              onChange={set('expiryMonth')}
            />
            /
            <input
              id="expiryYear"
              aria-label="Expiry year"
              maxLength={4}
              size={4}
              readOnly={!editable}
              value={fields.expiryYear}
              onChange={set('expiryYear')}
            />
          </span>
        </div>
        <div className="cards-info">{info ?? ''}</div>
        <button type="submit" hidden>
          Enter
        </button>
      </form>
    </Screen>
  );
}
