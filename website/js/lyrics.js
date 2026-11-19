/* LyricsPlay — genuine LRCLIB word timings, 15 AURA looks, 2 cinematic views, AUTO mood matching. */
import { state, emit } from './state.js';
import { endpoints } from './api.js';
import { icon, hydrateIcons } from './icons.js';
import { currentTrack, seek, playTrack } from './player.js';

/* name|look|font|primary|accent|glow|speed|signature — exact AURA catalog. */
const CATALOG_TEXT = `
Aurora|aurora|sans|EAF2FF|6FE7FF|A78BFA|0.7|Flowing ribbons of cool light
Noir|noir|serif|F5F1E8|E8C77B|8B7355|0.45|Quiet luxury, one gold hairline
Golden Hour|golden|serif|FFF6E5|F5C86B|FF8A5B|0.6|Amber halo and letterbox bars
Neon|neon|display|F0FDFF|22D3EE|F472B6|1|Cyan tubes with magenta bloom
Ember|ember|sans|FFF4EC|FF8A4C|FF3D5A|0.9|Rising sparks and a heat haze
Prism|prism|sans|FFFFFF|7DD3FC|F0ABFC|0.85|Chromatic split around every word
Chrome|chrome|display|FFFFFF|CBD5E1|818CF6|0.8|Polished metal and extruded layers
Terminal|terminal|mono|DCFCE7|34D399|6EE7B7|0.9|Green phosphor and a block cursor
Hologram|hologram|display|F0F9FF|7DD3FC|A78BFA|1|Travelling scan bar and ghost layers
Orbit|orbit|sans|F0FDFA|5EEAD4|818CF6|0.85|Elliptical rings and a satellite dot
Tidal|tidal|sans|EFF6FF|60A5FA|C084FC|0.85|Words riding a beat-driven sine bed
Depth|depth|display|F8FAFC|94A3B8|38BDF8|0.9|Layered extrusion with parallax
Echo|echo|serif|F8FAFC|C4B5FD|67E8F9|0.8|Ghost trails and a mirrored reflection
Poster|poster|poster|FFF7ED|FFB4A2|F472B6|0.7|Bold graphic type with a hard offset
Stardust|stardust|sans|F8FAFC|E0E7FF|7DD3FC|0.8|Drifting stars and cool dust`.trim();

export const LOOKS = CATALOG_TEXT.split('\n').map((line) => {
  const [name, look, font, primary, accent, glow, speed, signature] = line.split('|');
  return { name, look, font, primary: '#' + primary, accent: '#' + accent, glow: '#' + glow, speed: +speed, signature };
});

/* Two dedicated Android-equivalent views. They are separate from the 15 looks. */
export const VIEW_MODES = [
  { id: 'CINEMATIC', name: 'Cinematic', look: 'cinema', primary: '#FFF7ED', accent: '#F5C86B', glow: '#FF8A5B', speed: 0.6, signature: 'Letterboxed light and a warm film halo' },
  { id: 'AMOLED', name: 'AMOLED', look: 'amoled', primary: '#FFFFFF', accent: '#C4B5FD', glow: '#6D28D9', speed: 0.45, signature: 'True black, pure white, minimal bloom' },
];
const MODE_BY_ID = Object.fromEntries(VIEW_MODES.map((mode) => [mode.id, mode]));

const MOOD_LOOKS = {
  SAD: ['echo', 'stardust', 'noir'],
  LOVE: ['aurora', 'orbit', 'golden'],
  CHILL: ['tidal', 'hologram', 'depth'],
  PARTY: ['neon', 'poster', 'prism'],
  DARK: ['terminal', 'chrome', 'ember'],
};

const overlay = document.getElementById('lyricsOverlay');
let activeLine = -1;
let activeWord = -1;
let lineNodes = [];
let wordNodes = [];
let menuOpen = false;
let previewTrack = null;
let lyricRequest = 0;
let lastFocus = null;
let searchQuery = '';
let searchResults = [];
let searchError = '';
let searchLoading = false;

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function idOf(track) {
  return track?.id || track?.videoId || '';
}

function activeTrack() {
  return previewTrack || currentTrack();
}

function isPreview() {
  const track = activeTrack();
  const playing = currentTrack();
  return !!track && (!playing || idOf(playing) !== idOf(track));
}

