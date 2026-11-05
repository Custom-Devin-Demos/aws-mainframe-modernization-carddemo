import { useCallback, useEffect, useRef, useState } from 'react';

/** Puts the cursor on the input named by ScreenResponse.field (MOVE -1 TO xxxL). */
export function useFieldFocus(initial: string) {
  const refs = useRef<Record<string, HTMLInputElement | null>>({});
  const [target, setTarget] = useState<{ field: string; tick: number }>({ field: initial, tick: 0 });
  useEffect(() => {
    refs.current[target.field]?.focus();
  }, [target]);
  const focus = useCallback((field: string | undefined) => {
    setTarget((t) => ({ field: field ?? initial, tick: t.tick + 1 }));
  }, [initial]);
  const register = useCallback((name: string) => (el: HTMLInputElement | null) => {
    refs.current[name] = el;
  }, []);
  return { focus, register };
}
