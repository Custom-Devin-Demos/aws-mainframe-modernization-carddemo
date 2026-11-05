import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { PROGRAM_PATHS, programPath } from '../../programs';
import { cardPath, type CardDetailView } from './types';
import { useFieldFocus } from './useFieldFocus';
import './cards.css';

const PROMPT_FOR_INPUT = 'Please enter Account and Card Number';

/** COCRDSLC / COCRDSL: view one credit card. Coming from the list, the keys are protected. */
export function CardViewPage() {
  const navigate = useNavigate();
  const query = new URLSearchParams(useLocation().search);
  const fromList = Boolean(query.get('acctId') && query.get('cardNum'));
  const [acctId, setAcctId] = useState(query.get('acctId') ?? '');
  const [cardNum, setCardNum] = useState(query.get('cardNum') ?? '');
  const [card, setCard] = useState<CardDetailView | undefined>();
  const [info, setInfo] = useState<string | undefined>(PROMPT_FOR_INPUT);
  const [message, setMessage] = useState<string | undefined>();
  const [messageType, setMessageType] = useState<MessageType | undefined>();
  const [field, setField] = useState<string | undefined>(fromList ? undefined : 'acctId');
  const [version, setVersion] = useState(0);
  useFieldFocus(field, version);

  const search = useCallback(
    async (acct: string, num: string) => {
      let res: ScreenResponse<CardDetailView>;
      try {
        res = await api.get<CardDetailView>(`${cardPath(num)}?acctId=${encodeURIComponent(acct.trim())}`);
      } catch (e) {
        if (!(e instanceof ApiError)) throw e;
        res = (e as ApiError<CardDetailView>).response;
        if (e.status === 401 && res.nextProgram) {
          navigate(programPath(res.nextProgram));
          return;
        }
      }
      const ok = res.messageType !== 'ERROR';
      setCard(ok ? res.data : undefined);
      setInfo(res.data?.infoMessage);
      setMessage(ok ? undefined : res.message);
      setMessageType(res.messageType);
      setField(res.field);
      setVersion((v) => v + 1);
    },
    [navigate],
  );

  useEffect(() => {
    if (fromList) void search(acctId, cardNum);
  }, []);

  const onEnter = (e?: FormEvent) => {
    e?.preventDefault();
    void search(acctId, cardNum);
  };

  return (
    <Screen
      tranId="CCDL"
      program="COCRDSLC"
      title="View Credit Card Detail"
      message={message}
      messageType={messageType}
      pfKeys={[
        { key: 'Enter', label: 'Search Cards', onPress: () => onEnter() },
        {
          key: 'F3',
          label: 'Exit',
          onPress: () => navigate(fromList ? PROGRAM_PATHS.COCRDLIC : PROGRAM_PATHS.COMEN01C),
        },
      ]}
    >
      <form onSubmit={onEnter}>
        <div className="cards-form">
          <label htmlFor="acctId">Account Number    :</label>
          <input
            id="acctId"
            maxLength={11}
            readOnly={fromList}
            value={acctId}
            onChange={(e) => setAcctId(e.target.value)}
          />
          <label htmlFor="cardNum">Card Number       :</label>
          <input
            id="cardNum"
            maxLength={16}
            readOnly={fromList}
            value={cardNum}
            onChange={(e) => setCardNum(e.target.value)}
          />
          <label htmlFor="embossedName">Name on card      :</label>
          <input id="embossedName" readOnly value={card?.embossedName ?? ''} />
          <label htmlFor="activeStatus">Card Active Y/N   :</label>
          <input id="activeStatus" readOnly value={card?.activeStatus ?? ''} />
          <label htmlFor="expiryMonth">Expiry Date       :</label>
          <span>
            <input id="expiryMonth" aria-label="Expiry month" readOnly size={2} value={card?.expiryMonth ?? ''} />/
            <input id="expiryYear" aria-label="Expiry year" readOnly size={4} value={card?.expiryYear ?? ''} />
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