function hash(text) {
  let h = 0;
  for (const ch of text) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h;
}

function moodFor(track) {
  const text = `${track.title} ${track.artist}`.toLowerCase();
  if (/sad|lonely|heartbreak|goodbye|miss|cry|broken|grief/.test(text)) return 'SAD';
  if (/love|kiss|baby|darling|romance|forever|you/.test(text)) return 'LOVE';
  if (/chill|lofi|lo-fi|calm|study|rain|sleep|soft|ambient/.test(text)) return 'CHILL';
  if (/party|dance|club|bass|festival|electr|beat|drop/.test(text)) return 'PARTY';
  if (/dark|night|shadow|gothic|black|evil|rage/.test(text)) return 'DARK';
  return ['SAD', 'LOVE', 'CHILL', 'PARTY', 'DARK'][hash(text) % 5];
}

function resolvedLook() {
  const pref = state.settings.lyricLook || 'AUTO';
  const mode = MODE_BY_ID[pref];
  if (mode) return { ...mode, mode: mode.look };
  if (pref !== 'AUTO') return LOOKS.find((l) => l.look === pref) || LOOKS[0];
  const track = currentTrack();
  if (!track || !state.settings.autoMood) return LOOKS[0];
  const mood = moodFor(track);
  const group = MOOD_LOOKS[mood];
  return LOOKS.find((l) => l.look === group[hash(track.title) % group.length]) || LOOKS[0];
}

export function openLyrics(track = null) {
  if (!state.lyrics.open) {
    lastFocus = document.activeElement;
    state.lyrics.open = true;
    overlay.hidden = false;
    overlay.classList.add('open');
    document.body.classList.add('overlay-open');
  }
  previewTrack = track || previewTrack;
  const selected = activeTrack();
  if (!selected) renderSearch();
  else loadLyrics(selected);
  const target = overlay.querySelector('[data-lyr="close"]') || overlay.querySelector('[data-lyr="search-input"]');
  target?.focus();
}

export function closeLyrics() {
  overlay.hidden = true;
  overlay.classList.remove('open');
  document.body.classList.remove('overlay-open');
  state.lyrics.open = false;
  menuOpen = false;
  previewTrack = null;
  lyricRequest += 1;
  if (lastFocus?.isConnected) lastFocus.focus();
}

async function loadLyrics(track) {
  if (!track) return renderSearch();
  const token = ++lyricRequest;
  const key = idOf(track);
  if (state.lyrics.data && state.lyrics.data.__id === key) { renderLyrics(track); return; }
  state.lyrics = { data: null, loading: true, error: null, look: resolvedLook(), open: true };
  renderShell(track, true);
  try {
    const data = await endpoints.lyrics(track);
    if (token !== lyricRequest) return;
    state.lyrics = { data: { ...data, __id: key }, loading: false, error: null, look: resolvedLook(), open: true };
    renderLyrics(track);
    emit();
  } catch (error) {
    if (token !== lyricRequest) return;
    state.lyrics = { data: null, loading: false, error: error.message, look: resolvedLook(), open: true };
    renderShell(track, false);
  }
}

function lookStyle(look) {
  return `--l-primary:${look.primary};--l-accent:${look.accent};--l-glow:${look.glow};--l-speed:${look.speed}`;
}

function lookMenu() {
  const options = [
    { name: 'AUTO · Mood Match', look: 'AUTO', primary: '#ED5564', accent: '#6CE5FF', signature: 'Picks a look from the song mood' },
    ...VIEW_MODES.map((mode) => ({ ...mode, look: mode.id })),
    ...LOOKS,
  ];
  return `<div class="look-menu" role="menu" aria-label="LyricsPlay looks and views">${options.map((o) => `
    <button type="button" role="menuitem" data-lyr="look" data-look="${escapeHtml(o.look)}" class="${state.settings.lyricLook === o.look ? 'active' : ''}">
      <i style="background:linear-gradient(135deg,${o.primary},${o.accent})"></i>
      <span>${escapeHtml(o.name)}<small>${escapeHtml(o.signature || '')}</small></span>
      ${o.mode ? '<b>VIEW</b>' : ''}
    </button>`).join('')}</div>`;
}

