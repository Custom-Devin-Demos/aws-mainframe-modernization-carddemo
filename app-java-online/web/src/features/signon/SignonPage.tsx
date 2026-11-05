import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { useSession } from '../../session/SessionContext';
import type { SignonResult } from './types';

type Field = 'userId' | 'password';

/** COSGN00C (CC00) / map COSGN00. */
export function SignonPage() {
  const navigate = useNavigate();
  const { signIn, signOut } = useSession();
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<string>();
  const [messageType, setMessageType] = useState<MessageType>();
  const [field, setField] = useState<Field>('userId');
  const [thankYou, setThankYou] = useState<string>();
  const userIdRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (thankYou) return;
    (field === 'password' ? passwordRef : userIdRef).current?.focus();
  }, [field, message, thankYou]);

  async function onSubmit(e?: FormEvent) {
    e?.preventDefault();
    try {
      const res = await api.post<SignonResult>('/api/auth/signon', { userId, password });
      if (res.data) signIn(res.data.token, res.data.user);
      navigate(programPath(res.nextProgram ?? 'COMEN01C'));
    } catch (err) {
      if (!(err instanceof ApiError)) {
        setMessage(err instanceof Error ? err.message : String(err));
        setMessageType('ERROR');
        return;
      }
      setMessage(err.response.message);
      setMessageType(err.response.messageType ?? 'ERROR');
      setField(err.response.field === 'password' ? 'password' : 'userId');
    }
  }

  async function onExit() {
    const res = await api.post<void>('/api/auth/signoff');
    signOut();
    setThankYou(res.message ?? '');
  }

  if (thankYou !== undefined) {
    return (
      <div className="screen" role="status">
        <p>{thankYou}</p>
        <button
          type="button"
          onClick={() => {
            setThankYou(undefined);
            setUserId('');
            setPassword('');
            setMessage(undefined);
            setField('userId');
          }}
        >
          Sign on again
        </button>
      </div>
    );
  }

  return (
    <Screen
      tranId="CC00"
      program="COSGN00C"
      title="This is a Credit Card Demo Application for Mainframe Modernization"
      message={message}
      messageType={messageType}
      pfKeys={[
        { key: 'Enter', label: 'Sign-on', onPress: () => void onSubmit() },
        { key: 'F3', label: 'Exit', onPress: () => void onExit() },
      ]}
    >
      <form onSubmit={onSubmit} noValidate>
        <p>Type your User ID and Password, then press ENTER:</p>
        <p>
          <label htmlFor="userId">User ID     :</label>{' '}
          <input
            id="userId"
            ref={userIdRef}
            value={userId}
            maxLength={8}
            autoComplete="username"
            onChange={(e) => setUserId(e.target.value)}
          />{' '}
          (8 Char)
        </p>
        <p>
          <label htmlFor="password">Password    :</label>{' '}
          <input
            id="password"
            ref={passwordRef}
            type="password"
            value={password}
            maxLength={8}
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
          />{' '}
          (8 Char)
        </p>
        <button type="submit" hidden />
      </form>
    </Screen>
  );
}
