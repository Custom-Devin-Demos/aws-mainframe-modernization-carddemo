import { api } from '../../api/client';
import type { KeysetPage } from '../../api/types';

/** COTRN00 list row (TRNIDnn / TDATEnn / TDESCnn / TAMTnnn). */
export interface TransactionListRow {
  tranId: string;
  date: string;
  description: string;
  amount: string;
}

/** COTRN01 output fields. */
export interface TransactionView {
  tranId: string;
  cardNum: string;
  typeCd: string;
  catCd: string;
  source: string;
  amount: string;
  description: string;
  origDate: string;
  procDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
}

/** COTRN02 input fields. */
export interface TransactionForm {
  acctId: string;
  cardNum: string;
  typeCd: string;
  catCd: string;
  source: string;
  description: string;
  amount: string;
  origDate: string;
  procDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  confirm: string;
}

export interface ListQuery {
  startTranId?: string;
  after?: string;
  before?: string;
  page?: number;
  selection?: string;
  selectedTranId?: string;
}

/** Location state passed between the transaction screens (CDEMO-CT0x-TRN-SELECTED / CDEMO-FROM-PROGRAM). */
export interface TransactionNavState {
  tranId?: string;
  from?: string;
}

export function listTransactions(q: ListQuery) {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(q)) {
    if (v !== undefined) params.set(k, String(v));
  }
  const qs = params.toString();
  return api.get<KeysetPage<TransactionListRow>>(`/api/transactions${qs ? `?${qs}` : ''}`);
}

export function getTransaction(tranId: string) {
  return api.get<TransactionView>(`/api/transactions/${encodeURIComponent(tranId)}`);
}

export function addTransaction(form: TransactionForm) {
  return api.post<{ tranId: string }>('/api/transactions', form);
}

export function copyLastTransaction(form: TransactionForm) {
  return api.post<{ tranId: string }>('/api/transactions/copy-last', form);
}
