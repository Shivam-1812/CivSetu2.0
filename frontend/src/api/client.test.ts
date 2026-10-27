import { beforeEach, describe, expect, it, vi } from 'vitest';

import { api, ApiError, buildUrl, getAccessToken, setSession } from './client';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const session = (token: string) => ({ accessToken: token, tokenType: 'Bearer', expiresIn: 900, user: { id: 'u1' } });

describe('buildUrl', () => {
  it('repeats array parameters and skips empty ones', () => {
    expect(buildUrl('/issues', { status: ['REPORTED', 'ASSIGNED'], q: '', category: undefined, page: 0 })).toBe(
      '/api/v1/issues?status=REPORTED&status=ASSIGNED&page=0',
    );
  });
});

describe('api', () => {
  beforeEach(() => setSession(null));

  it('turns a problem response into an ApiError with its code and field errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(400, {
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: 'Request validation failed.',
        errors: [{ field: 'title', message: 'size must be between 5 and 150' }],
      }),
    );

    const error = await api('/issues', { method: 'POST', body: {} }).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('VALIDATION_FAILED');
    expect((error as ApiError).fieldMessage('title')).toBe('size must be between 5 and 150');
  });

  it('sends the access token and refreshes once when it has expired', async () => {
    setSession(session('old-token') as never);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/auth/refresh')) return json(200, session('new-token'));
      const auth = new Headers(init?.headers).get('Authorization');
      return auth === 'Bearer new-token' ? json(200, { ok: true }) : json(401, { code: 'UNAUTHORIZED' });
    });

    await expect(api('/issues/mine')).resolves.toEqual({ ok: true });

    expect(getAccessToken()).toBe('new-token');
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))).toHaveLength(1);
    // The first try carried the old token, the retry the new one
    const authHeaders = fetchMock.mock.calls
      .filter(([url]) => String(url).endsWith('/issues/mine'))
      .map(([, init]) => new Headers(init?.headers).get('Authorization'));
    expect(authHeaders).toEqual(['Bearer old-token', 'Bearer new-token']);
  });

  it('shares one refresh between requests that fail at the same time', async () => {
    setSession(session('old-token') as never);
    let refreshes = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/auth/refresh')) {
        refreshes += 1;
        return json(200, session('new-token'));
      }
      const auth = new Headers(init?.headers).get('Authorization');
      return auth === 'Bearer new-token' ? json(200, { url }) : json(401, { code: 'UNAUTHORIZED' });
    });

    const results = await Promise.all([api('/issues/mine'), api('/notifications'), api('/surveys/mine')]);

    expect(results).toHaveLength(3);
    // Rotating refresh tokens treat a reused token as theft, so parallel refreshes must not happen
    expect(refreshes).toBe(1);
  });

  it('ends the session when the refresh is refused', async () => {
    setSession(session('old-token') as never);
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input).endsWith('/auth/refresh') ? json(401, { code: 'REFRESH_TOKEN_REUSED' }) : json(401, { code: 'UNAUTHORIZED' }),
    );

    await expect(api('/issues/mine')).rejects.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBeNull();
  });
});
