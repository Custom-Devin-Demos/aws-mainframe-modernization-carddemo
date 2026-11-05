import { ApiError, api } from './client';
import { getToken, saveSession } from '../session/sessionStore';
import { mockFetch } from '../test/renderWithSession';

describe('api client', () => {
  it('sends the bearer token and returns the envelope', async () => {
    saveSession('tok', { userId: 'USER0001', userType: 'U' });
    const calls = mockFetch([200, { data: { userId: 'USER0001' } }]);
    const res = await api.get<{ userId: string }>('/api/session');
    expect(res.data?.userId).toBe('USER0001');
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe('Bearer tok');
  });

  it('throws ApiError with the COBOL message and field', async () => {
    mockFetch([400, { message: 'Acct ID can NOT be empty...', messageType: 'ERROR', field: 'acctId' }]);
    const err = await api.post('/api/bill-payments', {}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe('Acct ID can NOT be empty...');
    expect(err.response.field).toBe('acctId');
  });

  it('clears the session on 401', async () => {
    saveSession('tok', { userId: 'USER0001', userType: 'U' });
    mockFetch([401, { message: 'Please sign on ...', nextProgram: 'COSGN00C' }]);
    await api.get('/api/session').catch(() => undefined);
    expect(getToken()).toBeNull();
  });
});
