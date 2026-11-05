import { useCallback, useRef } from 'react';

/** MOVE -1 TO xxxL: focus the input whose name matches ScreenResponse.field. */
export function useFieldFocus() {
  const formRef = useRef<HTMLFormElement>(null);
  const focus = useCallback((field?: string) => {
    if (!field || !formRef.current) return;
    const el = formRef.current.querySelector<HTMLInputElement>(`[name="${field}"]`);
    el?.focus();
  }, []);
  return { formRef, focus };
}
