import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { call, focusField, userApi, type ScreenMessage } from './userApi';

const EMPTY = { firstName: '', lastName: '', password: '', userType: '' };

/** COUSR02C / COUSR02 map: Update User. The password is never returned, so it must be re-entered. */
export function UserUpdatePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const initialId = params.get('userId') ?? '';
  const from = params.get('from') || 'COADM01C';
  const [userId, setUserId] = useState(initialId);
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<ScreenMessage>({});
  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  /** PROCESS-ENTER-KEY: READ-USER-SEC-FILE and show the user. */
  const fetchUser = async (id: string) => {
    const { ok, res } = await call(userApi.fetch(id, 'update'));
    if (ok && res.data) {
      setForm({ firstName: res.data.firstName, lastName: res.data.lastName, password: '', userType: res.data.userType });
    } else {
      setForm(EMPTY);
    }
    setMessage({ text: res.message, type: res.messageType });
    focusField(res.field);
  };

  const fetched = useRef(false);
  useEffect(() => {
    // first entry with CDEMO-CU02-USR-SELECTED from COUSR00C
    if (initialId && !fetched.current) {
      fetched.current = true;
      void fetchUser(initialId);
    }
  }, [initialId]);

  /** UPDATE-USER-INFO. */
  const save = async () => {
    const { res } = await call(userApi.update(userId, form));
    setMessage({ text: res.message, type: res.messageType });
    focusField(res.field);
  };

  /** CLEAR-CURRENT-SCREEN. */
  const clear = () => {
    setUserId('');
    setForm(EMPTY);
    setMessage({});
    focusField('userId');
  };

  const onEnter = (e?: FormEvent) => {
    e?.preventDefault();
    void fetchUser(userId);
  };

  return (
    <Screen
      tranId="CU02"
      program="COUSR02C"
      title="Update User"
      message={message.text}
      messageType={message.type}
      pfKeys={[
        { key: 'Enter', label: 'Fetch', onPress: () => onEnter() },
        // DFHPF3: PERFORM UPDATE-USER-INFO, then XCTL to the calling program
        {
          key: 'F3',
          label: 'Save&Exit',
          onPress: () => void save().then(() => navigate(programPath(from))),
        },
        { key: 'F4', label: 'Clear', onPress: clear },
        { key: 'F5', label: 'Save', onPress: () => void save() },
        { key: 'F12', label: 'Cancel', onPress: () => navigate(programPath('COADM01C')) },
      ]}
    >
      <form onSubmit={onEnter}>
        <p>
          <label htmlFor="userId">Enter User ID:</label>{' '}
          <input id="userId" maxLength={8} value={userId} onChange={(e) => setUserId(e.target.value)} autoFocus />
        </p>
        <hr />
        <p>
          <label htmlFor="firstName">First Name:</label>{' '}
          <input id="firstName" maxLength={20} value={form.firstName} onChange={set('firstName')} />{' '}
          <label htmlFor="lastName">Last Name:</label>{' '}
          <input id="lastName" maxLength={20} value={form.lastName} onChange={set('lastName')} />
        </p>
        <p>
          <label htmlFor="password">Password:</label>{' '}
          <input id="password" type="password" maxLength={8} value={form.password} onChange={set('password')} /> (8 Char)
        </p>
        <p>
          <label htmlFor="userType">User Type:</label>{' '}
          <input id="userType" maxLength={1} size={1} value={form.userType} onChange={set('userType')} /> (A=Admin,
          U=User)
        </p>
        <button type="submit" hidden>
          Fetch
        </button>
      </form>
    </Screen>
  );
}
