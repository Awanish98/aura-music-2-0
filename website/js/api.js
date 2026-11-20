/* Thin fetch layer: timeouts, typed errors and one bounded retry for GETs. */
export const API_BASE = (window.AURA_API_BASE || '/api').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(status, message, code = 'request_failed') {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

async function once(path, { signal, timeout = 60000, method = 'GET', body } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const onAbort = () => controller.abort();
  if (signal) signal.addEventListener('abort', onAbort, { once: true });
  try {
    const response = await fetch(API_BASE + path, {
      method,
      signal: controller.signal,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
    let payload = null;
    try { payload = await response.json(); } catch { /* non-JSON error body */ }
    if (!response.ok) {
      throw new ApiError(
        response.status,
        payload?.error || `Request failed (${response.status}).`,
        payload?.code || 'request_failed',
      );
    }
    return payload;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error?.name === 'AbortError' && signal?.aborted) throw error;
    throw new ApiError(0, 'Cannot reach the AURA backend. Check your connection and retry.', 'network_error');
  } finally {
    clearTimeout(timer);
    if (signal) signal.removeEventListener('abort', onAbort);
  }
}

export async function api(path, options = {}) {
  try {
    return await once(path, options);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 0 || error.status >= 500) && !options.signal?.aborted) {
      return once(path, options); // one retry for transient/network failures
    }
    throw error;
  }
}

export const endpoints = {
  config: () => api('/config'),
  apk: () => api('/download/apk'),
  search: (q, limit = 18) => api(`/catalog/search?q=${encodeURIComponent(q)}&limit=${limit}`),
  discover: () => api('/catalog/discover'),
  aiSuggest: (input) => api('/ai/suggest', { method: 'POST', timeout: 120000, body: input }),
  lastfmToken: () => api('/lastfm/token', { method: 'POST', timeout: 20000 }),
  lastfmSession: (token) => api('/lastfm/session', { method: 'POST', timeout: 20000, body: { token } }),
  lastfmScrobble: (input) => api('/lastfm/scrobble', { method: 'POST', timeout: 20000, body: input }),
  lastfmNowPlaying: (input) => api('/lastfm/nowplaying', { method: 'POST', timeout: 20000, body: input }),
  lastfmLove: (input, love = true) => api(love ? '/lastfm/love' : '/lastfm/unlove', { method: 'POST', timeout: 20000, body: input }),
  related: (track) => api(`/catalog/related?id=${encodeURIComponent(track.id)}&artist=${encodeURIComponent(track.artist || '')}&title=${encodeURIComponent(track.title || '')}`),
  lyrics: (track) => api(`/lyrics?title=${encodeURIComponent(track.title)}&artist=${encodeURIComponent(track.artist || '')}&duration=${Math.round(track.durationMs || 0)}`),
  streamInfo: (id) => api(`/stream/info?id=${encodeURIComponent(id)}`),
};

export function streamUrl(id) {
  return `${API_BASE}/stream/audio?id=${encodeURIComponent(id)}`;
}

export function proxyImage(url) {
  // Artwork comes from the provider's CDN directly; the service worker never
  // caches cross-origin images, and no private data is embedded in the URL.
  return url || '';
}
