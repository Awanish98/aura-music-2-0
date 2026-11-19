/* LyricsPlay — genuine LRCLIB word timings, 15 AURA looks, AUTO mood matching. */
import { state, emit } from './state.js';
import { endpoints } from './api.js';
import { icon, hydrateIcons } from './icons.js';
import { currentTrack, seek } from './player.js';

/* name|look|font|primary|accent|glow|speed|signature — exact AURA catalog. */
const CATALOG_TEXT = `
Aurora|aurora|sans|EAF2FF|6FE7FF|A78BFA|0.7|Flowing ribbons of cool light
Noir|noir|serif|F5F1E8|E8C77B|8B7355|0.45|Quiet luxury, one gold hairline
Golden Hour|golden|serif|FFF6E5|F5C86B|FF8A5B|0.6|Amber halo and letterbox bars
Neon|neon|display|F0FDFF|22D3EE|F472B6|1|Cyan tubes with magenta bloom
Ember|ember|sans|FFF4EC|FF8A4C|FF3D5A|0.9|Rising sparks and a heat haze
Prism|prism|sans|FFFFFF|7DD3FC|F0ABFC|0.85|Chromatic split around every word
Chrome|chrome|display|FFFFFF|CBD5E1|818CF8|0.8|Polished metal and extruded layers
Terminal|terminal|mono|DCFCE7|34D399|6EE7B7|0.9|Green phosphor and a block cursor
Hologram|hologram|display|F0F9FF|7DD3FC|A78BFA|1|Travelling scan bar and ghost layers
Orbit|orbit|sans|F0FDFA|5EEAD4|818CF8|0.85|Elliptical rings and a satellite dot
Tidal|tidal|sans|EFF6FF|60A5FA|C084FC|0.85|Words riding a beat-driven sine bed
Depth|depth|display|F8FAFC|94A3B8|38BDF8|0.9|Layered extrusion with parallax
Echo|echo|serif|F8FAFC|C4B5FD|67E8F9|0.8|Ghost trails and a mirrored reflection
Poster|poster|poster|FFF7ED|FFB4A2|F472B6|0.7|Bold graphic type with a hard offset
Stardust|stardust|sans|F8FAFC|E0E7FF|7DD3FC|0.8|Drifting stars and cool dust`.trim();

export const LOOKS = CATALOG_TEXT.split('\n').map((line) => {
  const [name, look, font, primary, accent, glow, speed, signature] = line.split('|');
  return { name, look, font, primary: '#' + primary, accent: '#' + accent, glow: '#' + glow, speed: +speed, signature };
});

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

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
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
  if (pref !== 'AUTO') return LOOKS.find((l) => l.look === pref) || LOOKS[0];
  const track = currentTrack();
  if (!track || !state.settings.autoMood) return LOOKS[0];
  const mood = moodFor(track);
  const group = MOOD_LOOKS[mood];
  return LOOKS.find((l) => l.look === group[hash(track.title) % group.length]) || LOOKS[0];
}

export function openLyrics() {
  overlay.hidden = false;
  overlay.classList.add('open');
  state.lyrics.open = true;
  const track = currentTrack();
  if (!track) {
    overlay.innerHTML = `<div class="lyrics-stage" data-look="aurora">
      <header class="lyrics-head"><span class="look-name">LYRICSPLAY</span>
      <button type="button" class="icon-btn" data-lyr="close" aria-label="Close LyricsPlay">${icon('close')}</button></header>
      <div class="empty-state" style="margin:auto"><strong>No track playing</strong><span>Start a song, then open LyricsPlay for synced words.</span>
      <a class="btn" href="#/search">Find music</a></div><footer class="lyrics-foot"></footer></div>`;
    hydrateIcons(overlay);
    return;
  }
  loadLyrics(track);
}

export function closeLyrics() {
  overlay.hidden = true;
  overlay.classList.remove('open');
  state.lyrics.open = false;
  menuOpen = false;
}

async function loadLyrics(track) {
  const key = track.id || track.videoId;
  if (state.lyrics.data && state.lyrics.data.__id === key) { renderLyrics(); return; }
  state.lyrics = { data: null, loading: true, error: null, look: resolvedLook(), open: true };
  renderShell(track, true);
  try {
    const data = await endpoints.lyrics(track);
    state.lyrics = { data: { ...data, __id: key }, loading: false, error: null, look: resolvedLook(), open: true };
    renderLyrics();
  } catch (error) {
    state.lyrics = { data: null, loading: false, error: error.message, look: resolvedLook(), open: true };
    renderShell(track, false);
  }
}

function lookStyle(look) {
  return `--l-primary:${look.primary};--l-accent:${look.accent};--l-glow:${look.glow};--l-speed:${look.speed}`;
}

