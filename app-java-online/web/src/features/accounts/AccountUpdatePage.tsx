import { useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen, type PfKey } from '../../components/Screen';
import { programPath } from '../../programs';
import { fetchAccount, toForm, updateAccount, type AccountDetails, type AccountUpdateForm } from './api';
import { useFieldFocus } from './useFieldFocus';
import './accounts.css';

/**
 * ACUP-CHANGE-ACTION: details not fetched -> shown (editable) -> validated (PF5 allowed) -> saved.
 */
type Phase = 'search' | 'edit' | 'confirm' | 'done';

/** 3250-SETUP-INFOMSG. */
const INFO: Record<Phase, string> = {
  search: 'Enter or update id of account to update',
  edit: 'Update account details presented above.',
  confirm: 'Changes validated.Press F5 to save',
  done: 'Changes committed to database',
};

type FieldName = keyof AccountUpdateForm;

/** COACTUPC (CAUP): Update Account. ENTER fetches/validates, PF5 saves, PF12 cancels, PF3 exits. */
export function AccountUpdatePage() {
  const navigate = useNavigate();
  const { formRef, focus } = useFieldFocus();
  const [acctId, setAcctId] = useState('');
  const [custId, setCustId] = useState('');
  const [form, setForm] = useState<AccountUpdateForm | null>(null);
  const [phase, setPhase] = useState<Phase>('search');
  const [message, setMessage] = useState<string>();
  const [messageType, setMessageType] = useState<MessageType>();

  function show(res: ScreenResponse<AccountDetails>, type?: MessageType) {
    setMessage(res.message);
    setMessageType(type ?? res.messageType);
  }

  function load(d: AccountDetails) {
    setForm(toForm(d));
    setCustId(d.custId ?? '');
  }

  function handleError(err: unknown) {
    if (!(err instanceof ApiError)) throw err;
    if (err.status === 401) {
      navigate(programPath(err.response.nextProgram ?? 'COSGN00C'));
      return;
    }
    const res = err.response as ScreenResponse<AccountDetails>;
    show(res, 'ERROR');
    // DATA-WAS-CHANGED-BEFORE-UPDATE re-shows the current record for review
    if (err.status === 409 && res.data) {
      load(res.data);
      setPhase('edit');
    }
    focus(res.field);
  }

  async function fetchDetails() {
    try {
      const res = await fetchAccount(acctId, 'update');
      if (res.data) load(res.data);
      setPhase('edit');
      show(res, 'INFO');
      focus('acctStatus');
    } catch (err) {
      handleError(err);
    }
  }

  async function save(validateOnly: boolean) {
    if (!form) return;
    try {
      const res = await updateAccount(acctId, form, validateOnly);
      if (res.data) load(res.data);
      show(res);
      if (res.field) {
        // NO-CHANGES-DETECTED
        focus(res.field);
        return;
      }
      setPhase(validateOnly ? 'confirm' : 'done');
    } catch (err) {
      handleError(err);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (phase === 'search' || phase === 'done') void fetchDetails();
    else void save(true);
  }

  function setField(name: FieldName, value: string) {
    setForm((f) => (f ? { ...f, [name]: value } : f));
    if (phase === 'confirm') setPhase('edit');
  }

  const pfKeys: PfKey[] = [{ key: 'F3', label: 'Exit', onPress: () => navigate(programPath('COMEN01C')) }];
  // PF5 only valid with ACUP-CHANGES-OK-NOT-CONFIRMED, PF12 only once details are fetched
  if (phase === 'confirm') pfKeys.push({ key: 'F5', label: 'Save', onPress: () => void save(false) });
  if (phase !== 'search') pfKeys.push({ key: 'F12', label: 'Cancel', onPress: () => void fetchDetails() });

  const editable = phase === 'edit' || phase === 'confirm';
  const input = (name: FieldName, label: string, size: number) => (
    <input
      name={name}
      aria-label={label}
      maxLength={size}
      size={size}
      readOnly={!editable}
      value={form?.[name] ?? ''}
      onChange={(e) => setField(name, e.target.value)}
    />
  );
  const row = (label: string, ...children: ReactNode[]) => (
    <div className="acct-row">
      <label>{label}</label>
      {children}
    </div>
  );

  return (
    <Screen
      tranId="CAUP"
      program="COACTUPC"
      title="Update Account"
      message={message}
      messageType={messageType}
      pfKeys={pfKeys}
    >
      <form ref={formRef} onSubmit={onSubmit} aria-label="Update Account">
        <div className="acct-row">
          <label htmlFor="acctId">Account Number :</label>
          <input
            id="acctId"
            name="acctId"
            maxLength={11}
            autoFocus
            readOnly={phase !== 'search' && phase !== 'done'}
            value={acctId}
            onChange={(e) => setAcctId(e.target.value)}
          />
          <button type="submit">ENTER</button>
          {row('Active Y/N:', input('acctStatus', 'Active Y/N', 1))}
        </div>
        <div className="acct-grid">
          {row('Opened :', input('openYear', 'Opened year', 4), input('openMonth', 'Opened month', 2),
            input('openDay', 'Opened day', 2))}
          {row('Credit Limit        :', input('creditLimit', 'Credit Limit', 15))}
          {row('Expiry :', input('expiryYear', 'Expiry year', 4), input('expiryMonth', 'Expiry month', 2),
            input('expiryDay', 'Expiry day', 2))}
          {row('Cash credit Limit   :', input('cashCreditLimit', 'Cash credit Limit', 15))}
          {row('Reissue:', input('reissueYear', 'Reissue year', 4), input('reissueMonth', 'Reissue month', 2),
            input('reissueDay', 'Reissue day', 2))}
          {row('Current Balance     :', input('currBal', 'Current Balance', 15))}
          <span />
          {row('Current Cycle Credit:', input('currCycCredit', 'Current Cycle Credit', 15))}
          {row('Account Group:', input('groupId', 'Account Group', 10))}
          {row('Current Cycle Debit :', input('currCycDebit', 'Current Cycle Debit', 15))}
        </div>
        <div className="acct-section">Customer Details</div>
        <div className="acct-grid">
          {row('Customer id  :', <span key="c" className="acct-value" aria-label="Customer id">{custId}</span>)}
          {row('SSN:', input('ssn1', 'SSN part 1', 3), input('ssn2', 'SSN part 2', 2), input('ssn3', 'SSN part 3', 4))}
          {row('Date of birth:', input('dobYear', 'Date of birth year', 4), input('dobMonth', 'Date of birth month', 2),
            input('dobDay', 'Date of birth day', 2))}
          {row('FICO Score:', input('ficoScore', 'FICO Score', 3))}
          {row('First Name', input('firstName', 'First Name', 25))}
          {row('Middle Name:', input('middleName', 'Middle Name', 25))}
          {row('Last Name :', input('lastName', 'Last Name', 25))}
          <span />
          {row('Address:', input('addrLine1', 'Address line 1', 50))}
          {row('State', input('state', 'State', 2))}
          {row('', input('addrLine2', 'Address line 2', 50))}
          {row('Zip', input('zip', 'Zip', 5))}
          {row('City', input('city', 'City', 50))}
          {row('Country', input('country', 'Country', 3))}
          {row('Phone 1:', input('phone1a', 'Phone 1 area code', 3), input('phone1b', 'Phone 1 prefix', 3),
            input('phone1c', 'Phone 1 line number', 4))}
          {row('Government Issued Id Ref    :', input('govtId', 'Government Issued Id Ref', 20))}
          {row('Phone 2:', input('phone2a', 'Phone 2 area code', 3), input('phone2b', 'Phone 2 prefix', 3),
            input('phone2c', 'Phone 2 line number', 4))}
          {row('EFT Account Id:', input('eftAccountId', 'EFT Account Id', 10))}
          {row('Primary Card Holder Y/N:', input('primaryCardHolder', 'Primary Card Holder Y/N', 1))}
        </div>
        <div className="acct-info" data-testid="info-message">
          {INFO[phase]}
        </div>
      </form>
    </Screen>
  );
}
