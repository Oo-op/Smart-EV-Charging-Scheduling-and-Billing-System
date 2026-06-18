import { reactive } from 'vue';

export const STORAGE_KEY = 'smart_charging_session';
const LEGACY_STORAGE_KEY = 'charging_user_session';

/** 按标签页隔离，同一浏览器可开多个窗口分别登录不同用户 */
const tabStorage = sessionStorage;

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

function clearLegacyLocalStorage() {
  localStorage.removeItem(STORAGE_KEY);
  localStorage.removeItem(LEGACY_STORAGE_KEY);
}

function loadSession() {
  try {
    const raw = tabStorage.getItem(STORAGE_KEY) || tabStorage.getItem(LEGACY_STORAGE_KEY);
    return normalizeSession(raw ? JSON.parse(raw) : null);
  } catch {
    tabStorage.removeItem(STORAGE_KEY);
    tabStorage.removeItem(LEGACY_STORAGE_KEY);
    return { ...EMPTY_SESSION };
  }
}

export const authState = reactive({
  session: loadSession()
});

export function setSession(session) {
  const normalized = normalizeSession(session);
  authState.session = normalized;
  tabStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
  tabStorage.removeItem(LEGACY_STORAGE_KEY);
  clearLegacyLocalStorage();
}

export function clearSession() {
  authState.session = { ...EMPTY_SESSION };
  tabStorage.removeItem(STORAGE_KEY);
  tabStorage.removeItem(LEGACY_STORAGE_KEY);
  clearLegacyLocalStorage();
}

export function getSession() {
  return authState.session;
}

export function isAuthenticated() {
  return !!authState.session.userId;
}
