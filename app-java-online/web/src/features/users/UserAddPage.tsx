import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { call, focusField, MSG_INVALID_KEY, userApi, type ScreenMessage } from './userApi';

const EMPTY = { firstName: '', lastName: '', userId: '', password: '', userType: '' };

/** COUSR01C / COUSR01 map: Add User. */
export function UserAddPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<ScreenMessage>({});
  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const add = async (e?: FormEvent) => {
    e?.preventDefault();
    const { ok, res } = await call(userApi.add(form));
    // WRITE-USER-SEC-FILE NORMAL: INITIALIZE-ALL-FIELDS
    if (ok) setForm(EMPTY);
    setMessage({ text: res.message, type: res.messageType });
    focusField(res.field ?? 'firstName');
  };

  /** CLEAR-CURRENT-SCREEN. */
  const clear = () => {
    setForm(EMPTY);
    setMessage({});
    focusField('firstName');
  };

  return (
    <Screen
      tranId="CU01"
      program="COUSR01C"
      title="Add User"
      message={message.text}
      messageType={message.type}
      pfKeys={[
        { key: 'Enter', label: 'Add User', onPress: () => void add() },
        { key: 'F3', label: 'Back', onPress: () => navigate(programPath('COADM01C')) },
        { key: 'F4', label: 'Clear', onPress: clear },
        // COUSR01C has no DFHPF12 branch: WHEN OTHER -> CCDA-MSG-INVALID-KEY
        {
          key: 'F12',
          label: 'Exit',
          onPress: () => {
            setMessage({ text: MSG_INVALID_KEY, type: 'ERROR' });
            focusField('firstName');
          },
        },
      ]}
    >
      <form onSubmit={add}>
        <p>
          <label htmlFor="firstName">First Name:</label>{' '}
          <input id="firstName" maxLength={20} value={form.firstName} onChange={set('firstName')} autoFocus />{' '}
          <label htmlFor="lastName">Last Name:</label>{' '}
          <input id="lastName" maxLength={20} value={form.lastName} onChange={set('lastName')} />
        </p>
        <p>
          <label htmlFor="userId">User ID:</label>{' '}
          <input id="userId" maxLength={8} value={form.userId} onChange={set('userId')} /> (8 Char){' '}
          <label htmlFor="password">Password:</label>{' '}
          <input id="password" type="password" maxLength={8} value={form.password} onChange={set('password')} /> (8 Char)
        </p>
        <p>
          <label htmlFor="userType">User Type:</label>{' '}
          <input id="userType" maxLength={1} size={1} value={form.userType} onChange={set('userType')} /> (A=Admin,
          U=User)
        </p>
        <button type="submit" hidden>
          Add User
        </button>
      </form>
    </Screen>
  );
}
