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
  if (override) {
    try { localStorage.setItem('aura2:v1:apiBase', override); } catch (_) { /* private mode */ }
  }

  window.AURA_API_BASE = override || stored || defaultApi;
  window.AURA_RELEASE_DOWNLOADS = {
    apk: RELEASE_BASE + '/AURA-Music-UniversalGms-Release.apk',
    aab: RELEASE_BASE + '/AURA-Music-UniversalGms-Release.aab',
  };
  window.AURA_RELEASE_TAG = RELEASE_TAG;
})();
