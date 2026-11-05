import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { call, focusField, userApi, type ScreenMessage, type UserView } from './userApi';

/** COUSR03C / COUSR03 map: Delete User. */
export function UserDeletePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const initialId = params.get('userId') ?? '';
  const from = params.get('from') || 'COADM01C';
  const [userId, setUserId] = useState(initialId);
  const [user, setUser] = useState<UserView | null>(null);
  const [message, setMessage] = useState<ScreenMessage>({});

  /** PROCESS-ENTER-KEY: READ-USER-SEC-FILE and show the user. */
  const fetchUser = async (id: string) => {
    const { ok, res } = await call(userApi.fetch(id, 'delete'));
    setUser(ok && res.data ? res.data : null);
    setMessage({ text: res.message, type: res.messageType });
    focusField(res.field);
  };

  const fetched = useRef(false);
  useEffect(() => {
    // first entry with CDEMO-CU03-USR-SELECTED from COUSR00C
    if (initialId && !fetched.current) {
      fetched.current = true;
      void fetchUser(initialId);
    }
  }, [initialId]);

  /** DELETE-USER-INFO. */
  const remove = async () => {
    const { ok, res } = await call(userApi.remove(userId));
    if (ok) {
      // DELETE-USER-SEC-FILE NORMAL: INITIALIZE-ALL-FIELDS
      setUserId('');
      setUser(null);
    }
    setMessage({ text: res.message, type: res.messageType });
    focusField(res.field);
  };

  /** CLEAR-CURRENT-SCREEN. */
  const clear = () => {
    setUserId('');
    setUser(null);
    setMessage({});
    focusField('userId');
  };

  const onEnter = (e?: FormEvent) => {
    e?.preventDefault();
    void fetchUser(userId);
  };

  return (
    <Screen
      tranId="CU03"
      program="COUSR03C"
      title="Delete User"
      message={message.text}
      messageType={message.type}
      pfKeys={[
        { key: 'Enter', label: 'Fetch', onPress: () => onEnter() },
        { key: 'F3', label: 'Back', onPress: () => navigate(programPath(from)) },
        { key: 'F4', label: 'Clear', onPress: clear },
        { key: 'F5', label: 'Delete', onPress: () => void remove() },
        { key: 'F12', label: 'Cancel', onPress: () => navigate(programPath('COADM01C')) },
      ]}
    >
      <form onSubmit={onEnter}>
        <p>
          <label htmlFor="userId">Enter User ID:</label>{' '}
          <input id="userId" maxLength={8} value={userId} onChange={(e) => setUserId(e.target.value)} autoFocus />
        </p>
        <hr />
        <dl aria-label="User">
          <dt>First Name:</dt>
          <dd data-testid="firstName">{user?.firstName ?? ''}</dd>
          <dt>Last Name:</dt>
          <dd data-testid="lastName">{user?.lastName ?? ''}</dd>
          <dt>User Type:</dt>
          <dd data-testid="userType">{user?.userType ?? ''}</dd>
        </dl>
        <button type="submit" hidden>
          Fetch
        </button>
      </form>
    </Screen>
  );
}
