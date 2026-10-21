import type { Session } from './types';

const STORAGE_KEY = 'kuntur.session';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly isoReason?: string,
    readonly fields?: Record<string, string>,
  ) {
    super(message);
  }
}

interface Problem {
  code?: string;
  detail?: string;
  isoReason?: string;
  fields?: Record<string, string>;
}

// shortcut: tokens live in sessionStorage (cleared with the tab); a BFF with httpOnly cookies is the hardened option.
let session: Session | null = readStored();
const listeners = new Set<(s: Session | null) => void>();

function readStored(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function getSession(): Session | null {
  return session;
}

export function setSession(next: Session | null): void {
  session = next;
  try {
    if (next) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* storage unavailable: session stays in memory only */
  }
  listeners.forEach((l) => {
    l(next);
  });
}

export function onSessionChange(listener: (s: Session | null) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

// Single-flight refresh: concurrent 401s share one rotation (a second rotation would trip reuse detection).
let refreshing: Promise<boolean> | null = null;

async function refresh(): Promise<boolean> {
  const current = session;
  if (!current) return false;
  refreshing ??= fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  })
    .then(async (res) => {
      if (!res.ok) {
        setSession(null);
        return false;
      }
      setSession((await res.json()) as Session);
      return true;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

interface Options {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  /** Send the body as-is (e.g. XML) instead of JSON. */
  rawBody?: string;
  accept?: string;
}

async function send(path: string, opts: Options): Promise<Response> {
  const headers: Record<string, string> = { Accept: opts.accept ?? 'application/json', ...opts.headers };
  if (session) headers.Authorization = `Bearer ${session.accessToken}`;
  let body: string | undefined;
  if (opts.rawBody !== undefined) body = opts.rawBody;
  else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  return fetch(path, { method: opts.method ?? 'GET', headers, body });
}

async function request(path: string, opts: Options): Promise<Response> {
  let res = await send(path, opts);
  if (res.status === 401 && session && !path.startsWith('/api/v1/auth/')) {
    if (await refresh()) res = await send(path, opts);
  }
  if (!res.ok) {
    let problem: Problem = {};
    try {
      problem = (await res.json()) as Problem;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(
      res.status,
      problem.code ?? `HTTP_${res.status}`,
      problem.detail ?? res.statusText,
      problem.isoReason,
      problem.fields,
    );
  }
  return res;
}

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const res = await request(path, opts);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export async function apiText(path: string, opts: Options = {}): Promise<string> {
  const res = await request(path, { accept: 'application/xml', ...opts });
  return res.text();
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
