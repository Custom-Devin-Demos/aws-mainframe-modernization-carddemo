import { useEffect } from 'react';

/** Puts the cursor on the field the COBOL flagged (MOVE -1 TO xxxL). Inputs use the field name as id. */
export function useFieldFocus(field: string | undefined, version: number) {
  useEffect(() => {
    if (!field) return;
    const el = document.getElementById(field);
    if (el instanceof HTMLInputElement) el.focus();
  }, [field, version]);
}
