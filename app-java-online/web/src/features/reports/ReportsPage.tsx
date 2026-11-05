import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, api } from '../../api/client';
import type { MessageType, ScreenResponse } from '../../api/types';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';

export type ReportType = 'MONTHLY' | 'YEARLY' | 'CUSTOM';

export interface ReportDate {
  month: string;
  day: string;
  year: string;
}

/** Mirrors com.carddemo.online.report.ReportRequest (a persisted report_request row). */
export interface ReportRequest {
  requestId: number;
  reportName: string;
  startDate: string;
  endDate: string;
  requestedBy: string;
  requestedAt: string;
  status: string;
  jcl: string;
}

const EMPTY_DATE: ReportDate = { month: '', day: '', year: '' };

const DATE_FIELDS = [
  'startDate.month',
  'startDate.day',
  'startDate.year',
  'endDate.month',
  'endDate.day',
  'endDate.year',
] as const;
type Field = 'reportType' | 'confirm' | (typeof DATE_FIELDS)[number];

/** CORPT00C / CORPT00: request a Monthly, Yearly or Custom transaction report. */
export function ReportsPage() {
  const navigate = useNavigate();
  const [reportType, setReportType] = useState<ReportType | ''>('');
  const [startDate, setStartDate] = useState<ReportDate>(EMPTY_DATE);
  const [endDate, setEndDate] = useState<ReportDate>(EMPTY_DATE);
  const [confirm, setConfirm] = useState('');
  const [submitted, setSubmitted] = useState<ReportRequest | undefined>();
  const [message, setMessage] = useState<string | undefined>();
  const [messageType, setMessageType] = useState<MessageType | undefined>();
  const [field, setField] = useState<Field>('reportType');
  const [focusTick, setFocusTick] = useState(0);
  const refs = useRef<Partial<Record<Field, HTMLInputElement | null>>>({});

  useEffect(() => {
    refs.current[field]?.focus();
  }, [field, focusTick]);

  const focus = (f: string | undefined) => {
    const known = f && (f === 'confirm' || (DATE_FIELDS as readonly string[]).includes(f));
    setField(known ? (f as Field) : 'reportType');
    setFocusTick((t) => t + 1);
  };

  const clearFields = () => {
    // INITIALIZE-ALL-FIELDS
    setReportType('');
    setStartDate(EMPTY_DATE);
    setEndDate(EMPTY_DATE);
    setConfirm('');
  };

  const apply = (res: ScreenResponse<ReportRequest>, ok: boolean) => {
    if (res.nextProgram) {
      navigate(programPath(res.nextProgram));
      return;
    }
    if (ok) clearFields();
    setSubmitted(ok ? res.data : undefined);
    setMessage(res.message);
    setMessageType(res.messageType);
    focus(res.field);
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const body = {
      reportType: reportType || null,
      startDate: reportType === 'CUSTOM' ? startDate : null,
      endDate: reportType === 'CUSTOM' ? endDate : null,
      confirm,
    };
    try {
      apply(await api.post<ReportRequest>('/api/reports', body), true);
    } catch (err) {
      if (err instanceof ApiError) apply(err.response as ScreenResponse<ReportRequest>, false);
      else {
        setMessage('Unexpected error. Please try again ...');
        setMessageType('ERROR');
      }
    }
  };

  const dateInputs = (prefix: 'startDate' | 'endDate', value: ReportDate, set: (d: ReportDate) => void) => {
    const label = prefix === 'startDate' ? 'Start Date' : 'End Date';
    const part = (key: keyof ReportDate, name: string, size: number) => (
      <input
        aria-label={`${label} ${name}`}
        ref={(el) => {
          refs.current[`${prefix}.${key}`] = el;
        }}
        value={value[key]}
        maxLength={size}
        size={size}
        onChange={(e) => set({ ...value, [key]: e.target.value })}
      />
    );
    return (
      <p>
        <span>{label} :</span> {part('month', 'Month', 2)} / {part('day', 'Day', 2)} / {part('year', 'Year', 4)}{' '}
        (MM/DD/YYYY)
      </p>
    );
  };

  const typeOption = (type: ReportType, label: string, first = false) => (
    <p>
      <input
        type="radio"
        id={`report-${type.toLowerCase()}`}
        name="reportType"
        value={type}
        checked={reportType === type}
        ref={
          first
            ? (el) => {
                refs.current.reportType = el;
              }
            : undefined
        }
        onChange={() => setReportType(type)}
      />{' '}
      <label htmlFor={`report-${type.toLowerCase()}`}>{label}</label>
    </p>
  );

  return (
    <Screen
      tranId="CR00"
      program="CORPT00C"
      title="Transaction Reports"
      message={message}
      messageType={messageType}
      pfKeys={[{ key: 'F3', label: 'Back', onPress: () => navigate(programPath('COMEN01C')) }]}
    >
      <form onSubmit={onSubmit} aria-label="Transaction Reports">
        {typeOption('MONTHLY', 'Monthly (Current Month)', true)}
        {typeOption('YEARLY', 'Yearly (Current Year)')}
        {typeOption('CUSTOM', 'Custom (Date Range)')}
        {dateInputs('startDate', startDate, setStartDate)}
        {dateInputs('endDate', endDate, setEndDate)}
        <p>
          <label htmlFor="report-confirm">The Report will be submitted for printing. Please confirm:</label>{' '}
          <input
            id="report-confirm"
            ref={(el) => {
              refs.current.confirm = el;
            }}
            value={confirm}
            maxLength={1}
            size={1}
            onChange={(e) => setConfirm(e.target.value)}
          />{' '}
          (Y/N)
        </p>
        <button type="submit">ENTER=Continue</button>
        {submitted && (
          <p aria-label="Submitted report request">
            Request {submitted.requestId}: {submitted.reportName} {submitted.startDate} to {submitted.endDate} (
            {submitted.status})
          </p>
        )}
      </form>
    </Screen>
  );
}