function stageHeader(look) {
  const label = state.settings.lyricLook === 'AUTO' ? 'AUTO · ' + look.name : look.name;
  return `<header class="lyrics-head">
    <div class="lhead-copy">
      <div class="look-name">${escapeHtml(label)}</div>
      <div class="auto-note">${escapeHtml(look.signature || '')}</div>
      <span class="view-badge">${look.mode ? escapeHtml(look.name.toUpperCase()) + ' VIEW' : '15 SIGNATURE LOOKS'}</span>
    </div>
    <div class="lyrics-head-actions">
      <button type="button" class="icon-btn" data-lyr="find" aria-label="Find other lyrics" title="Find other lyrics">${icon('search')}</button>
      <div class="look-picker">
        <button type="button" class="icon-btn" data-lyr="menu" aria-label="Choose LyricsPlay look" aria-expanded="${menuOpen}">${icon('sparkles')}</button>
        ${menuOpen ? lookMenu() : ''}
      </div>
      <button type="button" class="icon-btn" data-lyr="close" aria-label="Close LyricsPlay">${icon('close')}</button>
    </div>
  </header>`;
}

function renderShell(track, loading) {
  const look = state.lyrics.look || resolvedLook();
  const art = track?.artwork || '';
  const preview = isPreview();
  const foot = preview
    ? `<div class="ltrack">${escapeHtml(track?.title || '')} · ${escapeHtml(track?.artist || '')} · preview</div>
       <button type="button" class="btn primary sm" data-lyr="play-preview">${icon('play', 15)} Play this song</button>`
    : `<div class="ltrack">${escapeHtml(track?.title || '')} · ${escapeHtml(track?.artist || '')}</div>
       <div class="lseek"><span class="time" data-role="cur">0:00</span>
         <input class="seek" type="range" min="0" max="1000" value="0" aria-label="Seek" data-lyr="seek">
         <span class="time" data-role="dur">0:00</span></div>`;
  overlay.innerHTML = `
    <div class="lyrics-stage" data-look="${escapeHtml(look.look)}" data-mode="${escapeHtml(look.mode || 'look')}" style="${lookStyle(look)}">
      <div class="lyrics-bg" style="background-image:url('${escapeHtml(art)}')"></div>
      <div class="lyrics-veil"></div>
      ${stageHeader(look)}
      <div class="lyrics-scroll" data-role="scroll" role="region" aria-label="Synchronized lyrics">
        <p class="sr-only" data-role="status" role="status" aria-live="polite"></p>
        ${loading ? `<div class="empty-state lyrics-loading" style="margin:auto"><span class="icon-badge">${icon('lyrics', 26)}</span><strong>Loading synced lyrics…</strong><span>Fetching real timed lyrics from the provider.</span></div>` : ''}
        ${!loading && state.lyrics.error ? `<div class="empty-state error-state" style="margin:auto"><strong>Lyrics unavailable</strong><span>${escapeHtml(state.lyrics.error)}</span>
          <div class="hero-actions"><button type="button" class="btn" data-lyr="retry">${icon('refresh', 15)} Retry</button>
          <button type="button" class="btn" data-lyr="find">Search another song</button></div></div>` : ''}
      </div>
      <footer class="lyrics-foot">${foot}</footer>
    </div>`;
  hydrateIcons(overlay);
  activeLine = -1; activeWord = -1; lineNodes = []; wordNodes = [];
}

function renderLyrics(track = activeTrack()) {
  if (!track) return renderSearch();
  renderShell(track, false);
  const data = state.lyrics.data;
  const scroll = overlay.querySelector('[data-role="scroll"]');
  if (!scroll || !data?.lines?.length) return;
  const note = data.approximateWords && state.settings.showWordApproxNote
    ? '<p class="word-note">Word timing is estimated from line timing for this track; line timing is exact.</p>' : '';
  scroll.insertAdjacentHTML('beforeend', note + data.lines.map((line, i) => `
    <div class="lyric-line" data-line="${i}" data-start="${Number(line.startMs)}" data-end="${Number(line.endMs)}">
      ${line.words.length
        ? line.words.map((w, j) => `<span class="lyric-word" data-word="${j}" data-start="${Number(w.startMs)}" data-end="${Number(w.endMs)}">${escapeHtml(w.text)} </span>`).join('')
        : escapeHtml(line.text)}
    </div>`).join(''));
  lineNodes = Array.from(scroll.querySelectorAll('.lyric-line'));
  wordNodes = lineNodes.map((node) => Array.from(node.querySelectorAll('.lyric-word')));
  const status = overlay.querySelector('[data-role="status"]');
  if (status) status.textContent = `Lyrics loaded for ${track.title} — ${data.lines.length} lines.`;
  updateLyrics(isPreview() ? 0 : state.player.position);
}

