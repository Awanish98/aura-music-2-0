/* Live search suggestions from the same real catalog endpoint as full search.
 * Enter still submits the form; only an explicit suggestion pick plays audio. */
import { endpoints } from './api.js';

let uid = 0;
const cache = new Map();

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function highlight(text, query) {
  const value = String(text ?? '');
  const q = String(query ?? '').trim();
  const at = q ? value.toLowerCase().indexOf(q.toLowerCase()) : -1;
  if (at < 0) return escapeHtml(value);
  return `${escapeHtml(value.slice(0, at))}<mark>${escapeHtml(value.slice(at, at + q.length))}</mark>${escapeHtml(value.slice(at + q.length))}`;
}

/** Attach an accessible suggestion listbox to a search input.
 *  Returns a cleanup function for callers that re-render the input. */
export function attachSearchSuggestions(input, { onPick } = {}) {
  if (!input) return () => {};

  const anchor = input.closest('form') || input.parentElement;
  anchor.classList.add('suggest-anchor');

  const list = document.createElement('div');
  list.className = 'suggest-list';
  list.id = `aura-suggest-${++uid}`;
  list.setAttribute('role', 'listbox');
  list.setAttribute('aria-label', 'Song suggestions');
  list.setAttribute('aria-live', 'polite');
  list.hidden = true;
  anchor.appendChild(list);

  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('aria-controls', list.id);
  input.setAttribute('aria-expanded', 'false');

  let items = [];
  let active = -1;
  let timer = null;
  let controller = null;

  function hide() {
    clearTimeout(timer);
    list.hidden = true;
    list.innerHTML = '';
    items = [];
    active = -1;
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
  }

  function setActive(index) {
    if (!items.length) return;
    active = (index + items.length) % items.length;
    list.querySelectorAll('.suggest-item').forEach((el, i) => {
      const selected = i === active;
      el.classList.toggle('active', selected);
      el.setAttribute('aria-selected', selected ? 'true' : 'false');
    });
    input.setAttribute('aria-activedescendant', `${list.id}-opt-${active}`);
    list.querySelector('.suggest-item.active')?.scrollIntoView({ block: 'nearest' });
  }

  function render(data, query) {
    if (!input.isConnected) return;
    items = Array.isArray(data?.tracks) ? data.tracks.slice(0, 6) : [];
    active = -1;
    input.removeAttribute('aria-activedescendant');
    if (!items.length) {
      list.innerHTML = '<div class="suggest-empty">No instant matches. Press Enter to search the full catalog.</div>';
    } else {
      list.innerHTML = items.map((track, i) => `
        <button type="button" class="suggest-item" role="option" id="${list.id}-opt-${i}"
                aria-selected="false" data-index="${i}">
          <img src="${escapeHtml(track.artwork || '')}" alt="" loading="lazy">
          <span class="suggest-meta">
            <strong>${highlight(track.title, query)}</strong>
            <span>${highlight(track.artist || 'Unknown artist', query)}</span>
          </span>
        </button>`).join('');
    }
    list.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  }

  async function load(query) {
    if (controller) controller.abort();
    const mine = new AbortController();
    controller = mine;
    const key = query.toLowerCase();
    if (cache.has(key)) {
      if (controller === mine && input.isConnected) render(cache.get(key), query);
      return;
    }
    try {
      const data = await endpoints.search(query, 6, { signal: mine.signal });
      cache.set(key, data);
      if (controller === mine && input.isConnected) render(data, query);
    } catch (error) {
      if (error?.name === 'AbortError' || mine.signal.aborted || controller !== mine) return;
      if (input.isConnected) hide(); // suggestions are optional; never block search
    }
  }

  function pick(track) {
    if (!track) return;
    const queue = items.slice();
    input.value = track.title || input.value;
    hide();
    try { onPick?.(track, queue); } catch { /* picking must not break the UI */ }
  }

  function onInput() {
    clearTimeout(timer);
    if (controller) { controller.abort(); controller = null; }
    const query = input.value.trim();
    if (query.length < 2) { hide(); return; }
    timer = setTimeout(() => load(query), 220);
  }

  function onKeydown(event) {
    if (event.key === 'Escape') { hide(); return; }
    if (event.key === 'Tab') { hide(); return; }
    if (list.hidden || !items.length) {
      if (event.key === 'ArrowDown' && input.value.trim().length >= 2) {
        event.preventDefault();
        load(input.value.trim());
      }
      return;
    }
    if (event.key === 'ArrowDown') { event.preventDefault(); setActive(active + 1); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setActive(active - 1); }
    else if (event.key === 'Enter' && active >= 0) {
      event.preventDefault(); // keep Enter as full search when nothing is selected
      pick(items[active]);
    }
  }

  // Keep focus on the input while clicking an option, then play on click.
  function onPointerDown(event) {
    if (event.target.closest('.suggest-item')) event.preventDefault();
  }
  function onClick(event) {
    const option = event.target.closest('.suggest-item');
    if (!option) return;
    pick(items[Number(option.dataset.index)]);
  }
  function onBlur() {
    setTimeout(() => { if (!input.isConnected || !list.contains(document.activeElement)) hide(); }, 120);
  }

  input.addEventListener('input', onInput);
  input.addEventListener('keydown', onKeydown);
  input.addEventListener('blur', onBlur);
  list.addEventListener('pointerdown', onPointerDown);
  list.addEventListener('click', onClick);

  return () => {
    clearTimeout(timer);
    controller?.abort();
    input.removeEventListener('input', onInput);
    input.removeEventListener('keydown', onKeydown);
    input.removeEventListener('blur', onBlur);
    list.removeEventListener('pointerdown', onPointerDown);
    list.removeEventListener('click', onClick);
    list.remove();
  };
}
