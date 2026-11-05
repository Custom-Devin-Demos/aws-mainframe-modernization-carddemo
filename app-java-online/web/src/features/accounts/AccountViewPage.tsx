import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import type { MessageType } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { fetchAccount, type AccountDetails } from './api';
import { useFieldFocus } from './useFieldFocus';
import './accounts.css';

const fmtMoney = (n?: number) =>
  n === undefined ? '' : n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** CUST-SSN shown as 999-99-9999 (1200-SETUP-SCREEN-VARS). */
const fmtSsn = (s?: string) => (s ? `${s.slice(0, 3)}-${s.slice(3, 5)}-${s.slice(5, 9)}` : '');

function Field({ label, value }: { label: string; value?: string }) {
  return (
    <div className="acct-row">
      <label>{label}</label>
      <span className="acct-value" aria-label={label.replace(/\s+/g, ' ').replace(/\s*:$/, '').trim()}>
        {value ?? ''}
      </span>
    </div>
  );
}

/** COACTVWC (CAVW): View Account. ENTER reads the account, PF3 returns to the menu. */
export function AccountViewPage() {
  const navigate = useNavigate();
  const { formRef, focus } = useFieldFocus();
  const [acctId, setAcctId] = useState('');
  const [data, setData] = useState<AccountDetails | null>(null);
  const [message, setMessage] = useState<string>();
  const [messageType, setMessageType] = useState<MessageType>();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      const res = await fetchAccount(acctId);
      setData(res.data ?? null);
      setMessage(res.message);
      setMessageType(res.messageType);
    } catch (err) {
      if (!(err instanceof ApiError)) throw err;
      if (err.status === 401) {
        navigate(programPath(err.response.nextProgram ?? 'COSGN00C'));
        return;
      }
      setData((err.response.data as AccountDetails | undefined) ?? null);
      setMessage(err.message);
      setMessageType('ERROR');
      focus(err.response.field);
    }
  }

  return (
    <Screen
      tranId="CAVW"
      program="COACTVWC"
      title="View Account"
      message={message}
      messageType={messageType}
      pfKeys={[{ key: 'F3', label: 'Exit', onPress: () => navigate(programPath('COMEN01C')) }]}
    >
      <form ref={formRef} onSubmit={onSubmit} aria-label="View Account">
        <div className="acct-row">
          <label htmlFor="acctId">Account Number :</label>
          <input
            id="acctId"
            name="acctId"
            maxLength={11}
            autoFocus
            value={acctId}
            onChange={(e) => setAcctId(e.target.value)}
          />
          <button type="submit">ENTER</button>
          <Field label="Active Y/N:" value={data?.activeStatus} />
        </div>
        <div className="acct-grid">
          <Field label="Opened:" value={data?.openDate} />
          <Field label="Credit Limit        :" value={fmtMoney(data?.creditLimit)} />
          <Field label="Expiry:" value={data?.expirationDate} />
          <Field label="Cash credit Limit   :" value={fmtMoney(data?.cashCreditLimit)} />
          <Field label="Reissue:" value={data?.reissueDate} />
          <Field label="Current Balance     :" value={fmtMoney(data?.currBal)} />
          <span />
          <Field label="Current Cycle Credit:" value={fmtMoney(data?.currCycCredit)} />
          <Field label="Account Group:" value={data?.groupId} />
          <Field label="Current Cycle Debit :" value={fmtMoney(data?.currCycDebit)} />
        </div>
        <div className="acct-section">Customer Details</div>
        <div className="acct-grid">
          <Field label="Customer id  :" value={data?.custId} />
          <Field label="SSN:" value={fmtSsn(data?.ssn)} />
          <Field label="Date of birth:" value={data?.dob} />
          <Field label="FICO Score:" value={data?.ficoScore} />
          <Field label="First Name" value={data?.firstName} />
          <Field label="Middle Name:" value={data?.middleName} />
          <Field label="Last Name :" value={data?.lastName} />
          <span />
          <Field label="Address:" value={data?.addrLine1} />
          <Field label="State" value={data?.state} />
          <Field label="Address line 2:" value={data?.addrLine2} />
          <Field label="Zip" value={data?.zip} />
          <Field label="City" value={data?.city} />
          <Field label="Country" value={data?.country} />
          <Field label="Phone 1:" value={data?.phone1} />
          <Field label="Government Issued Id Ref    :" value={data?.govtId} />
          <Field label="Phone 2:" value={data?.phone2} />
          <Field label="EFT Account Id:" value={data?.eftAccountId} />
          <Field label="Primary Card Holder Y/N:" value={data?.primaryCardHolder} />
        </div>
        <div className="acct-info">Enter or update id of account to display</div>
      </form>
    </Screen>
  );
}