function renderSearch(query = searchQuery, results = searchResults, error = searchError, loading = searchLoading) {
  const look = resolvedLook();
  const resultsHtml = loading
    ? `<div class="lyric-results">${Array.from({ length: 3 }, () => '<div class="lyric-result skeleton-row"></div>').join('')}</div>`
    : results.length
      ? `<div class="lyric-results">${results.map((track) => `
          <button type="button" class="lyric-result" data-lyr="search-result" data-id="${escapeHtml(idOf(track))}">
            <img src="${escapeHtml(track.artwork || '')}" alt="" loading="lazy" decoding="async">
            <span><strong>${escapeHtml(track.title)}</strong><small>${escapeHtml(track.artist)}</small></span>
            ${icon('chevronRight', 16)}
          </button>`).join('')}</div>`
      : '';
  overlay.innerHTML = `
    <div class="lyrics-stage lyrics-search-stage" data-look="${escapeHtml(look.look)}" data-mode="${escapeHtml(look.mode || 'look')}" style="${lookStyle(look)}">
      <div class="lyrics-bg" style="background-image:url('')"></div>
      <div class="lyrics-veil"></div>
      ${stageHeader(look)}
      <div class="lyrics-scroll" data-role="scroll">
        <div class="lyrics-search-card">
          <div class="eyebrow">LYRICSPLAY · STANDALONE SEARCH</div>
          <h2>Find lyrics, even before playback</h2>
          <p>Search the real catalog and preview word-synced lyrics. Starting a song is optional.</p>
          <form class="lyrics-search-form" data-lyr="search" role="search">
            <span data-icon="search" aria-hidden="true"></span>
            <input type="search" data-lyr="search-input" value="${escapeHtml(query)}" placeholder="Song or artist…" aria-label="Search lyrics by song or artist" autocomplete="off">
            <button type="submit" class="btn primary sm">Find lyrics</button>
          </form>
          ${error ? `<div class="lyric-search-error" role="alert"><span>${escapeHtml(error)}</span><button type="button" class="btn sm" data-lyr="search-retry">Retry</button></div>` : ''}
          ${resultsHtml}
        </div>
      </div>
      <footer class="lyrics-foot"><div class="ltrack">Real timed lyrics from LRCLIB · no playback required for preview</div></footer>
    </div>`;
  hydrateIcons(overlay);
  overlay.querySelector('[data-lyr="search-input"]')?.focus();
}

async function runLyricSearch(query) {
  searchQuery = query;
  searchResults = [];
  searchError = '';
  searchLoading = true;
  renderSearch();
  try {
    const data = await endpoints.search(query, 8);
    searchResults = Array.isArray(data?.tracks) ? data.tracks : [];
  } catch (error) {
    searchError = error.message || 'Search is unavailable right now.';
  } finally {
    searchLoading = false;
    if (state.lyrics.open && !activeTrack()) renderSearch();
  }
}

