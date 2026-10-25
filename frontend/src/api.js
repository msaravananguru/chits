// All backend calls are POST (as decided). The caller is identified by the
// X-User-Id header for now; the real login will replace this later.

const KEY = 'chits.userId';

export function getUserId() {
  try { return localStorage.getItem(KEY); } catch { return null; }
}
export function setUserId(id) {
  try { localStorage.setItem(KEY, String(id)); } catch { /* storage blocked */ }
}
export function clearUserId() {
  try { localStorage.removeItem(KEY); } catch { /* storage blocked */ }
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

let memoryUserId = null; // fallback when localStorage is blocked

export async function post(path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const uid = getUserId() ?? memoryUserId;
  if (uid) headers['X-User-Id'] = uid;

  let res;
  try {
    res = await fetch('/api' + path, {
      method: 'POST',
      headers,
      body: JSON.stringify(body ?? {}),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check that the backend is running.');
  }

  let data = null;
  try { data = await res.json(); } catch { /* empty body */ }

  if (!res.ok) {
    if (res.status === 401) window.dispatchEvent(new Event('chits:unauthorized'));
    throw new ApiError(res.status, (data && data.message) || `Request failed (${res.status})`);
  }
  return data;
}