function renderShell(track, loading) {
  const look = state.lyrics.look || resolvedLook();
  const art = track.artwork || '';
  overlay.innerHTML = `
    <div class="lyrics-stage" data-look="${look.look}" style="${lookStyle(look)}">
      <div class="lyrics-bg" style="background-image:url('${art}')"></div>
      <div class="lyrics-veil"></div>
      <header class="lyrics-head">
        <div><div class="look-name">${state.settings.lyricLook === 'AUTO' ? 'AUTO · ' + look.name : look.name}</div>
          <div class="auto-note">${escapeHtml(look.signature)}</div></div>
        <div class="lyrics-head-actions">
          <div class="look-picker">
            <button type="button" class="icon-btn" data-lyr="menu" aria-label="Choose LyricsPlay look">${icon('sparkles')}</button>
            ${menuOpen ? lookMenu(look) : ''}
          </div>
          <button type="button" class="icon-btn" data-lyr="close" aria-label="Close LyricsPlay">${icon('close')}</button>
        </div>
      </header>
      <div class="lyrics-scroll" data-role="scroll">
        ${loading ? '<div class="empty-state" style="margin:auto"><strong>Loading synced lyrics…</strong><span>Fetching real timed lyrics from the provider.</span></div>' : ''}
        ${!loading && state.lyrics.error ? `<div class="empty-state error-state" style="margin:auto"><strong>Lyrics unavailable</strong><span>${escapeHtml(state.lyrics.error)}</span><button type="button" class="btn" data-lyr="retry">${icon('refresh', 15)} Retry</button></div>` : ''}
      </div>
      <footer class="lyrics-foot">
        <div class="ltrack">${escapeHtml(track.title)} · ${escapeHtml(track.artist)}</div>
        <div class="lseek"><span class="time" data-role="cur">0:00</span>
          <input class="seek" type="range" min="0" max="1000" value="0" aria-label="Seek" data-lyr="seek">
          <span class="time" data-role="dur">0:00</span></div>
      </footer>
    </div>`;
  hydrateIcons(overlay);
  activeLine = -1; activeWord = -1; lineNodes = []; wordNodes = [];
}

function lookMenu(current) {
  const options = [{ name: 'AUTO · Mood Match', look: 'AUTO', primary: '#ED5564', accent: '#6CE5FF', glow: '#B388FF', signature: 'Picks a look from the song mood' }, ...LOOKS];
  return `<div class="look-menu" role="menu">${options.map((o) => `
    <button type="button" role="menuitem" data-lyr="look" data-look="${o.look}" class="${state.settings.lyricLook === o.look ? 'active' : ''}">
      <i style="background:linear-gradient(135deg,${o.primary},${o.accent})"></i>
      <span>${escapeHtml(o.name)}<small>${escapeHtml(o.signature || '')}</small></span>
    </button>`).join('')}</div>`;
}

function renderLyrics() {
  const track = currentTrack();
  if (!track) return;
  renderShell(track, false);
  const data = state.lyrics.data;
  const scroll = overlay.querySelector('[data-role="scroll"]');
  if (!data?.lines?.length) return;
  const note = data.approximateWords && state.settings.showWordApproxNote
    ? '<p style="color:rgba(255,255,255,.45);font-size:11px;margin:0 0 18px">Word timing is estimated from line timing for this track; line timing is exact.</p>' : '';
  scroll.innerHTML = note + data.lines.map((line, i) => `
    <div class="lyric-line" data-line="${i}" data-start="${line.startMs}" data-end="${line.endMs}">
      ${line.words.length
        ? line.words.map((w, j) => `<span class="lyric-word" data-word="${j}" data-start="${w.startMs}" data-end="${w.endMs}">${escapeHtml(w.text)} </span>`).join('')
        : escapeHtml(line.text)}
    </div>`).join('');
  lineNodes = Array.from(scroll.querySelectorAll('.lyric-line'));
  wordNodes = lineNodes.map((node) => Array.from(node.querySelectorAll('.lyric-word')));
  updateLyrics(state.player.position);
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
      wordNodes[activeLine]?.forEach((w) => w.classList.remove('sung', 'current'));
    }
    if (lineNodes[activeLine - 1]) lineNodes[activeLine - 1].classList.remove('near');
    activeLine = line;
    if (lineNodes[line]) {
      lineNodes[line].classList.add('active');
      lineNodes[line - 1]?.classList.add('near');
      lineNodes[line].scrollIntoView({ block: 'center', behavior: state.settings.reducedMotion ? 'auto' : 'smooth' });
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
  const track = currentTrack();
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
  const track = currentTrack();
  if (track) { renderShell(track, false); renderLyrics(); }
  emit();
}

function onClick(event) {
  const btn = event.target.closest('[data-lyr]');
  if (!btn) return;
  const action = btn.dataset.lyr;
  if (action === 'close') closeLyrics();
  else if (action === 'menu') { menuOpen = !menuOpen; renderShell(currentTrack(), !!state.lyrics.loading); }
  else if (action === 'retry') loadLyrics(currentTrack());
  else if (action === 'look') { menuOpen = false; setLook(btn.dataset.look); }
}

export function initLyrics() {
  overlay.addEventListener('click', onClick);
  overlay.addEventListener('input', (event) => {
    if (event.target.matches('[data-lyr="seek"]')) {
      const track = currentTrack();
      if (track) seek(Number(event.target.value) / 1000 * (track.durationMs || 0));
    }
  });
  document.addEventListener('aura:time', (event) => updateLyrics(event.detail.position));
  document.addEventListener('aura:trackchange', () => {
    activeLine = -1; activeWord = -1;
    if (state.lyrics.open) {
      const track = currentTrack();
      if (track) loadLyrics(track);
    }
  });
  document.addEventListener('aura:openlyrics', openLyrics);
}
