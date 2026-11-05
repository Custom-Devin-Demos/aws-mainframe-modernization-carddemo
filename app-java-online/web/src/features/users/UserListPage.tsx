import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Screen } from '../../components/Screen';
import { programPath } from '../../programs';
import { call, focusField, userApi, type ScreenMessage, type UserPage } from './userApi';

const PAGE_SIZE = 10;

/** COUSR00C / COUSR00 map: List Users. */
export function UserListPage() {
  const navigate = useNavigate();
  const [startUserId, setStartUserId] = useState('');
  const [page, setPage] = useState<UserPage | null>(null);
  const [selections, setSelections] = useState<string[]>(Array(PAGE_SIZE).fill(''));
  const [message, setMessage] = useState<ScreenMessage>({});

  const show = useCallback((res: { data?: UserPage; message?: string; messageType?: ScreenMessage['type']; field?: string }) => {
    if (res.data) {
      setPage(res.data);
      setSelections(Array(PAGE_SIZE).fill(''));
    }
    setMessage({ text: res.message, type: res.messageType });
    focusField(res.field);
  }, []);

  const enter = useCallback(
    async (search: string, sel?: string, selUserId?: string) => {
      const { res } = await call(userApi.list({ startUserId: search, sel, selUserId }));
      if (res.nextProgram && selUserId) {
        navigate(`${programPath(res.nextProgram)}?userId=${encodeURIComponent(selUserId)}&from=COUSR00C`);
        return;
      }
      show(res);
    },
    [navigate, show],
  );

  useEffect(() => {
    void enter('');
  }, [enter]);

  const onEnter = (e?: FormEvent) => {
    e?.preventDefault();
    // PROCESS-ENTER-KEY: the first non-blank SELnnnn wins
    const idx = selections.findIndex((s) => s.trim() !== '');
    const row = idx >= 0 ? page?.items[idx] : undefined;
    void enter(startUserId, idx >= 0 ? selections[idx] : undefined, row?.userId);
  };

  const pf7 = async () => {
    const { res } = await call(userApi.list({ before: page?.firstKey ?? '', page: page?.pageNumber ?? 0 }));
    show(res);
  };

  const pf8 = async () => {
    const { res } = await call(userApi.list({ after: page?.lastKey ?? '', page: page?.pageNumber ?? 0 }));
    show(res);
  };

  const rows = page?.items ?? [];

  return (
    <Screen
      tranId="CU00"
      program="COUSR00C"
      title="List Users"
      message={message.text}
      messageType={message.type}
      pfKeys={[
        { key: 'Enter', label: 'Continue', onPress: () => onEnter() },
        { key: 'F3', label: 'Back', onPress: () => navigate(programPath('COADM01C')) },
        { key: 'F7', label: 'Backward', onPress: () => void pf7() },
        { key: 'F8', label: 'Forward', onPress: () => void pf8() },
      ]}
    >
      <form onSubmit={onEnter}>
        <div>
          <label htmlFor="startUserId">Search User ID:</label>{' '}
          <input
            id="startUserId"
            maxLength={8}
            value={startUserId}
            onChange={(e) => setStartUserId(e.target.value)}
            autoFocus
          />
          <span style={{ float: 'right' }}>Page: {page && page.pageNumber > 0 ? page.pageNumber : ''}</span>
        </div>
        <table aria-label="Users">
          <thead>
            <tr>
              <th>Sel</th>
              <th>User ID</th>
              <th>First Name</th>
              <th>Last Name</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((u, i) => (
              <tr key={u.userId}>
                <td>
                  <input
                    aria-label={`Select ${u.userId}`}
                    maxLength={1}
                    size={1}
                    value={selections[i]}
                    onChange={(e) =>
                      setSelections((s) => s.map((v, j) => (j === i ? e.target.value : v)))
                    }
                  />
                </td>
                <td>{u.userId}</td>
                <td>{u.firstName}</td>
                <td>{u.lastName}</td>
                <td>{u.userType}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>Type &apos;U&apos; to Update or &apos;D&apos; to Delete a User from the list</p>
        <button type="submit" hidden>
          Continue
        </button>
      </form>
    </Screen>
  );
}
