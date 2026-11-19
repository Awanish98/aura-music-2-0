/* Hash routing keeps the app static-host friendly (no server rewrites needed). */
import { render, loadDiscover, loadApk, runSearch } from './views.js';
import { state, emit } from './state.js';

export function navigate(path) {
  if (location.hash === '#' + path) return;
  location.hash = '#' + path;
}

function route() {
  const path = location.hash.slice(1) || '/home';
  const [routePart, query] = path.split('?');
  render(document.getElementById('view'), path);

  if (routePart === '/home' && !state.discover && !state.discoverLoading) loadDiscover();
  if (routePart === '/download' && !state.apk) loadApk();
  if (routePart === '/search') {
    const q = new URLSearchParams(query).get('q');
    if (q && state.search.loadedQuery !== q) runSearch(q);
  }
  emit();
}

export function initRouter() {
  window.addEventListener('hashchange', route);
  document.getElementById('backBtn')?.addEventListener('click', () => history.back());
  document.getElementById('forwardBtn')?.addEventListener('click', () => history.forward());
  route();
}
