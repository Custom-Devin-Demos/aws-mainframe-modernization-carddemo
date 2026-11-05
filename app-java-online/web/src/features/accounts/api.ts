import { api } from '../../api/client';

/** Mirrors com.carddemo.online.account.AccountDetails (COACTVW / COACTUP output fields). */
export interface AccountDetails {
  acctId: string;
  activeStatus: string;
  openDate?: string;
  expirationDate?: string;
  reissueDate?: string;
  creditLimit: number;
  cashCreditLimit: number;
  currBal: number;
  currCycCredit: number;
  currCycDebit: number;
  groupId: string;
  cardNum: string;
  custId?: string;
  ssn?: string;
  dob?: string;
  ficoScore?: string;
  firstName?: string;
  middleName?: string;
  lastName?: string;
  addrLine1?: string;
  addrLine2?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
  phone1?: string;
  phone2?: string;
  govtId?: string;
  eftAccountId?: string;
  primaryCardHolder?: string;
  version?: string;
}

/** Mirrors com.carddemo.online.account.AccountUpdateRequest (COACTUP input fields). */
export interface AccountUpdateForm {
  acctStatus: string;
  openYear: string;
  openMonth: string;
  openDay: string;
  creditLimit: string;
  expiryYear: string;
  expiryMonth: string;
  expiryDay: string;
  cashCreditLimit: string;
  reissueYear: string;
  reissueMonth: string;
  reissueDay: string;
  currBal: string;
  currCycCredit: string;
  currCycDebit: string;
  groupId: string;
  ssn1: string;
  ssn2: string;
  ssn3: string;
  dobYear: string;
  dobMonth: string;
  dobDay: string;
  ficoScore: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addrLine1: string;
  addrLine2: string;
  city: string;
  state: string;
  zip: string;
  country: string;
  phone1a: string;
  phone1b: string;
  phone1c: string;
  phone2a: string;
  phone2b: string;
  phone2c: string;
  govtId: string;
  eftAccountId: string;
  primaryCardHolder: string;
  version: string;
}

export function fetchAccount(acctId: string, mode: 'view' | 'update' = 'view') {
  const path = acctId.trim() ? `/api/accounts/${encodeURIComponent(acctId)}` : '/api/accounts';
  return api.get<AccountDetails>(mode === 'update' ? `${path}?mode=update` : path);
}

export function updateAccount(acctId: string, form: AccountUpdateForm, validateOnly: boolean) {
  return api.put<AccountDetails>(
    `/api/accounts/${encodeURIComponent(acctId)}?validateOnly=${validateOnly}`,
    form,
  );
}

function dateParts(iso?: string): [string, string, string] {
  return iso ? [iso.slice(0, 4), iso.slice(5, 7), iso.slice(8, 10)] : ['', '', ''];
}

/** CUST-PHONE-NUM '(aaa)bbb-cccc' -> parts at (2:3), (6:3), (10:4), as 3202-SHOW-ORIGINAL-VALUES. */
export function phoneParts(phone?: string): [string, string, string] {
  const p = (phone ?? '').padEnd(15, ' ');
  return [p.slice(1, 4).trim(), p.slice(5, 8).trim(), p.slice(9, 13).trim()];
}

export const money = (n: number | undefined) => (n ?? 0).toFixed(2);

/** 3202-SHOW-ORIGINAL-VALUES: fetched record -> map input fields. */
export function toForm(d: AccountDetails): AccountUpdateForm {
  const [openYear, openMonth, openDay] = dateParts(d.openDate);
  const [expiryYear, expiryMonth, expiryDay] = dateParts(d.expirationDate);
  const [reissueYear, reissueMonth, reissueDay] = dateParts(d.reissueDate);
  const [dobYear, dobMonth, dobDay] = dateParts(d.dob);
  const [phone1a, phone1b, phone1c] = phoneParts(d.phone1);
  const [phone2a, phone2b, phone2c] = phoneParts(d.phone2);
  const ssn = d.ssn ?? '';
  return {
    acctStatus: d.activeStatus ?? '',
    openYear,
    openMonth,
    openDay,
    creditLimit: money(d.creditLimit),
    expiryYear,
    expiryMonth,
    expiryDay,
    cashCreditLimit: money(d.cashCreditLimit),
    reissueYear,
    reissueMonth,
    reissueDay,
    currBal: money(d.currBal),
    currCycCredit: money(d.currCycCredit),
    currCycDebit: money(d.currCycDebit),
    groupId: d.groupId ?? '',
    ssn1: ssn.slice(0, 3),
    ssn2: ssn.slice(3, 5),
    ssn3: ssn.slice(5, 9),
    dobYear,
    dobMonth,
    dobDay,
    ficoScore: d.ficoScore ?? '',
    firstName: d.firstName ?? '',
    middleName: d.middleName ?? '',
    lastName: d.lastName ?? '',
    addrLine1: d.addrLine1 ?? '',
    addrLine2: d.addrLine2 ?? '',
    city: d.city ?? '',
    state: d.state ?? '',
    zip: d.zip ?? '',
    country: d.country ?? '',
    phone1a,
    phone1b,
    phone1c,
    phone2a,
    phone2b,
    phone2c,
    govtId: d.govtId ?? '',
    eftAccountId: d.eftAccountId ?? '',
    primaryCardHolder: d.primaryCardHolder ?? '',
    version: d.version ?? '',
  };
}
