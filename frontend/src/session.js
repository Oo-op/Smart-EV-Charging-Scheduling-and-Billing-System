import { reactive } from 'vue';

export const STORAGE_KEY = 'smart_charging_session';
const LEGACY_STORAGE_KEY = 'charging_user_session';

const EMPTY_SESSION = {
  userId: null,
  username: '',
  token: '',
  role: ''
};

function normalizeSession(raw) {
  if (!raw || !raw.userId) {
    return { ...EMPTY_SESSION };
  }
  const inferredRole =
    raw.role ||
    (typeof raw.token === 'string' && raw.token.startsWith('mock:')
      ? raw.token.split(':')[1] || ''
      : '');
  return {
    userId: raw.userId,
    username: raw.username || '',
    token: raw.token || '',
    role: inferredRole
  };
}

function readStoredSession() {
  const current = sessionStorage.getItem(STORAGE_KEY);
  if (current) {
    return current;
  }

  const legacy =
    localStorage.getItem(STORAGE_KEY) ||
    localStorage.getItem(LEGACY_STORAGE_KEY);
  if (legacy) {
    sessionStorage.setItem(STORAGE_KEY, legacy);
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(LEGACY_STORAGE_KEY);
    return legacy;
  }

  return null;
}

function clearStoredSession() {
  sessionStorage.removeItem(STORAGE_KEY);
  localStorage.removeItem(STORAGE_KEY);
  localStorage.removeItem(LEGACY_STORAGE_KEY);
}

function loadSession() {
  try {
    const raw = readStoredSession();
    return normalizeSession(raw ? JSON.parse(raw) : null);
  } catch {
    clearStoredSession();
    return { ...EMPTY_SESSION };
  }
}

export const authState = reactive({
  session: loadSession()
});

export function setSession(session) {
  const normalized = normalizeSession(session);
  authState.session = normalized;
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
  localStorage.removeItem(STORAGE_KEY);
  localStorage.removeItem(LEGACY_STORAGE_KEY);
}

export function clearSession() {
  authState.session = { ...EMPTY_SESSION };
  clearStoredSession();
}

export function getSession() {
  return authState.session;
}

export function isAuthenticated() {
  return !!authState.session.userId;
}

/** 打开独立会话的新窗口/标签页（sessionStorage 按窗口隔离） */
export function openNewSessionWindow(path = '/') {
  const url = new URL(path, window.location.origin);
  return window.open(url.toString(), '_blank', 'noopener,noreferrer');
}
