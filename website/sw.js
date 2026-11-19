/* AURA MUSIC 2.0 service worker.
 * Caches the application shell only. Audio, artwork from third parties and
 * private user data are deliberately never cached here.
 */
const VERSION = 'aura-web-2.0.1';
const SHELL_CACHE = `${VERSION}-shell`;
const RUNTIME_CACHE = `${VERSION}-runtime`;

const SHELL = [
  './',
  './index.html',
  './privacy.html',
  './terms.html',
  './manifest.webmanifest',
  './favicon.ico',
  './favicon-192.png',
  './favicon-512.png',
  './apple-touch-icon.png',
  './og-image.png',
  './styles/tokens.css',
  './styles/base.css',
  './styles/layout.css',
  './styles/components.css',
  './styles/player.css',
  './styles/lyrics.css',
  './js/app.js',
  './js/state.js',
  './js/api.js',
  './js/router.js',
  './js/player.js',
  './js/lyrics.js',
  './js/storage.js',
  './js/views.js',
  './js/icons.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL_CACHE && k !== RUNTIME_CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function isStaticAsset(url) {
  return ['styles/', 'js/', 'assets/'].some((prefix) => url.pathname.includes(prefix)) ||
    /\.(css|js|png|svg|webp|woff2?)$/.test(url.pathname);
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.includes('/api/')) {
    event.respondWith(fetch(req).catch(() => new Response(JSON.stringify({ error: 'Offline. Reconnect and try again.' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' }
    })));
    return;
  }
  if (url.pathname.includes('/downloads/')) return;

  if (isStaticAsset(url)) {
    // Network-first for code and styles: a deploy must reach returning users
    // immediately. Cached copies remain as an offline fallback.
    event.respondWith(
      fetch(req).then((res) => {
        const copy = res.clone();
        caches.open(RUNTIME_CACHE).then((cache) => cache.put(req, copy));
        return res;
      }).catch(() => caches.match(req).then((cached) => cached || caches.match('./index.html')))
    );
    return;
  }

  event.respondWith(
    fetch(req).then((res) => {
      const copy = res.clone();
      caches.open(RUNTIME_CACHE).then((cache) => cache.put(req, copy));
      return res;
    }).catch(() => caches.match(req).then((cached) => cached || caches.match('./index.html')))
  );
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});
