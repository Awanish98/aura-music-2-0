/* AURA MUSIC 2.0 runtime configuration. No secrets belong in this file.
 * Override the API with ?api=https://your-backend/api for a custom deployment.
 */
(function () {
  var RELEASE_TAG = 'v1.4.6';
  var RELEASE_BASE = 'https://github.com/Awanish98/aura-music-2-0/releases/download/' + RELEASE_TAG;
  var isLocal = ['127.0.0.1', 'localhost'].indexOf(location.hostname) !== -1;
  var defaultApi = isLocal
    ? (location.port === '8787' ? location.origin + '/api' : 'http://127.0.0.1:8787/api')
    : 'https://aura-music-2-0-api.onrender.com/api';

  var override = new URLSearchParams(location.search).get('api');
  var stored = null;
  try { stored = localStorage.getItem('aura2:v1:apiBase'); } catch (_) { /* private mode */ }

  // A local override saved during development must never pin the deployed app to
  // 127.0.0.1. Explicit ?api= always wins; otherwise only usable stored values apply.
  var usableStored = null;
  if (stored && !override) {
    try {
      var storedUrl = new URL(stored, location.href);
      var storedIsLocal = ['127.0.0.1', 'localhost', '[::1]'].indexOf(storedUrl.hostname) !== -1;
      // On a deployed origin a stored localhost value is stale development state.
      // On a local origin it is a valid developer override.
      if (isLocal || !storedIsLocal) usableStored = stored;
    } catch (_) { usableStored = null; }
  }
  if (override) {
    try { localStorage.setItem('aura2:v1:apiBase', override); } catch (_) { /* private mode */ }
  }

  window.AURA_API_BASE = override || usableStored || defaultApi;
  window.AURA_RELEASE_DOWNLOADS = {
    apk: RELEASE_BASE + '/AURA-Music-UniversalGms-Release.apk',
    aab: RELEASE_BASE + '/AURA-Music-UniversalGms-Release.aab',
  };
  window.AURA_RELEASE_TAG = RELEASE_TAG;
})();
