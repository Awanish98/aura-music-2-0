/* Optional Last.fm scrobbling.
 *
 * The shared secret lives only in the backend. The browser holds the user's own
 * session key and sends track metadata to AURA's signed proxy. Scrobbling is
 * best-effort: a network failure here must never interrupt playback.
 */
import { state, persistSettings, emit } from './state.js';
import { endpoints } from './api.js';

let pendingToken = '';
let session = null;

export function isConnected() {
  return Boolean(state.settings.lastfmSessionKey);
}

function payloadFor(track) {
  const artist = String(track?.artist || '').trim().slice(0, 200);
  const title = String(track?.title || '').trim().slice(0, 200);
  if (!artist || !title) return null;
  const payload = { sessionKey: state.settings.lastfmSessionKey, artist, track: title };
  const album = String(track?.album || '').trim().slice(0, 200);
  if (album) payload.album = album;
  const seconds = Math.round((Number(track?.durationMs) || 0) / 1000);
  if (seconds >= 1) payload.duration = seconds;
  return payload;
}

function send(endpoint, payload) {
  if (!isConnected() || !payload) return;
  Promise.resolve(endpoint(payload)).catch(() => { /* scrobbling is never fatal */ });
}

function maybeNowPlaying() {
  if (!state.settings.nowPlayingEnabled || !session || session.nowPlayingSent) return;
  const payload = payloadFor(session.track);
  if (!payload) return;
  session.nowPlayingSent = true;
  send(endpoints.lastfmNowPlaying, payload);
}

function maybeScrobble(positionMs) {
  if (!state.settings.scrobbleEnabled || !session || session.scrobbled || !session.startedAt) return;
  const payload = payloadFor(session.track);
  if (!payload) return;
  const position = Math.max(0, Number(positionMs) || 0) / 1000;
  const minimum = Math.max(10, Number(state.settings.scrobbleMinSeconds) || 30);
  const percent = Math.min(0.95, Math.max(0.1, Number(state.settings.scrobblePercent) || 0.5));
  const duration = Math.max(0, Number(session.track?.durationMs) || 0) / 1000;
  const target = Math.min(duration > 0 ? duration * percent : Infinity, 240);
  if (position < minimum || position < target) return;
  session.scrobbled = true;
  payload.timestamp = session.startedAt;
  send(endpoints.lastfmScrobble, payload);
}

export function initLastfm() {
  document.addEventListener('aura:trackchange', (event) => {
    session = {
      track: event.detail || null,
      startedAt: 0,
      nowPlayingSent: false,
      scrobbled: false,
    };
  });

  document.addEventListener('aura:playstate', () => {
    if (!state.player.playing || !session) return;
    if (!session.startedAt) session.startedAt = Math.floor(Date.now() / 1000);
    maybeNowPlaying();
  });

  document.addEventListener('aura:time', (event) => {
    if (session) maybeScrobble(Number(event.detail?.position) || 0);
  });

  document.addEventListener('aura:favorite', (event) => {
    if (!state.settings.loveEnabled) return;
    const payload = payloadFor(event.detail?.track);
    if (!payload) return;
    const loved = Boolean(event.detail?.loved);
    send((data) => endpoints.lastfmLove(data, loved), payload);
  });
}

export function hasPendingConnection() {
  return Boolean(pendingToken);
}

/* Two-step auth: Last.fm must approve a token before it becomes a session.
 * A popup opened synchronously from the click handler is passed in, so the
 * browser's transient user activation is not lost during the token fetch. */
export async function beginLastfmConnect(popup) {
  const data = await endpoints.lastfmToken();
  pendingToken = data.token;
  if (popup) {
    try { popup.location.href = data.authUrl; }
    catch { window.open(data.authUrl, '_blank'); }
  } else {
    const opened = window.open(data.authUrl, '_blank');
    if (!opened) throw new Error('Popup blocked. Allow popups for this site and try again.');
    try { opened.opener = null; } catch { /* best effort */ }
  }
  return data;
}

export async function completeLastfmConnect() {
  if (!pendingToken) throw new Error('Open the Last.fm approval page first.');
  const data = await endpoints.lastfmSession(pendingToken);
  state.settings.lastfmSessionKey = data.sessionKey;
  state.settings.lastfmUsername = data.username;
  // A fresh connection should scrobble immediately (disconnect resets these).
  state.settings.scrobbleEnabled = true;
  state.settings.nowPlayingEnabled = true;
  persistSettings();
  emit();
  pendingToken = '';
  return data;
}

export function disconnectLastfm() {
  state.settings.lastfmSessionKey = '';
  state.settings.lastfmUsername = '';
  state.settings.scrobbleEnabled = false;
  state.settings.nowPlayingEnabled = false;
  state.settings.loveEnabled = false;
  persistSettings();
  emit();
}