export function updateLyrics(positionMs) {
  if (!state.lyrics.open || !lineNodes.length) return;
  let line = -1;
  for (let i = 0; i < lineNodes.length; i++) {
    if (positionMs >= Number(lineNodes[i].dataset.start)) line = i; else break;
  }
  if (line !== activeLine) {
    if (lineNodes[activeLine]) {
      lineNodes[activeLine].classList.remove('active');
      lineNodes[activeLine].removeAttribute('aria-current');
      wordNodes[activeLine]?.forEach((w) => w.classList.remove('sung', 'current'));
    }
    lineNodes[activeLine - 1]?.classList.remove('near');
    activeLine = line;
    if (lineNodes[line]) {
      lineNodes[line].classList.add('active');
      lineNodes[line].setAttribute('aria-current', 'true');
      lineNodes[line - 1]?.classList.add('near');
      const reduced = state.settings.reducedMotion || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      lineNodes[line].scrollIntoView({ block: 'center', behavior: reduced ? 'auto' : 'smooth' });
    }
  }
  const words = wordNodes[line] || [];
  let word = -1;
  for (let i = 0; i < words.length; i++) {
    if (positionMs >= Number(words[i].dataset.start)) word = i; else break;
  }
  words.forEach((w, i) => {
    w.classList.toggle('sung', i < word);
    w.classList.toggle('current', i === word);
  });
  activeWord = word;
  const track = activeTrack();
  const dur = track?.durationMs || 0;
  const seekEl = overlay.querySelector('[data-lyr="seek"]');
  if (seekEl && dur) seekEl.value = String(Math.min(1000, Math.round(positionMs / dur * 1000)));
  const cur = overlay.querySelector('[data-role="cur"]');
  if (cur) cur.textContent = fmt(positionMs);
  const durEl = overlay.querySelector('[data-role="dur"]');
  if (durEl) durEl.textContent = fmt(dur);
}

function fmt(ms) {
  const s = Math.floor((ms || 0) / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function setLook(lookId) {
  state.settings.lyricLook = lookId;
  state.lyrics.look = resolvedLook();
  import('./storage.js').then(({ save }) => save('settings', state.settings));
  const track = activeTrack();
  if (state.lyrics.open) {
    if (track && state.lyrics.data) renderLyrics(track);
    else if (track) renderShell(track, !!state.lyrics.loading);
    else renderSearch();
  }
  emit();
}

function onClick(event) {
  const btn = event.target.closest('[data-lyr]');
  if (!btn) return;
  const action = btn.dataset.lyr;
  if (action === 'close') closeLyrics();
  else if (action === 'menu') {
    menuOpen = !menuOpen;
    const track = activeTrack();
    if (track) renderShell(track, !!state.lyrics.loading); else renderSearch();
  } else if (action === 'retry') loadLyrics(activeTrack());
  else if (action === 'look') { menuOpen = false; setLook(btn.dataset.look); }
  else if (action === 'find') {
    lyricRequest += 1;
    previewTrack = null;
    menuOpen = false;
    state.lyrics = { data: null, loading: false, error: null, look: resolvedLook(), open: true };
    renderSearch();
  } else if (action === 'search-retry') runLyricSearch(searchQuery);
  else if (action === 'search-result') {
    const track = searchResults.find((t) => idOf(t) === btn.dataset.id);
    if (track) { previewTrack = track; loadLyrics(track); }
  } else if (action === 'play-preview') {
    const track = activeTrack();
    if (track) { previewTrack = null; playTrack(track, [track], 'LyricsPlay'); }
  }
}

function trapFocus(event) {
  if (event.key !== 'Tab') return;
  const focusable = Array.from(overlay.querySelectorAll('button:not([disabled]), input:not([disabled]), a[href]'))
    .filter((el) => el.offsetParent !== null);
  if (!focusable.length) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
}

export function initLyrics() {
  overlay.addEventListener('click', onClick);
  overlay.addEventListener('keydown', trapFocus);
  overlay.addEventListener('submit', (event) => {
    if (!event.target.matches('[data-lyr="search"]')) return;
    event.preventDefault();
    const query = overlay.querySelector('[data-lyr="search-input"]')?.value.trim();
    if (query) runLyricSearch(query);
  });
  overlay.addEventListener('input', (event) => {
    if (event.target.matches('[data-lyr="seek"]')) {
      const track = currentTrack();
      if (track) seek(Number(event.target.value) / 1000 * (track.durationMs || 0));
    }
  });
  document.addEventListener('aura:time', (event) => updateLyrics(event.detail.position));
  document.addEventListener('aura:trackchange', () => {
    previewTrack = null;
    activeLine = -1; activeWord = -1;
    if (state.lyrics.open) {
      const track = currentTrack();
      if (track) loadLyrics(track);
      else { state.lyrics.data = null; renderSearch(); }
    }
  });
  document.addEventListener('aura:openlyrics', (event) => openLyrics(event.detail?.track || null));
}
