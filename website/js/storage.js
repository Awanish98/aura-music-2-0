/* Local-first persistence. Library and preferences stay in this browser.
 * The optional Last.fm session key is sent only to AURA's own backend, which
 * signs scrobbles with the shared secret so the secret never reaches the web app.
 */
const PREFIX = 'aura2:v1:';

export const DEFAULT_SETTINGS = {
  lyricLook: 'AUTO',
  lyricEngine: 'classic',
  mobileStyle: 'M_AMOLED',
  autoMood: true,
  reducedMotion: false,
  crossfadeVisual: true,
  showWordApproxNote: true,
  volume: 0.8,
  sortLibrary: 'recent',
  dataSaver: false,
  lastfmSessionKey: '',
  lastfmUsername: '',
  scrobbleEnabled: true,
  nowPlayingEnabled: true,
  loveEnabled: false,
  scrobblePercent: 0.5,
  scrobbleMinSeconds: 30,
};

export const DEFAULT_LIBRARY = {
  favorites: [],
  playlists: [],
  history: [],
  recent: [],
  files: [],
  hidden: [],
};

export function load(key, fallback) {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    if (raw === null) return structuredClone(fallback);
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : structuredClone(fallback);
  } catch {
    return structuredClone(fallback);
  }
}

export function save(key, value) {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function remove(key) {
  try { localStorage.removeItem(PREFIX + key); } catch { /* private mode */ }
}

export function clearAll() {
  try {
    Object.keys(localStorage).filter((k) => k.startsWith(PREFIX)).forEach((k) => localStorage.removeItem(k));
  } catch { /* private mode */ }
}
