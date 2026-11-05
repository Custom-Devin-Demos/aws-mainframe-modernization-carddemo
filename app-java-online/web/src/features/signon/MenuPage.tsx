import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { useSession } from '../../session/SessionContext';
import type { MenuOption, MenuScreen } from './types';

export interface MenuPageProps {
  tranId: string;
  program: string;
  title: string;
  /** GET endpoint returning the option table. */
  menuPath: string;
  /** POST endpoint taking {option}. */
  selectPath: string;
}

/** Shared body of COMEN01C (main menu) and COADM01C (admin menu). */
export function MenuPage({ tranId, program, title, menuPath, selectPath }: MenuPageProps) {
  const navigate = useNavigate();
  const { signOut } = useSession();
  const [options, setOptions] = useState<MenuOption[]>([]);
  const [option, setOption] = useState('');
  const [message, setMessage] = useState<string>();
  const [messageType, setMessageType] = useState<MessageType>();
  const inputRef = useRef<HTMLInputElement>(null);

  function show(res: ScreenResponse<MenuScreen>) {
    if (res.data?.options) setOptions(res.data.options);
    setMessage(res.message);
    setMessageType(res.messageType);
    inputRef.current?.focus();
  }

  function handleError(err: unknown) {
    if (!(err instanceof ApiError)) {
      setMessage(err instanceof Error ? err.message : String(err));
      setMessageType('ERROR');
      return;
    }
    const res = err.response as ScreenResponse<MenuScreen>;
    if (res.nextProgram) {
      navigate(programPath(res.nextProgram));
      return;
    }
    show(res);
  }

  useEffect(() => {
    api.get<MenuScreen>(menuPath).then(show, handleError);
  }, [menuPath]);

  async function onSubmit(e?: FormEvent) {
    e?.preventDefault();
    try {
      const res = await api.post<MenuScreen>(selectPath, { option });
      if (res.nextProgram) {
        navigate(programPath(res.nextProgram));
        return;
      }
      show(res);
    } catch (err) {
      handleError(err);
    }
  }

  // PF3: XCTL COSGN00C without a COMMAREA, i.e. back to a fresh sign-on screen.
  function onExit() {
    signOut();
    navigate(programPath('COSGN00C'));
  }

  return (
    <Screen
      tranId={tranId}
      program={program}
      title={title}
      message={message}
      messageType={messageType}
      pfKeys={[
        { key: 'Enter', label: 'Continue', onPress: () => void onSubmit() },
        { key: 'F3', label: 'Exit', onPress: onExit },
      ]}
    >
      <form onSubmit={onSubmit} noValidate>
        <ol className="menu-options" style={{ listStyle: 'none' }}>
          {options.map((o) => (
            <li key={o.number}>
              {o.number}. {o.name}
            </li>
          ))}
        </ol>
        <p>
          <label htmlFor="option">Please select an option :</label>{' '}
          <input
            id="option"
            ref={inputRef}
            value={option}
            maxLength={2}
            inputMode="numeric"
            autoFocus
            onChange={(e) => setOption(e.target.value)}
          />
        </p>
        <button type="submit" hidden />
      </form>
    </Screen>
  );
}

export function MainMenuPage() {
  return <MenuPage tranId="CM00" program="COMEN01C" title="Main Menu" menuPath="/api/menu" selectPath="/api/menu/select" />;
}

export function AdminMenuPage() {
  return (
    <MenuPage
      tranId="CA00"
      program="COADM01C"
      title="Admin Menu"
      menuPath="/api/admin/menu"
      selectPath="/api/admin/menu/select"
    />
  );
}
