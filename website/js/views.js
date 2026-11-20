/* All application views. Data is always real: provider search, local files or local storage. */
import { state, emit, persistLibrary, persistSettings, isFavorite, toggleFavorite, findTrack, makePlaylist, addToPlaylist, removeFromPlaylist, deletePlaylist, toast } from './state.js';
import { endpoints } from './api.js';
import { icon, hydrateIcons } from './icons.js';
import { LOOKS, VIEW_MODES, MOBILE_STYLES, setLook, setEngine, setMobileStyle } from './lyrics.js';
import { playQueue, playTrack, addToQueue, removeFromQueue, currentTrack, togglePlay, openFullPlayer } from './player.js';
import { beginLastfmConnect, completeLastfmConnect, disconnectLastfm, hasPendingConnection } from './lastfm.js';
import { attachSearchSuggestions } from './suggest.js';

let activeNav = '';

const LOOK_OPTIONS = [
  { name: 'AUTO · Mood', look: 'AUTO', primary: '#ED5564', accent: '#6CE5FF', signature: 'Matches the mood of the playing song' },
  ...VIEW_MODES.map((mode) => ({ ...mode, name: `${mode.name} view`, look: mode.id })),
  ...LOOKS,
];

const MOBILE_OPTIONS = MOBILE_STYLES.map((style) => ({
  name: style.name, look: style.id, primary: style.primary,
  accent: style.accent, signature: style.signature,
}));

