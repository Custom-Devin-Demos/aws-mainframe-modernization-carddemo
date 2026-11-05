import type { KeysetPage } from '../../api/types';

/** Mirrors com.carddemo.online.card.CardRow. */
export interface CardRow {
  acctId: string;
  cardNum: string;
  activeStatus: string;
}

/** Mirrors com.carddemo.online.card.CardListView (COCRDLI). */
export interface CardListView {
  page: KeysetPage<CardRow>;
  infoMessage?: string;
  errorRows?: number[];
  selectedAcctId?: string;
  selectedCardNum?: string;
}

/** Mirrors com.carddemo.online.card.CardDetailView (COCRDSL / COCRDUP fields). */
export interface CardDetailView {
  acctId?: string;
  cardNum?: string;
  embossedName?: string;
  activeStatus?: string;
  expiryMonth?: string;
  expiryYear?: string;
  expiryDay?: string;
  infoMessage?: string;
}

/** CCUP-OLD-CARDDATA. */
export interface CardOriginal {
  embossedName?: string;
  activeStatus?: string;
  expiryYear?: string;
  expiryMonth?: string;
  expiryDay?: string;
}

export type CardUpdateState =
  | 'DETAILS_NOT_FETCHED'
  | 'SHOW_DETAILS'
  | 'CHANGES_NOT_OK'
  | 'CHANGES_OK_NOT_CONFIRMED'
  | 'CHANGES_OKAYED_AND_DONE'
  | 'CHANGES_FAILED';

/** Mirrors com.carddemo.online.card.CardUpdateView. */
export interface CardUpdateView {
  card?: CardDetailView;
  original?: CardOriginal | null;
  state: CardUpdateState;
}

/** Path segment for a card number; a blank number is sent as an encoded space. */
export function cardPath(cardNum: string): string {
  return `/api/cards/${encodeURIComponent(cardNum.trim() || ' ')}`;
}

/** Query string carrying CDEMO-ACCT-ID / CDEMO-CARD-NUM between the card screens. */
export function cardQuery(acctId?: string, cardNum?: string): string {
  const q = new URLSearchParams();
  if (acctId) q.set('acctId', acctId);
  if (cardNum) q.set('cardNum', cardNum);
  return q.toString();
}
