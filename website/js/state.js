/* Single application state store with observer notifications. */
import { load, save, DEFAULT_LIBRARY, DEFAULT_SETTINGS } from './storage.js';

const listeners = new Set();

export const state = {
  route: '/home',
  config: null,
  apk: null,
  discover: null,
  discoverLoading: false,
  discoverError: null,
  search: { query: '', results: null, loading: false, error: null, filter: 'all', sort: 'relevance' },
  detail: null,
  library: load('library', DEFAULT_LIBRARY),
  settings: load('settings', DEFAULT_SETTINGS),
  player: {
    queue: [],
    index: -1,
    playing: false,
    position: 0,
    duration: 0,
    volume: load('volume', 0.8),
    shuffle: false,
    repeat: 'off',
    shuffleOrder: [],
    context: '',
  },
  lyrics: { data: null, loading: false, error: null, look: null, open: false },
  ai: { mood: 'CHILL', vibe: '', mix: null, loading: false, error: null },
  flow: { active: false, seed: null, loading: false, seen: new Set() },
  toastTimer: null,
};

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function emit() {
  listeners.forEach((fn) => {
    try { fn(state); } catch { /* one bad listener must not break the app */ }
  });
}

export function persistLibrary() { save('library', state.library); }
export function persistSettings() { save('settings', state.settings); }
export function persistVolume() { save('volume', state.player.volume); }

export function isFavorite(id) { return state.library.favorites.includes(id); }

export function toggleFavorite(track) {
  const id = track.id || track.videoId;
  const at = state.library.favorites.indexOf(id);
  if (at >= 0) state.library.favorites.splice(at, 1);
  else state.library.favorites.unshift(id);
  persistLibrary();
  emit();
  return at < 0;
}

export function addHistory(track) {
  const id = track.id || track.videoId;
  state.library.history = [id, ...state.library.history.filter((x) => x !== id)].slice(0, 200);
  state.library.recent = [track, ...state.library.recent.filter((x) => (x.id || x.videoId) !== id)].slice(0, 24);
  persistLibrary();
  emit();
}

export function findTrack(id) {
  const all = [
    ...state.player.queue,
    ...(state.search.results?.tracks || []),
    ...state.library.recent,
    ...(state.discover?.sections || []).flatMap((s) => s.tracks),
    ...(state.detail?.tracks || []),
    ...(state.flow?.tracks || []),
    ...(state.ai?.mix?.tracks || []),
  ];
  return all.find((t) => (t.id || t.videoId) === id) || null;
}

export function makePlaylist(name, tracks = []) {
  const playlist = {
    id: 'pl_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7),
    name: name || 'New playlist',
    tracks,
    createdAt: Date.now(),
  };
  state.library.playlists.unshift(playlist);
  persistLibrary();
  emit();
  return playlist;
}

export function addToPlaylist(playlistId, track) {
  const playlist = state.library.playlists.find((p) => p.id === playlistId);
  if (!playlist) return false;
  const id = track.id || track.videoId;
  if (!playlist.tracks.some((t) => (t.id || t.videoId) === id)) playlist.tracks.push(track);
  persistLibrary();
  emit();
  return true;
}

export function removeFromPlaylist(playlistId, trackId) {
  const playlist = state.library.playlists.find((p) => p.id === playlistId);
  if (!playlist) return;
  playlist.tracks = playlist.tracks.filter((t) => (t.id || t.videoId) !== trackId);
  persistLibrary();
  emit();
}

export function deletePlaylist(playlistId) {
  state.library.playlists = state.library.playlists.filter((p) => p.id !== playlistId);
  persistLibrary();
  emit();
}

export function toast(message, error = false) {
  const node = document.getElementById('toast');
  if (!node) return;
  node.textContent = message;
  node.classList.toggle('error', error);
  node.classList.add('show');
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => node.classList.remove('show'), 3200);
}
