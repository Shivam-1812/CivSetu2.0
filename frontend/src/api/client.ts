import type { AuthResponse } from './types';

/**
 * The one place that talks HTTP to the CivSetu API.
 *
 * - The access token lives in memory only (never localStorage), so page scripts cannot leak it
 *   between sessions; the refresh token is an httpOnly cookie the browser sends to /auth/refresh.
 * - A 401 triggers one silent refresh and a retry.
 * - Refreshes are serialised (within the tab and across tabs via the Web Locks API): the backend
 *   rotates refresh tokens and treats a reused one as theft, so two parallel refreshes would end
 *   the session.
 */

export const API_BASE = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`;

export interface FieldError {
  field: string;
  message: string;
}

/** An RFC 9457 problem returned by the API, with its stable machine-readable code. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** The message for one form field, if the server rejected it. */
  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.message;
  }
}

type SessionListener = (session: AuthResponse | null) => void;

let accessToken: string | null = null;
let pendingRefresh: Promise<AuthResponse | null> | null = null;
const listeners = new Set<SessionListener>();

/** Called by the auth layer whenever the session starts, refreshes or ends. */
export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function setSession(session: AuthResponse | null): void {
  accessToken = session?.accessToken ?? null;
  listeners.forEach((listener) => listener(session));
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** Exchanges the refresh cookie for a new session; null if there is no valid session. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!pendingRefresh) {
    pendingRefresh = withRefreshLock(async () => {
      const response = await fetch(`${API_BASE}/auth/refresh`, { method: 'POST', credentials: 'include' });
      const session = response.ok ? ((await response.json()) as AuthResponse) : null;
      setSession(session);
      return session;
    }).finally(() => {
      pendingRefresh = null;
    });
  }
  return pendingRefresh;
}

function withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
  // The DOM typings say Promise<Promise<T>>, but the browser flattens the callback's promise
  return locks ? (locks.request('civsetu-refresh', work) as unknown as Promise<T>) : work();
}

type QueryValue = string | number | boolean | null | undefined | Array<string | number>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** A plain object is sent as JSON; FormData as multipart. */
  body?: unknown;
  query?: Record<string, QueryValue>;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) value.forEach((item) => params.append(key, String(item)));
    else params.append(key, String(value));
  });
  const queryString = params.toString();
  return `${API_BASE}${path}${queryString ? `?${queryString}` : ''}`;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options);
  if (response.status === 401 && accessToken && !path.startsWith('/auth/')) {
    // The access token expired: refresh once and retry
    const session = await refreshSession();
    if (session) {
      return parse<T>(await send(path, options));
    }
  }
  return parse<T>(response);
}

function send(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body; // the browser sets the multipart boundary
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return fetch(buildUrl(path, options.query), {
    method: options.method ?? 'GET',
    headers,
    body,
    credentials: 'include',
    signal: options.signal,
  });
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  throw await toApiError(response);
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as {
      code?: string;
      detail?: string;
      title?: string;
      errors?: FieldError[];
    };
    return new ApiError(
      response.status,
      problem.code ?? `HTTP_${response.status}`,
      problem.detail ?? problem.title ?? response.statusText,
      problem.errors ?? [],
    );
  } catch {
    return new ApiError(response.status, `HTTP_${response.status}`, response.statusText || 'Request failed');
  }
}

/** A JSON part for multipart requests; the API expects Content-Type application/json on it. */
export function jsonPart(value: unknown): Blob {
  return new Blob([JSON.stringify(value)], { type: 'application/json' });
}