const MOOD_CHIPS = [
  { mood: 'CHILL', label: 'Chill', emoji: '🌙' },
  { mood: 'LO_FI', label: 'Lo-fi focus', emoji: '📚' },
  { mood: 'PARTY', label: 'Party', emoji: '🎉' },
  { mood: 'ROMANTIC', label: 'Romantic', emoji: '💗' },
  { mood: 'SAD', label: 'Melancholy', emoji: '🌧️' },
  { mood: 'MOTIVATIONAL', label: 'Workout', emoji: '🔥' },
  { mood: 'DREAMY', label: 'Dreamy', emoji: '✨' },
  { mood: 'DARK', label: 'Dark', emoji: '🖤' },
  { mood: 'ENERGETIC', label: 'High energy', emoji: '⚡' },
  { mood: 'EMOTIONAL', label: 'Emotional', emoji: '💫' },
  { mood: 'AGGRESSIVE', label: 'Heavy', emoji: '🥁' },
  { mood: 'NEUTRAL', label: 'Feel good', emoji: '🎶' },
];

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function fmt(ms) {
  const s = Math.floor((ms || 0) / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function art(track, cls = '') {
  return `<img src="${esc(track.artwork)}" alt="" loading="lazy" decoding="async" class="${cls}">`;
}

function trackRow(track, index, options = {}) {
  const id = track.id || track.videoId;
  const fav = isFavorite(id);
  const playing = (currentTrack()?.id || currentTrack()?.videoId) === id;
  return `<div class="track-row ${playing ? 'current' : ''}" data-track="${esc(id)}">
    <span class="idx">${String(index + 1).padStart(2, '0')}</span>
    ${art(track, 'thumb')}
    <div class="meta"><strong>${esc(track.title)}</strong><span>${esc(track.artist)}${track.album ? ' · ' + esc(track.album) : ''}</span></div>
    <div class="row-actions">
      <button type="button" class="icon-btn tiny ${fav ? 'on' : ''}" data-action="fav" data-id="${esc(id)}" aria-label="${fav ? 'Remove favorite' : 'Add favorite'}" aria-pressed="${fav}">${icon('heart', 16)}</button>
      <button type="button" class="icon-btn tiny" data-action="nextplay" data-id="${esc(id)}" aria-label="Play next">${icon('plus', 16)}</button>
      <button type="button" class="icon-btn tiny" data-action="playlist-add" data-id="${esc(id)}" aria-label="Add to playlist">${icon('playlist', 16)}</button>
    </div>
    <span class="dur-txt">${fmt(track.durationMs)}</span>
  </div>`;
}

function trackCard(track) {
  return `<article class="track-card" data-track="${esc(track.id || track.videoId)}" tabindex="0" role="button" aria-label="Play ${esc(track.title)}">
    <div class="art">${art(track)}<span class="dur">${fmt(track.durationMs)}</span>
      <span class="play-fab">${icon('play', 20)}</span></div>
    <div class="t">${esc(track.title)}</div><div class="s">${esc(track.artist)}</div>
  </article>`;
}

function section(title, subtitle, tracks, link = '') {
  return `<section class="view-section">
    <div class="section-head"><div><div class="eyebrow">${esc(subtitle || 'AURA SELECTION')}</div><h2>${esc(title)}</h2></div>
      ${link ? `<a class="link" href="${link}">View all →</a>` : ''}</div>
    <div class="card-row">${tracks.slice(0, 12).map(trackCard).join('')}</div>
  </section>`;
}

function empty(title, message, action = '') {
  return `<div class="empty-state"><span class="icon-badge">${icon('music', 28)}</span><strong>${esc(title)}</strong><span>${esc(message)}</span>${action}</div>`;
}

function errorState(message, action = 'data-action="retry-discover"') {
  return `<div class="empty-state error-state"><span class="icon-badge">${icon('info', 28)}</span><strong>Could not load music</strong><span>${esc(message)}</span>
    <button type="button" class="btn" ${action}>${icon('refresh', 15)} Retry</button></div>`;
}

function skeletonCards() {
  return `<div class="card-row">${Array.from({ length: 7 }, () => `<div class="track-card"><div class="art skeleton"></div><div class="skeleton" style="height:12px;width:80%;margin-top:10px"></div><div class="skeleton" style="height:10px;width:55%;margin-top:6px"></div></div>`).join('')}</div>`;
}

/* ---------------------------------------------------------------- home */
function renderHome(root) {
  const d = state.discover;
  const recent = state.library.recent.slice(0, 6);
  root.innerHTML = `
    <section class="hero">
      <div class="hero-copy">
        <div class="eyebrow">AURA MUSIC 2.0 · PREMIUM WEB ECOSYSTEM</div>
        <h1>Every song.<br><em>Every feeling.</em><br>One Aura.</h1>
        <p>Real music search, a cinematic player, genuine word-synced LyricsPlay and your library — the Android experience, reimagined for the web.</p>
        <div class="hero-actions">
          <a class="btn primary" href="#/search">${icon('search', 17)} Explore music</a>
          <button type="button" class="btn" data-action="surprise">${icon('sparkles', 17)} Surprise me</button>
          <a class="btn ghost" href="#/download">${icon('download', 17)} Get the app</a>
        </div>
        <div class="hero-meta"><span>REAL CATALOG</span><span>WORD-SYNC LYRICS</span><span>NO LOGIN REQUIRED</span></div>
      </div>
      <div class="hero-art" aria-hidden="true"><div class="hero-orbs"><i></i><i></i><i></i><span class="hero-core"></span></div></div>
    </section>
    <section class="view-section ai-home">
      <div class="section-head"><div><div class="eyebrow">AI MOOD RADIO · NEW</div><h2>What do you feel like hearing?</h2></div>
        <a class="link" href="#/mood">Open Mood Radio →</a></div>
      <p class="ai-home-sub">Pick a feeling. AURA builds a real, playable mix and can open it in cinematic LyricsPlay.</p>
      <div class="mood-chips compact">${MOOD_CHIPS.slice(0, 6).map((m) => `<button type="button" class="mood-chip" data-action="ai-mood" data-value="${esc(m.mood)}"><span aria-hidden="true">${m.emoji}</span><strong>${esc(m.label)}</strong></button>`).join('')}</div>
    </section>
    ${recent.length ? section('Continue listening', 'YOUR RECENT PLAYS', recent) : ''}
    ${d ? d.sections.map((s, i) => section(s.title, i === 0 ? 'TRENDING NOW · REAL RESULTS' : 'AURA DISCOVERY', s.tracks, `#/search?q=${encodeURIComponent(s.title)}`)).join('')
      : state.discoverLoading ? `<div class="view-section"><div class="section-head"><h2>Discover</h2></div>${skeletonCards()}</div>`
      : state.discoverError ? errorState(state.discoverError) : `<div class="view-section">${empty('Catalog is loading', 'Fetching live music sections from the authorized provider.', '<button class="btn" data-action="retry-discover">Load catalog</button>')}</div>`}`;
}

export async function loadDiscover(force = false) {
  if (state.discover && !force) return;
  state.discoverLoading = true; state.discoverError = null;
  if (state.route === '/home') render(document.getElementById('view'), '/home');
  try {
    state.discover = await endpoints.discover();
  } catch (error) {
    state.discoverError = error.message;
  } finally {
    state.discoverLoading = false;
    if (state.route === '/home') render(document.getElementById('view'), '/home');
  }
}

/* --------------------------------------------------------------- search */
function renderSearch(root, params) {
  const activeInput = document.activeElement;
  const keepFocus = activeInput?.id === 'searchInput';
  const caret = keepFocus ? [activeInput.selectionStart, activeInput.selectionEnd] : null;
  const q = params.get('q') || state.search.query || '';
  state.search.query = q;
  const s = state.search;
  const tracks = s.results?.tracks || [];
  let shown = tracks.slice();
  if (s.filter === 'long') shown = shown.filter((t) => t.durationMs >= 240000);
  if (s.filter === 'quick') shown = shown.filter((t) => t.durationMs < 240000);
  if (s.sort === 'duration') shown.sort((a, b) => a.durationMs - b.durationMs);
  if (s.sort === 'az') shown.sort((a, b) => a.title.localeCompare(b.title));
  if (s.sort === 'artist') shown.sort((a, b) => a.artist.localeCompare(b.artist));

  root.innerHTML = `<div class="search-page">
    <form class="big-search" id="searchForm" role="search">
      ${icon('search', 22)}
      <input id="searchInput" type="search" value="${esc(q)}" placeholder="Search millions of songs, artists and moods…" aria-label="Search music" autocomplete="off" enterkeyhint="search">
      <button type="submit" class="btn primary sm">Search</button>
    </form>
    <div class="toolbar">
      <div class="chip-row">
        ${[['all', 'All results'], ['long', 'Long form'], ['quick', 'Quick picks']].map(([f, label]) => `<button type="button" class="chip ${s.filter === f ? 'active' : ''}" data-action="filter" data-value="${f}">${label}</button>`).join('')}
      </div>
      <label>Sort <select data-action="sort">${[['relevance', 'Relevance'], ['duration', 'Duration'], ['az', 'Title A–Z'], ['artist', 'Artist']].map(([v, l]) => `<option value="${v}" ${s.sort === v ? 'selected' : ''}>${l}</option>`).join('')}</select></label>
    </div>
    ${s.loading ? `<div class="view-section">${skeletonCards()}</div>`
      : s.error ? errorState(s.error, 'data-action="retry-search"')
      : !q ? empty('Search the catalog', 'Type a song, artist, album or mood to find real, playable results.')
      : !shown.length ? empty('No matching tracks', 'Try a different spelling or a broader search.')
      : `<div class="section-head"><div><div class="eyebrow">RESULTS · SOURCE: ${esc(s.results?.source || 'YouTube')}</div><h2>${shown.length} result${shown.length === 1 ? '' : 's'} for “${esc(q)}”</h2></div>
          <button type="button" class="btn sm" data-action="playall">${icon('play', 15)} Play all</button></div>
        <div class="track-list">${shown.map((t, i) => trackRow(t, i)).join('')}</div>
        <p style="margin-top:16px;font-size:11px;color:var(--faint)">Results are real provider matches. Titles may differ slightly from official metadata.</p>`}
  </div>`;
  root.querySelector('#searchForm')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const value = root.querySelector('#searchInput')?.value.trim();
    if (value) runSearch(value);
  });
  attachSearchSuggestions(root.querySelector('#searchInput'), {
    onPick: (track, queue) => playTrack(track, queue, 'Search suggestion'),
  });
  if (keepFocus) {
    const input = root.querySelector('#searchInput');
    input?.focus();
    if (input && caret && caret[0] != null) input.setSelectionRange(caret[0], caret[1]);
  }
}

export async function runSearch(query) {
  const q = String(query || '').trim();
  if (!q) return;
  state.search.query = q;
  state.search.loadedQuery = q;
  state.search.loading = true; state.search.error = null;
  const path = `/search?q=${encodeURIComponent(q)}`;
  // Setting the hash fires the router, which renders the search view from any
  // route. replaceState() did not fire hashchange, so home searches never left
  // the home view. Keep the hash change for back/forward history support.
  if (location.hash.slice(1) !== path) location.hash = '#' + path;
  else render(document.getElementById('view'), path);
  try {
    state.search.results = await endpoints.search(q, 24);
  } catch (error) {
    state.search.error = error.message;
  } finally {
    state.search.loading = false;
    if (location.hash.slice(1) === path) render(document.getElementById('view'), path);
  }
}

/* -------------------------------------------------------------- library */
function renderLibrary(root, params) {
  const tab = params.get('tab') || 'songs';
  const lib = state.library;
  const playlists = lib.playlists;
  const favTracks = lib.favorites.map(findTrack).filter(Boolean);
  const historyTracks = lib.history.map(findTrack).filter(Boolean);
  const allTracks = [...lib.recent, ...historyTracks, ...favTracks, ...(state.search.results?.tracks || [])];
  const unique = allTracks.filter((t, i, arr) => arr.findIndex((x) => (x.id || x.videoId) === (t.id || t.videoId)) === i);
  let list = unique;
  if (tab === 'favorites') list = favTracks;
  if (tab === 'history') list = historyTracks;
  if (tab === 'playlists') list = [];
  if (tab === 'files') list = lib.files.map((f) => ({ ...f, artwork: '', provider: 'local' }));

  root.innerHTML = `
    <div class="section-head"><div><div class="eyebrow">YOUR MUSIC · SAVED LOCALLY</div><h1>Your library</h1></div>
      <button type="button" class="btn primary sm" data-action="playlist-new">${icon('plus', 15)} New playlist</button></div>
    <div class="tabs" role="tablist">
      ${[['songs', 'Songs'], ['playlists', 'Playlists'], ['favorites', 'Favorites'], ['history', 'History'], ['files', 'Local files']].map(([v, l]) =>
        `<button type="button" role="tab" class="tab ${tab === v ? 'active' : ''}" data-action="libtab" data-value="${v}">${l}</button>`).join('')}
    </div>
    ${tab === 'playlists' ? `<div class="look-grid">${playlists.length ? playlists.map((p) => `
      <a class="panel" href="#/playlist/${encodeURIComponent(p.id)}" style="min-height:120px">
        <div class="eyebrow">${p.tracks.length} TRACKS</div><h3>${esc(p.name)}</h3>
        <p style="font-size:11px;margin:6px 0 0">${esc(p.tracks.slice(0, 2).map((t) => t.title).join(' · ') || 'Empty playlist')}</p></a>`).join('')
      : empty('No playlists yet', 'Create a playlist to organize favorites and discoveries.')}</div>`
      : list.length ? `<div class="toolbar"><span style="color:var(--muted);font-size:12px">${list.length} item${list.length === 1 ? '' : 's'}</span>
          <button type="button" class="btn sm" data-action="playall">${icon('play', 15)} Play all</button></div>
        <div class="track-list">${list.map((t, i) => trackRow(t, i)).join('')}</div>`
      : empty(tab === 'favorites' ? 'No favorites yet' : tab === 'history' ? 'No play history yet' : 'Your library is empty',
        tab === 'files' ? 'Import your own audio files in Offline & files.' : 'Search for music and save what you love.')}`;
}

/* ----------------------------------------------------------- detail views */
function detailShell(kind, title, subtitle, artwork, tracks, actions = '') {
  return `<div class="detail-hero">
      ${artwork ? `<img src="${esc(artwork)}" alt="">` : `<div class="art skeleton" style="width:190px;height:190px"></div>`}
      <div><div class="kind">${esc(kind)}</div><h1>${esc(title)}</h1>
        <div class="facts"><span>${esc(subtitle)}</span><span>${tracks.length} tracks</span><span>Source: YouTube</span></div>
        <div class="detail-actions"><button type="button" class="btn primary" data-action="playall">${icon('play', 16)} Play all</button>
        <button type="button" class="btn" data-action="playlist-new">${icon('plus', 16)} Save as playlist</button>${actions}</div>
      </div></div>
    <div class="track-list">${tracks.map((t, i) => trackRow(t, i)).join('')}</div>`;
}

function renderArtist(root, name) {
  root.innerHTML = `<div class="empty-state"><strong>Loading ${esc(name)}…</strong></div>`;
  endpoints.search(name, 24).then((data) => {
    if (state.route !== `/artist/${name}` && !state.route.startsWith('/artist/')) return;
    state.detail = { kind: 'artist', tracks: data.tracks };
    root.innerHTML = detailShell('ARTIST · SEARCH-BACKED', name, 'Artist collection', data.tracks[0]?.artwork, data.tracks);
    hydrateIcons(root);
  }).catch((e) => { root.innerHTML = errorState(e.message, 'data-action="retry-detail"'); hydrateIcons(root); });
}

function renderAlbum(root, name) {
  root.innerHTML = `<div class="empty-state"><strong>Loading ${esc(name)}…</strong></div>`;
  endpoints.search(name, 24).then((data) => {
    if (!state.route.startsWith('/album/')) return;
    state.detail = { kind: 'album', tracks: data.tracks };
    root.innerHTML = detailShell('ALBUM · SEARCH-BACKED', name, 'Album collection', data.tracks[0]?.artwork, data.tracks);
    hydrateIcons(root);
  }).catch((e) => { root.innerHTML = errorState(e.message, 'data-action="retry-detail"'); hydrateIcons(root); });
}

function renderPlaylist(root, id) {
  const playlist = state.library.playlists.find((p) => p.id === id);
  if (!playlist) { root.innerHTML = empty('Playlist not found', 'It may have been removed.'); return; }
  root.innerHTML = detailShell('PLAYLIST · LOCAL', playlist.name, `Created ${new Date(playlist.createdAt).toLocaleDateString()}`, playlist.tracks[0]?.artwork, playlist.tracks,
    `<button type="button" class="btn" data-action="delete-playlist" data-id="${esc(playlist.id)}">${icon('trash', 16)} Delete</button>`);
}

/* --------------------------------------------------------------- history */
function renderHistory(root) {
  const tracks = state.library.history.map(findTrack).filter(Boolean);
  root.innerHTML = `<div class="section-head"><div><div class="eyebrow">LISTENING HISTORY · LOCAL</div><h1>Recently played</h1></div>
    ${tracks.length ? '<button type="button" class="btn sm" data-action="clear-history">Clear history</button>' : ''}</div>
    ${tracks.length ? `<div class="track-list">${tracks.map((t, i) => trackRow(t, i)).join('')}</div>` : empty('Nothing here yet', 'Songs you play will appear here.')}`;
}

/* ----------------------------------------------------------------- queue */
function renderQueue(root) {
  const p = state.player;
  root.innerHTML = `<div class="section-head"><div><div class="eyebrow">PLAYBACK QUEUE</div><h1>Up next</h1></div>
    <button type="button" class="btn sm" data-action="open-full">${icon('waveform', 15)} Full player</button></div>
    ${p.queue.length ? `<div class="track-list">${p.queue.map((t, i) => `
      <div class="track-row ${i === p.index ? 'current' : ''}" data-track="${esc(t.id || t.videoId)}">
        <span class="idx">${i === p.index ? '▶' : String(i + 1).padStart(2, '0')}</span>${art(t, 'thumb')}
        <div class="meta"><strong>${esc(t.title)}</strong><span>${esc(t.artist)}</span></div>
        <div class="row-actions"><button type="button" class="icon-btn tiny" data-action="jump" data-value="${i}" aria-label="Play now">${icon('play', 15)}</button>
        <button type="button" class="icon-btn tiny" data-action="remove-queue" data-value="${i}" aria-label="Remove from queue">${icon('close', 15)}</button></div>
        <span class="dur-txt">${fmt(t.durationMs)}</span></div>`).join('')}</div>`
      : empty('Queue is empty', 'Play a song or add tracks from search.')}`;
}

/* -------------------------------------------------------------- musicflow */
function renderFlow(root) {
  const flow = state.flow;
  const seed = currentTrack() || state.library.recent[0] || null;
  const tracks = flow.tracks || [];
  root.innerHTML = `<div class="hero" style="grid-template-columns:1fr">
    <div class="hero-copy"><div class="eyebrow">MUSIC FLOW · ENDLESS DISCOVERY</div>
      <h1>One song. <em>An endless stream.</em></h1>
      <p>Music Flow chains related real tracks into a continuously growing queue, ranked by your skips and likes. Seeded from ${seed ? `<strong>${esc(seed.title)}</strong>` : 'your recent plays or search results'}.</p>
      <div class="hero-actions">
        <button type="button" class="btn primary" data-action="flow-start">${icon('flow', 17)} ${flow.active ? 'Add more tracks' : 'Start flow'}</button>
        ${flow.active ? '<button type="button" class="btn" data-action="flow-stop">Stop flow</button>' : ''}
        ${seed ? `<button type="button" class="btn ghost" data-action="flow-play">${icon('play', 17)} Play current flow</button>` : ''}
      </div>
      ${flow.loading ? '<p style="margin-top:16px">Finding related music…</p>' : ''}
      ${tracks.length ? `<p style="margin-top:12px;color:var(--cyan)">${tracks.length} tracks in this flow</p>` : ''}
    </div></div>
    ${tracks.length ? `<div class="view-section"><div class="section-head"><h2>Flow tracks</h2></div>
      <div class="track-list">${tracks.map((t, i) => trackRow(t, i)).join('')}</div></div>`
      : empty('Flow has not started', 'Press Start flow to build a real related-track queue.')}`;
}

export async function startFlow() {
  const seed = currentTrack() || state.library.recent[0] || state.search.results?.tracks?.[0];
  if (!seed) { toast('Play or search for a song first to seed Music Flow.', true); return; }
  state.flow.loading = true; state.flow.seed = seed;
  if (state.route === '/musicflow') render(document.getElementById('view'), '/musicflow');
  const tracks = state.flow.tracks || [];
  let added = 0;
  try {
    for (let page = 0; page < 3; page++) {
      const query = page === 0
        ? `${seed.artist || ''} ${seed.title || ''} similar`
        : `${seed.artist || ''} ${['hits', 'live', 'remix', 'songs'][page - 1]}`;
      const data = await endpoints.search(query, 12);
      for (const t of data.tracks) {
        const id = t.id || t.videoId;
        if (tracks.some((x) => (x.id || x.videoId) === id) || id === (seed.id || seed.videoId)) continue;
        if (state.library.hidden.includes(id)) continue;
        tracks.push(t); added++;
      }
    }
    state.flow.tracks = tracks;
    state.flow.active = true;
    if (!currentTrack() && tracks.length) playQueue(tracks.slice(0, 20), 0, 'Music Flow');
    toast(`Music Flow added ${added} tracks`);
  } catch (error) {
    toast(error.message, true);
  } finally {
    state.flow.loading = false;
    if (state.route === '/musicflow') render(document.getElementById('view'), '/musicflow');
  }
}

/* ---------------------------------------------------------------- videos */
function renderVideos(root, params) {
  const requested = params.get('id');
  let tracks = requested ? [findTrack(requested)].filter(Boolean) : [];
  if (!tracks.length) tracks = [...state.library.recent, ...(state.search.results?.tracks || []), ...(state.player.queue || [])]
    .filter((t, i, arr) => arr.findIndex((x) => (x.id || x.videoId) === (t.id || t.videoId)) === i).slice(0, 12);
  root.innerHTML = `<div class="section-head"><div><div class="eyebrow">VIDEO EQUIVALENTS · YOUTUBE EMBEDS</div><h1>Music videos</h1></div>
      <span style="color:var(--faint);font-size:12px">Video plays in the embedded player; audio playback stays in AURA.</span></div>
    ${tracks.length ? `<div class="look-grid" style="grid-template-columns:repeat(auto-fill,minmax(260px,1fr))">${tracks.map((t) => `
      <article class="panel" style="padding:0;overflow:hidden">
        <div class="video-frame" data-video="${esc(t.id || t.videoId)}" style="position:relative;aspect-ratio:16/9;background:#000">
          ${art(t, 'video-thumb')}
          <button type="button" class="play-fab" style="position:absolute;inset:0;margin:auto;width:52px;height:52px;border:0;border-radius:50%;background:var(--cyan);color:#05060a;display:grid;place-items:center" data-action="video-play" data-id="${esc(t.id || t.videoId)}" aria-label="Play video for ${esc(t.title)}">${icon('play', 22)}</button>
        </div>
        <div style="padding:12px"><strong style="font-size:13px">${esc(t.title)}</strong><p style="font-size:11px;margin:3px 0 0">${esc(t.artist)}</p></div>
      </article>`).join('')}</div>`
      : empty('No videos yet', 'Play a song or search, then return here for its official video.')}`;
  root.querySelectorAll('.video-frame img').forEach((img) => {
    img.style.cssText = 'width:100%;height:100%;object-fit:cover';
  });
}

/* -------------------------------------------------------- offline & files */
function renderDownloads(root) {
  const files = state.library.files;
  root.innerHTML = `<div class="section-head"><div><div class="eyebrow">OFFLINE & LOCAL FILES</div><h1>Your own audio, offline</h1></div>
      <label class="btn primary sm" for="fileInput">${icon('plus', 15)} Import audio files</label>
      <input id="fileInput" type="file" accept="audio/*" multiple hidden></div>
    <p>AURA never caches copyrighted streamed audio. Import audio you own to play it locally, even offline. Only file metadata is saved between sessions; the audio itself stays on this device until you reload.</p>
    ${files.length ? `<div class="track-list">${files.map((f, i) => trackRow({ ...f, artwork: '', provider: 'local' }, i)).join('')}</div>`
      : empty('No local files yet', 'Import MP3, M4A, WAV or OGG files from your device.')}
    <div class="view-section">
      <div class="section-head"><div class="eyebrow">ANDROID APP</div><h2>Prefer the native experience?</h2></div>
      <a class="btn primary" href="#/download">${icon('download', 16)} Download AURA MUSIC APK</a>
    </div>`;
}

/* -------------------------------------------------------------- APK page */
function renderDownload(root) {
  const release = window.AURA_RELEASE_DOWNLOADS || {};
  const apk = state.apk || {
    brand: 'AURA MUSIC 2.0', appName: 'AURA MUSIC', package: 'echo.music.iad1tya',
    versionName: '1.4.6', versionCode: 167, releaseDate: '2026-11-19', minAndroid: 8,
    sha256: 'F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16',
    changelog: ['15 Premium Lyrics looks with AUTO mood matching and CODE POETRY', '3D PATTERN visualizer and album-art reactive backgrounds', 'Media3 playback, downloads, Android Auto and Cast', 'Music Flow, recognition, scrobbling and cloud library'],
    assets: {
      apk: { file: 'AURA-Music-UniversalGms-Release.apk', url: release.apk || './downloads/AURA-Music-UniversalGms-Release.apk', sizeMb: 142.7, sha256: 'F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16' },
      aab: { file: 'AURA-Music-UniversalGms-Release.aab', url: release.aab || './downloads/AURA-Music-UniversalGms-Release.aab', sizeMb: 177.8 },
    },
  };
  const a = { ...(apk.assets?.apk || {}) };
  if (!a.url || a.url.startsWith('/downloads/')) a.url = release.apk || a.url;
  root.innerHTML = `<section class="apk-hero">
      <img class="app-icon" src="./favicon-192.png" alt="AURA MUSIC app icon">
      <div><div class="eyebrow">VERIFIED SIGNED RELEASE</div><h1>${esc(apk.brand)}</h1>
        <div class="ver">${esc(apk.appName)} · v${esc(apk.versionName)} (${apk.versionCode}) · ${esc(apk.package)}</div>
        <p style="margin-top:12px">The real, signed Android build used as the reference for this web experience. Same Aura identity, full native feature set.</p>
        <div class="hero-actions">
          <a class="btn primary" href="${esc(a.url)}" download="${esc(a.file || 'AURA-Music.apk')}">${icon('download', 17)} Download APK · ${a.sizeMb || '~143'} MB</a>
          <a class="btn" href="#/home">${icon('music', 17)} Explore the web app</a>
        </div>
      </div></section>
    <div class="apk-stats">
      <div class="stat"><b>v${esc(apk.versionName)}</b><span>LATEST VERSION</span></div>
      <div class="stat"><b>${a.sizeMb || '—'} MB</b><span>APK SIZE</span></div>
      <div class="stat"><b>${esc(apk.releaseDate)}</b><span>RELEASE DATE</span></div>
      <div class="stat"><b>Android ${apk.minAndroid}+</b><span>MINIMUM OS</span></div>
      <div class="stat"><b>v2 signed</b><span>SIGNATURE</span></div>
    </div>
    <div class="settings-grid">
      <div class="panel"><h3>What's new</h3><ol class="install-steps">${(apk.changelog || []).map((c) => `<li>${esc(c)}</li>`).join('')}</ol></div>
      <div class="panel"><h3>How to install</h3><ol class="install-steps">
        <li>Open the download link above and save the APK.</li>
        <li>On your Android device, open the downloaded file.</li>
        <li>Allow “Install unknown apps” for your browser or file manager when asked.</li>
        <li>Tap Install, then open AURA MUSIC from your app drawer.</li>
      </ol>
      <div class="disclaimer" style="margin-top:16px">This build is version ${esc(apk.versionName)}. “2.0” is the unified web experience brand; no separately versioned 2.0 APK exists.</div></div>
      <div class="panel"><h3>Verify your download</h3>
        <table class="apk-table"><tbody>
          <tr><td>Package</td><td>${esc(apk.package)}</td></tr>
          <tr><td>Version code</td><td class="tabular">${apk.versionCode}</td></tr>
          <tr><td>File</td><td class="tabular">${esc(a.file || '—')}</td></tr>
          <tr><td>SHA-256</td><td class="hash">${esc(a.sha256 || apk.sha256 || '—')}</td></tr>
        </tbody></table>
        <p style="font-size:11px;margin-top:12px">Compare the hash after downloading: <code>powershell Get-FileHash</code> on Windows or <code>sha256sum</code> on Linux/macOS.</p>
      </div>
      <div class="panel"><h3>Update availability</h3><p>v${esc(apk.versionName)} is the latest verified signed artifact in this project. The web app checks release metadata through <code>/api/download/apk</code> when the backend is running.</p>
        <button type="button" class="btn sm" data-action="reload-apk">${icon('refresh', 15)} Check for update</button></div>
    </div>`;
}

export async function loadApk() {
  try { state.apk = await endpoints.apk(); }
  catch { /* keep the verified fallback metadata */ }
  if (state.route === '/download') render(document.getElementById('view'), '/download');
}

/* -------------------------------------------------------------- settings */
function renderSettings(root) {
  const s = state.settings;
  const cfg = state.config || {};
  const fmReady = Boolean(cfg.lastfmConfigured);
  const fmConnected = Boolean(s.lastfmSessionKey);
  const fmPending = hasPendingConnection();
  const lookTiles = LOOK_OPTIONS.map((l) => `
    <button type="button" class="look-tile ${s.lyricLook === l.look ? 'active' : ''}" data-action="set-look" data-value="${esc(l.look)}" title="${esc(l.signature || '')}">
      <i style="background:linear-gradient(135deg,${l.primary},${l.accent})"></i>${esc(l.name)}</button>`).join('');
  const mobileTiles = MOBILE_OPTIONS.map((l) => `
    <button type="button" class="look-tile ${s.mobileStyle === l.look ? 'active' : ''}" data-action="set-mobile-style" data-value="${esc(l.look)}" title="${esc(l.signature || '')}">
      <i style="background:linear-gradient(135deg,${l.primary},${l.accent})"></i>${esc(l.name)}</button>`).join('');

  let fmPanel = '<div class="panel"><h3>Last.fm scrobbling</h3><p style="font-size:12px">Last.fm is not enabled on this backend yet. Scrobbling stays off until the backend is configured.</p></div>';
  if (fmReady) {
    const fmStatus = fmConnected
      ? `Connected as <strong>${esc(s.lastfmUsername || 'your Last.fm account')}</strong>. Your session key stays in this browser; the shared secret never leaves the backend.`
      : 'Connect your Last.fm account to scrobble tracks and show your now-playing status.';
    const fmAction = fmConnected
      ? '<button type="button" class="btn sm" data-action="lastfm-disconnect">Disconnect Last.fm</button>'
      : fmPending
        ? '<button type="button" class="btn sm primary" data-action="lastfm-finish">I approved — finish connecting</button>'
        : '<button type="button" class="btn sm primary" data-action="lastfm-connect">Connect to Last.fm</button>';
    const fmControls = fmConnected ? `
      <div class="setting-row"><div>Scrobble tracks<small>Submit a listen after the minimum time below. Network failures never interrupt playback.</small></div>
        <label class="switch"><input type="checkbox" data-setting="scrobbleEnabled" ${s.scrobbleEnabled ? 'checked' : ''}><span></span></label></div>
      <div class="setting-row"><div>Update now playing<small>Show the current track on your Last.fm profile.</small></div>
        <label class="switch"><input type="checkbox" data-setting="nowPlayingEnabled" ${s.nowPlayingEnabled ? 'checked' : ''}><span></span></label></div>
      <div class="setting-row"><div>Love on favorite<small>Heart a track here to love it on Last.fm.</small></div>
        <label class="switch"><input type="checkbox" data-setting="loveEnabled" ${s.loveEnabled ? 'checked' : ''}><span></span></label></div>
      <div class="setting-row"><div>Minimum listen time</div>
        <select data-setting="scrobbleMinSeconds">${[30, 60, 120].map((v) => `<option value="${v}" ${Number(s.scrobbleMinSeconds) === v ? 'selected' : ''}>${v} seconds</option>`).join('')}</select></div>
      <div class="setting-row"><div>Scrobble after</div>
        <select data-setting="scrobblePercent">${[0.3, 0.5, 0.8].map((v) => `<option value="${v}" ${Number(s.scrobblePercent) === v ? 'selected' : ''}>${Math.round(v * 100)}% of track</option>`).join('')}</select></div>`
      : '';
    fmPanel = `<div class="panel"><h3>Last.fm scrobbling</h3>
      <p style="font-size:12px">${fmStatus}</p>
      <div class="hero-actions">${fmAction}</div>
      ${fmControls}
    </div>`;
  }

  root.innerHTML = `<div class="section-head"><div><div class="eyebrow">PREFERENCES · STORED LOCALLY</div><h1>Settings</h1></div></div>
    <div class="settings-grid">
      <div class="panel"><h3>LyricsPlay</h3>
        <div class="setting-row"><div>Lyrics engine<small>Classic keeps all 18 original looks. Mobile adds beat sync; Word Flow is the cinematic scattered-word stage.</small></div></div>
        <div class="mode-switch engine-switch" role="group" aria-label="Lyrics engine">
          <button type="button" data-action="set-engine" data-value="classic" class="${s.lyricEngine === 'classic' ? 'active' : ''}">Classic</button>
          <button type="button" data-action="set-engine" data-value="mobile" class="${s.lyricEngine === 'mobile' ? 'active' : ''}">Mobile · word sync</button>
          <button type="button" data-action="set-engine" data-value="wordflow" class="${s.lyricEngine === 'wordflow' ? 'active' : ''}">Word Flow</button>
        </div>
        <div class="setting-row"><div>Classic default look<small>All 18 original looks stay available. AUTO matches the playing song's mood.</small></div></div>
        <div class="look-grid">${lookTiles}</div>
        <div class="setting-row"><div>Mobile word style<small>InstaLrc presets and true-black AMOLED word flow.</small></div></div>
        <div class="look-grid">${mobileTiles}</div>
        <div class="setting-row"><div>AUTO mood matching<small>Only steers visuals. Lyrics words and timings are never changed.</small></div>
          <label class="switch"><input type="checkbox" data-setting="autoMood" ${s.autoMood ? 'checked' : ''}><span></span></label></div>
        <div class="setting-row"><div>Note approximate word timing<small>Shown when the provider supplies line timing only.</small></div>
          <label class="switch"><input type="checkbox" data-setting="showWordApproxNote" ${s.showWordApproxNote ? 'checked' : ''}><span></span></label></div>
      </div>
      <div class="panel"><h3>Playback & interface</h3>
        <div class="setting-row"><div>Volume<small>${Math.round(s.volume * 100)}%</small></div><input class="range" type="range" min="0" max="1" step="0.01" value="${s.volume}" data-setting="volume"></div>
        <div class="setting-row"><div>Reduce motion<small>Minimizes animations and lyric scrolling.</small></div>
          <label class="switch"><input type="checkbox" data-setting="reducedMotion" ${s.reducedMotion ? 'checked' : ''}><span></span></label></div>
        <div class="setting-row"><div>Ambient transitions<small>Cross-fade visual backgrounds between tracks.</small></div>
          <label class="switch"><input type="checkbox" data-setting="crossfadeVisual" ${s.crossfadeVisual ? 'checked' : ''}><span></span></label></div>
        <div class="setting-row"><div>Data saver preference<small>Use smaller artwork where the provider offers it.</small></div>
          <label class="switch"><input type="checkbox" data-setting="dataSaver" ${s.dataSaver ? 'checked' : ''}><span></span></label></div>
      </div>
      <div class="panel"><h3>Backend & capabilities</h3>
        <table class="apk-table"><tbody>${Object.entries(state.config?.capabilities || {}).map(([k, v]) =>
          `<tr><td>${esc(k.replace(/([A-Z])/g, ' $1'))}</td><td style="color:${v ? 'var(--green)' : 'var(--faint)'}">${v ? 'available' : 'local only'}</td></tr>`).join('')}</tbody></table>
        <p style="font-size:11px;margin-top:12px">Groq visual analysis: ${state.config?.groqConfigured ? 'configured' : 'not configured (local heuristics)'}.</p>
        <p style="font-size:11px">Last.fm scrobbling: ${fmReady ? 'available' : 'not configured on this backend'}.</p>
      </div>
      <div class="panel"><h3>Keyboard shortcuts</h3>
        <table class="apk-table"><tbody>${[
          ['Space / K', 'Play or pause'], ['← / →', 'Seek 5 seconds'], ['Shift + ← / →', 'Previous / next track'],
          ['S', 'Shuffle'], ['R', 'Repeat'], ['M', 'Mute'], ['L', 'LyricsPlay'], ['F', 'Full player'], ['/', 'Search'],
        ].map(([k, v]) => `<tr><td><kbd style="font-family:var(--font-mono)">${k}</kbd></td><td>${v}</td></tr>`).join('')}</tbody></table>
      </div>
      <div class="panel"><h3>Data & privacy</h3>
        <p style="font-size:12px">Favorites, playlists, history and preferences are stored only in this browser. There is no shared account backend, so they do not sync to Android.</p>
        <div class="hero-actions"><button type="button" class="btn sm" data-action="export-data">Export library</button>
          <button type="button" class="btn sm" data-action="clear-data">Clear local data</button></div>
      </div>
      ${fmPanel}
      <div class="panel"><h3>Genuine limitations</h3>
        <p style="font-size:12px">Web uses YouTube search instead of the app's InnerTube visitor/po-token flow; no login or private credentials are used. Optional Last.fm scrobbling is available in Settings when the backend is configured. Playlist sync, Discord, Cast, Android Auto, widgets, mic recognition and native DSP remain Android-only. Streamed audio is never cached offline.</p>
        <a class="btn sm" href="./privacy.html" target="_blank" rel="noopener">Privacy policy</a>
      </div>
    </div>`;
}

/* ------------------------------------------------------------ mood radio */
function moodPill(mood) {
  return String(mood || '').replaceAll('_', ' ').toLowerCase();
}

function renderMood(root) {
  const ai = state.ai;
  const caps = state.config?.capabilities || {};
  const sourceNote = !caps.aiSuggest ? 'SMART CURATION · UPDATE BACKEND FOR AI'
    : state.config?.groqConfigured ? 'POWERED BY GROQ AI'
      : 'SMART CURATION · ADD GROQ_API_KEY FOR AI CURATION';
  const mix = ai.mix;
  const tracks = mix?.tracks || [];
  root.innerHTML = `
    <section class="ai-hero">
      <div class="eyebrow">AI MOOD RADIO · AURA CURATOR</div>
      <h1>Tell us the feeling.<br><em>We'll find the songs.</em></h1>
      <p>Pick a mood or describe any vibe — a rainy night drive, a breakup, a gym session. AURA builds a queue of real, playable tracks and can open it straight in cinematic LyricsPlay.</p>
      <form class="ai-search" id="aiVibeForm">
        <span data-icon="sparkles" aria-hidden="true"></span>
        <input id="aiVibeInput" type="text" maxlength="160" placeholder="e.g. rainy night drive with dreamy synths" value="${esc(ai.vibe)}" aria-label="Describe your vibe">
        <button type="submit" class="btn primary" aria-label="Build my mix">${icon('sparkles', 16)} Build my mix</button>
      </form>
      <div class="ai-source">${esc(sourceNote)}</div>
    </section>

    <section class="view-section">
      <div class="section-head"><div><div class="eyebrow">START WITH A FEELING</div><h2>Mood radio</h2></div></div>
      <div class="mood-chips">${MOOD_CHIPS.map((m) => `<button type="button" class="mood-chip ${ai.mood === m.mood ? 'active' : ''}" data-action="ai-mood" data-value="${esc(m.mood)}" aria-pressed="${ai.mood === m.mood}"><span aria-hidden="true">${m.emoji}</span><strong>${esc(m.label)}</strong></button>`).join('')}</div>
    </section>

    <section class="view-section">
      <div class="section-head"><div><div class="eyebrow">YOUR MIX</div><h2>${mix ? `${esc(moodPill(mix.mood))} radio` : 'Ready when you are'}</h2></div>
        ${tracks.length ? `<button type="button" class="btn sm" data-action="ai-save">${icon('playlist', 15)} Save playlist</button>` : ''}</div>
      ${ai.loading ? `<div class="ai-loading"><span class="ai-spinner" aria-hidden="true"></span> Curating real tracks for “${esc(ai.vibe || moodPill(ai.mood))}”…</div>` : ''}
      ${ai.error ? `<div class="empty-state error-state"><span class="icon-badge">${icon('info', 28)}</span><strong>Mix unavailable</strong><span>${esc(ai.error)}</span><button type="button" class="btn" data-action="ai-retry">${icon('refresh', 15)} Retry</button></div>` : ''}
      ${!ai.loading && !ai.error && !mix ? empty('No mix yet', 'Pick a mood above or describe a vibe to build a real track queue.') : ''}
      ${!ai.loading && mix ? `
        <div class="ai-mix-note">
          <span class="pill">${mix.source === 'groq' ? 'AI CURATED' : 'SMART CURATED'}</span>
          <span class="pill">${esc(moodPill(mix.mood))}</span>
          <span class="pill">${esc(moodPill(mix.theme))}</span>
          <span class="pill">${tracks.length} REAL TRACKS</span>
          <p>${esc(mix.explanation)}</p>
        </div>
        ${tracks.length ? `
          <div class="hero-actions" style="margin:14px 0 18px">
            <button type="button" class="btn primary" data-action="ai-play">${icon('play', 17)} Play ${tracks.length} tracks</button>
            <button type="button" class="btn" data-action="ai-cinematic">${icon('lyrics', 17)} Play in cinematic lyrics</button>
          </div>
          <div class="track-list">${tracks.map((t, i) => trackRow(t, i)).join('')}</div>`
        : `<div class="empty-state"><strong>No playable tracks found</strong><span>Try a broader vibe — the provider could not resolve this one right now.</span></div>`}
      ` : ''}
    </section>`;

  hydrateIcons(root);
  root.querySelector('#aiVibeForm')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const value = root.querySelector('#aiVibeInput')?.value.trim();
    if (value) runAiMix({ vibe: value });
  });
}

export async function runAiMix(input = {}) {
  const body = input.vibe
    ? { vibe: input.vibe, limit: 8 }
    : { mood: input.mood || state.ai.mood, limit: 8 };
  state.ai.loading = true;
  state.ai.error = null;
  state.ai.vibe = input.vibe || '';
  if (input.mood) state.ai.mood = input.mood;
  if (state.route === '/mood') render(document.getElementById('view'), '/mood');
  try {
    const mix = await endpoints.aiSuggest(body);
    state.ai.mix = mix;
    if (mix.mood) state.ai.mood = mix.mood;
    toast(mix.degraded ? 'Mix ready from real provider results' : 'AI mix ready');
  } catch (error) {
    state.ai.error = error.message;
    state.ai.mix = null;
    toast(error.message, true);
  } finally {
    state.ai.loading = false;
    if (state.route === '/mood') render(document.getElementById('view'), '/mood');
  }
}

/* --------------------------------------------------------------- routing */
export function render(root, path) {
  const [routePart, queryString = ''] = path.split('?');
  const params = new URLSearchParams(queryString);
  const route = routePart || '/home';
  state.route = route;
  activeNav = route.split('/')[1] || 'home';
  document.querySelectorAll('[data-nav]').forEach((el) => el.classList.toggle('active', el.dataset.nav === activeNav));

  if (route === '/home') renderHome(root);
  else if (route === '/search') renderSearch(root, params);
  else if (route === '/library') renderLibrary(root, params);
  else if (route === '/history') renderHistory(root);
  else if (route === '/queue') renderQueue(root);
  else if (route === '/musicflow') renderFlow(root);
  else if (route === '/mood') renderMood(root);
  else if (route === '/video') renderVideos(root, params);
  else if (route === '/downloads') renderDownloads(root);
  else if (route === '/download') renderDownload(root);
  else if (route === '/settings') renderSettings(root);
  else if (route === '/lyrics') {
    root.innerHTML = `<div class="section-head"><div><div class="eyebrow">PREMIUM LYRICSPLAY</div><h1>Synced, cinematic lyrics</h1></div></div>
      <p>Search and preview lyrics before playback, or open the overlay for the song that is playing. Timed words come from LRCLIB; the 18 classic looks, mobile word-sync engine and cinematic Word Flow stage never alter the words.</p>
      <div class="hero-actions"><button type="button" class="btn primary" data-action="open-lyrics">${icon('lyrics', 17)} Find lyrics / open LyricsPlay</button>
        <button type="button" class="btn" data-action="open-full">${icon('waveform', 17)} Full player</button></div>
      <div class="lyric-view-callout"><div><span class="eyebrow">ANDROID-EQUIVALENT VIEWS</span><strong>Cinematic</strong><small>Warm letterbox glow and film-halo type.</small></div>
        <div><span class="eyebrow">TRUE AMOLED</span><strong>AMOLED</strong><small>Pure black canvas, white words, minimal bloom.</small></div>
        <div><span class="eyebrow">NEW · MOBILE WORD SYNC</span><strong>InstaLrc + AMOLED Word Flow</strong><small>Words pop, sway and reveal one by one on the beat.</small></div>
        <div><span class="eyebrow">NEW · CINEMATIC WORD FLOW</span><strong>Scattered word stage</strong><small>Dim serif words drift on black; one lavender word lights up at a time.</small></div></div>
      <div class="view-section"><div class="section-head"><h2>Mobile · word-by-word (new)</h2></div>
        <div class="mode-switch engine-switch" role="group" aria-label="Lyrics engine">
          <button type="button" data-action="set-engine" data-value="classic" class="${state.settings.lyricEngine === 'classic' ? 'active' : ''}">Classic</button>
          <button type="button" data-action="set-engine" data-value="mobile" class="${state.settings.lyricEngine === 'mobile' ? 'active' : ''}">Mobile · word sync</button>
          <button type="button" data-action="set-engine" data-value="wordflow" class="${state.settings.lyricEngine === 'wordflow' ? 'active' : ''}">Word Flow · cinematic</button>
        </div>
        <div class="look-grid">${MOBILE_OPTIONS.map((l) => `<button type="button" class="look-tile ${state.settings.mobileStyle === l.look ? 'active' : ''}" data-action="set-mobile-style" data-value="${esc(l.look)}" title="${esc(l.signature || '')}"><i style="background:linear-gradient(135deg,${l.primary},${l.accent})"></i>${esc(l.name)}</button>`).join('')}</div></div>
      <div class="view-section"><div class="section-head"><h2>Classic · 15 signature looks + 2 views</h2></div>
        <div class="look-grid">${LOOK_OPTIONS.map((l) => `<button type="button" class="look-tile ${state.settings.lyricLook === l.look ? 'active' : ''}" data-action="set-look" data-value="${esc(l.look)}" title="${esc(l.signature || '')}"><i style="background:linear-gradient(135deg,${l.primary},${l.accent})"></i>${esc(l.name)}</button>`).join('')}</div></div>`;
  } else if (route.startsWith('/artist/')) renderArtist(root, decodeURIComponent(route.slice(8)));
  else if (route.startsWith('/album/')) renderAlbum(root, decodeURIComponent(route.slice(7)));
  else if (route.startsWith('/playlist/')) renderPlaylist(root, decodeURIComponent(route.slice(10)));
  else root.innerHTML = empty('Page not found', 'That view does not exist.');

  hydrateIcons(root);
  window.scrollTo({ top: 0, behavior: 'auto' });
}

/* -------------------------------------------------------------- handlers */
function allTracksInView() {
  return [
    ...(state.detail?.tracks || []),
    ...(state.search.results?.tracks || []),
    ...(state.discover?.sections || []).flatMap((s) => s.tracks),
    ...state.library.recent,
  ];
}

async function onClick(event) {
  const el = event.target.closest('[data-action]');
  const card = event.target.closest('[data-track]');
  if (card && !el) {
    const track = findTrack(card.dataset.track) || allTracksInView().find((t) => (t.id || t.videoId) === card.dataset.track);
    if (track) playTrack(track, allTracksInView(), 'AURA catalog');
    return;
  }
  if (!el) return;
  const action = el.dataset.action;
  const value = el.dataset.value ?? el.dataset.id;

  if (action === 'playall') {
    const list = allTracksInView();
    if (list.length) playQueue(list, 0, state.route.replace('/', ''));
  } else if (action === 'surprise') {
    const all = [...(state.discover?.sections || []).flatMap((s) => s.tracks), ...(state.search.results?.tracks || [])];
    if (all.length) { const t = all[Math.floor(Math.random() * all.length)]; playTrack(t, all, 'AURA picks'); }
    else loadDiscover(true);
  } else if (action === 'ai-mood') {
    state.ai.mood = value;
    state.ai.vibe = '';
    if (state.route !== '/mood') {
      render(document.getElementById('view'), '/mood');
      location.hash = '#/mood';
    }
    runAiMix({ mood: value });
  } else if (action === 'ai-retry') {
    runAiMix({ mood: state.ai.mood, vibe: state.ai.vibe });
  } else if (action === 'ai-play') {
    const list = state.ai.mix?.tracks || [];
    if (list.length) playQueue(list, 0, 'AI Mood Radio');
  } else if (action === 'ai-cinematic') {
    const list = state.ai.mix?.tracks || [];
    if (!list.length) return;
    setEngine('classic');
    setLook('CINEMATIC');
    playQueue(list, 0, 'AI Mood Radio');
    document.dispatchEvent(new CustomEvent('aura:openlyrics', { detail: { track: list[0] } }));
  } else if (action === 'ai-save') {
    const list = state.ai.mix?.tracks || [];
    if (!list.length) return;
    makePlaylist(`${moodPill(state.ai.mood)} radio`, list);
    toast('Mix saved as a playlist');
  } else if (action === 'fav') {
    const t = findTrack(value) || allTracksInView().find((x) => (x.id || x.videoId) === value);
    if (t) {
      const loved = toggleFavorite(t);
      document.dispatchEvent(new CustomEvent('aura:favorite', { detail: { track: t, loved } }));
      render(document.getElementById('view'), location.hash.slice(1));
    }
  } else if (action === 'nextplay' || action === 'queue') {
    const t = findTrack(value) || allTracksInView().find((x) => (x.id || x.videoId) === value);
    if (t) addToQueue(t, action === 'nextplay');
  } else if (action === 'playlist-add') {
    const t = findTrack(value) || allTracksInView().find((x) => (x.id || x.videoId) === value);
    if (!t) return;
    let playlist = state.library.playlists[0];
    if (!playlist) playlist = makePlaylist('My AURA mix', [t]);
    else { addToPlaylist(playlist.id, t); toast(`Added to ${playlist.name}`); }
  } else if (action === 'playlist-new') {
    const list = allTracksInView().slice(0, 50);
    const name = window.prompt('Playlist name', 'My AURA mix');
    if (name?.trim()) { makePlaylist(name.trim(), list); toast('Playlist created'); location.hash = '#/library?tab=playlists'; }
  } else if (action === 'delete-playlist') {
    if (window.confirm('Delete this playlist?')) { deletePlaylist(value); location.hash = '#/library?tab=playlists'; }
  } else if (action === 'libtab') location.hash = `#/library?tab=${value}`;
  else if (action === 'filter') { state.search.filter = value; render(document.getElementById('view'), location.hash.slice(1)); }
  else if (action === 'retry-discover') loadDiscover(true);
  else if (action === 'retry-search') runSearch(state.search.query);
  else if (action === 'retry-detail') render(document.getElementById('view'), location.hash.slice(1));
  else if (action === 'clear-history') {
    state.library.history = []; persistLibrary(); render(document.getElementById('view'), '/history'); toast('History cleared');
  } else if (action === 'jump') {
    const target = state.player.queue[Number(value)];
    if (target) playTrack(target, state.player.queue, state.player.context);
  } else if (action === 'remove-queue') { removeFromQueue(Number(value)); render(document.getElementById('view'), '/queue'); }
  else if (action === 'open-full') openFullPlayer();
  else if (action === 'open-lyrics') document.dispatchEvent(new CustomEvent('aura:openlyrics'));
  else if (action === 'set-look') setLook(value);
  else if (action === 'set-engine') { setEngine(value); render(document.getElementById('view'), location.hash.slice(1)); }
  else if (action === 'set-mobile-style') { setMobileStyle(value); render(document.getElementById('view'), location.hash.slice(1)); }
  else if (action === 'flow-start') startFlow();
  else if (action === 'flow-stop') { state.flow.active = false; state.flow.tracks = []; render(document.getElementById('view'), '/musicflow'); }
  else if (action === 'flow-play') { const list = state.flow.tracks || []; if (list.length) playQueue(list.slice(0, 25), 0, 'Music Flow'); }
  else if (action === 'video-play') {
    const frame = el.parentElement;
    frame.innerHTML = `<iframe src="https://www.youtube-nocookie.com/embed/${encodeURIComponent(value)}?autoplay=1&rel=0" title="Music video" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen style="width:100%;height:100%;border:0"></iframe>`;
  } else if (action === 'reload-apk') loadApk();
  else if (action === 'lastfm-connect') {
    // Open synchronously so the click gesture survives the async token fetch.
    const popup = window.open('about:blank', '_blank', 'width=920,height=720');
    if (popup) { try { popup.opener = null; } catch { /* best effort */ } }
    try {
      await beginLastfmConnect(popup);
      toast('Approve AURA in the Last.fm tab, then return here and finish connecting.');
    } catch (e) {
      if (popup) { try { popup.close(); } catch { /* best effort */ } }
      toast(e.message || 'Could not start the Last.fm connection.', true);
    }
    render(document.getElementById('view'), '/settings');
  } else if (action === 'lastfm-finish') {
    try {
      const data = await completeLastfmConnect();
      toast(`Last.fm connected${data.username ? ` as ${data.username}` : ''}.`);
    } catch (e) {
      toast(e.message || 'Last.fm has not approved this connection yet.', true);
    }
    render(document.getElementById('view'), '/settings');
  } else if (action === 'lastfm-disconnect') {
    disconnectLastfm();
    toast('Last.fm disconnected. Your local library is untouched.');
    render(document.getElementById('view'), '/settings');
  } else if (action === 'export-data') {
    const blob = new Blob([JSON.stringify({ library: state.library, settings: state.settings }, null, 2)], { type: 'application/json' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'aura-music-library.json'; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  } else if (action === 'clear-data') {
    if (window.confirm('Clear all local favorites, playlists, history and settings?')) {
      import('./storage.js').then(({ clearAll }) => { clearAll(); location.reload(); });
    }
  }
}

function onChange(event) {
  const el = event.target;
  if (el.matches('[data-action="sort"]')) { state.search.sort = el.value; render(document.getElementById('view'), location.hash.slice(1)); }
  else if (el.matches('[data-setting]')) {
    const key = el.dataset.setting;
    state.settings[key] = el.type === 'checkbox' ? el.checked : Number(el.value);
    if (key === 'volume') import('./player.js').then((m) => m.setVolume(state.settings.volume));
    persistSettings(); emit();
    if (key === 'volume') render(document.getElementById('view'), '/settings');
  } else if (el.id === 'fileInput') {
    const files = Array.from(el.files || []);
    files.forEach((file) => {
      const url = URL.createObjectURL(file);
      state.library.files.unshift({ id: 'blob_' + Date.now() + Math.random().toString(36).slice(2, 8), title: file.name.replace(/\.[^.]+$/, ''), artist: 'Local file', album: '', durationMs: 0, artwork: '', url, provider: 'local' });
    });
    persistLibrary();
    render(document.getElementById('view'), '/downloads');
    toast(`${files.length} file${files.length === 1 ? '' : 's'} imported`);
  }
}

export function initViews() {
  const root = document.getElementById('view');
  root.addEventListener('click', onClick);
  root.addEventListener('change', onChange);
  root.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && event.target.matches('[data-track]')) event.target.click();
  });
}
