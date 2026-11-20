/* AURA MUSIC 2.0 bootstrap. No frameworks, no private credentials in the client. */
import { state, toast, persistSettings } from './state.js';
import { API_BASE, endpoints } from './api.js';
import { hydrateIcons, icon } from './icons.js';
import { initViews, render, runSearch, loadDiscover, loadApk } from './views.js';
import { initPlayer, closeFullPlayer, playTrack } from './player.js';
import { attachSearchSuggestions } from './suggest.js';
import { initLyrics, closeLyrics, LOOKS, setLook } from './lyrics.js';
import { initRouter } from './router.js';
import { initLastfm } from './lastfm.js';

function setBackendStatus(online) {
  const pill = document.getElementById('backendPill');
  if (!pill) return;
  pill.classList.toggle('online', online);
  pill.classList.toggle('offline', !online);
  pill.querySelector('span').textContent = online ? 'BACKEND ONLINE' : 'LOCAL MODE';
  pill.title = online ? 'Music resolution service connected' : 'Offline: saved library and local files still work';
}

async function bootstrap() {
  hydrateIcons(document);

  // Service worker: static shell only. Audio, artwork and private data are excluded.
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('./sw.js').catch(() => { /* non-fatal */ });
  }

  initViews();
  initPlayer();
  initLyrics();
  initRouter();
  initLastfm();

  const topInput = document.getElementById('topSearchInput');
  const topForm = document.getElementById('topSearch');
  topForm?.addEventListener('submit', (event) => {
    event.preventDefault();
    const value = topInput?.value.trim();
    if (value) runSearch(value);
  });
  attachSearchSuggestions(topInput, {
    onPick: (track, queue) => playTrack(track, queue, 'Search suggestion'),
  });

  document.getElementById('themeBtn')?.addEventListener('click', () => {
    const ids = ['AUTO', ...LOOKS.map((l) => l.look)];
    const next = ids[(ids.indexOf(state.settings.lyricLook) + 1) % ids.length];
    setLook(next);
    toast(`LyricsPlay look: ${next}`);
  });

  document.getElementById('forwardBtn')?.setAttribute('title', 'Go forward');
  document.addEventListener('aura:closeoverlays', closeLyrics);

  try {
    state.config = await endpoints.config();
    setBackendStatus(true);
  } catch {
    state.config = { capabilities: {}, groqConfigured: false };
    setBackendStatus(false);
  }
  // Wake a sleeping backend when the user returns to the tab, before they type.
  let lastWarm = Date.now();
  const warmBackend = () => {
    if (Date.now() - lastWarm < 120000) return;
    lastWarm = Date.now();
    endpoints.health().catch(() => { /* offline remains usable */ });
  };
  document.addEventListener('visibilitychange', () => { if (!document.hidden) warmBackend(); });
  window.addEventListener('focus', warmBackend);
  // Mood Radio and Settings show config-derived notes; refresh once config is known.
  if (state.route === '/mood' || state.route === '/settings') {
    render(document.getElementById('view'), state.route);
  }
  loadApk();
  loadDiscover();

  window.addEventListener('error', (event) => {
    console.error('[AURA] Unhandled error', event.error || event.message);
    toast('Something went wrong. The app is still usable — retry the action.', true);
  });
  window.addEventListener('unhandledrejection', (event) => {
    console.error('[AURA] Unhandled promise rejection', event.reason);
  });

  document.documentElement.dataset.auraReady = '1';
}

bootstrap();
