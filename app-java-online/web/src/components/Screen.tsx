import { useEffect, useState, type ReactNode } from 'react';
import type { MessageType } from '../api/types';
import './screen.css';

/** CCDA-TITLE01 / CCDA-TITLE02 from COTTL01Y. */
export const TITLE01 = 'AWS Mainframe Modernization';
export const TITLE02 = 'CardDemo';

export interface PfKey {
  /** Keyboard key name, e.g. 'F3', 'F7', 'Escape'. */
  key: string;
  label: string;
  onPress: () => void;
}

export interface ScreenProps {
  /** CICS transaction id shown as "Tran:" (e.g. CC00). */
  tranId: string;
  /** COBOL program shown as "Prog:" (e.g. COSGN00C). */
  program: string;
  title: string;
  message?: string;
  messageType?: MessageType;
  pfKeys?: PfKey[];
  children: ReactNode;
}

function formatDate(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0');
  return {
    date: `${p(d.getMonth() + 1)}/${p(d.getDate())}/${p(d.getFullYear() % 100)}`,
    time: `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`,
  };
}

/** Common frame of every BMS map: header lines, title, body, ERRMSG line and PF-key legend. */
export function Screen({ tranId, program, title, message, messageType, pfKeys = [], children }: ScreenProps) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const pf = pfKeys.find((k) => k.key === e.key);
      if (pf) {
        e.preventDefault();
        pf.onPress();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [pfKeys]);
  const { date, time } = formatDate(now);

  return (
    <div className="screen">
      <header className="screen-header">
        <div>
          <span>Tran: {tranId}</span>
          <span className="screen-title01">{TITLE01}</span>
          <span>Date: {date}</span>
        </div>
        <div>
          <span>Prog: {program}</span>
          <span className="screen-title02">{TITLE02}</span>
          <span>Time: {time}</span>
        </div>
      </header>
      <h1 className="screen-title">{title}</h1>
      <main className="screen-body">{children}</main>
      <div role="status" className={`screen-message screen-message-${(messageType ?? 'INFO').toLowerCase()}`}>
        {message ?? ''}
      </div>
      {pfKeys.length > 0 && (
        <nav className="screen-pfkeys" aria-label="Function keys">
          {pfKeys.map((k) => (
            <button key={k.key} type="button" onClick={k.onPress}>
              {k.key}={k.label}
            </button>
          ))}
        </nav>
      )}
    </div>
  );
}
