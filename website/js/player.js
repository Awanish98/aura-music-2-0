/* Playback engine: HTMLAudio + Media Session, queue, shuffle, repeat, shortcuts. */
import { state, emit, addHistory, isFavorite, toggleFavorite, persistVolume, toast } from './state.js';
import { streamUrl } from './api.js';
import { icon, hydrateIcons } from './icons.js';

const audio = document.getElementById('audio');
const mini = document.getElementById('miniPlayer');
const overlay = document.getElementById('playerOverlay');

let analyser = null;
let audioCtx = null;
let vizFrame = null;
let vizMode = 0;
let seeking = false;
const VIZ_MODES = ['SPECTRUM', 'RADIAL', 'WAVE', 'PARTICLES', 'RINGS', 'AURORA', 'BARS', '3D PATTERN'];

export function currentTrack() {
  const p = state.player;
  return p.index >= 0 ? p.queue[p.index] || null : null;
}

function fmt(ms) {
  if (!Number.isFinite(ms) || ms < 0) ms = 0;
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function shuffledIndexes(length, keepFirst = 0) {
  const rest = Array.from({ length }, (_, i) => i).filter((i) => i !== keepFirst);
  for (let i = rest.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [rest[i], rest[j]] = [rest[j], rest[i]];
  }
  return [keepFirst, ...rest];
}

/* ----------------------------------------------------------------- playback */

export function playQueue(tracks, startIndex = 0, context = '') {
  if (!Array.isArray(tracks) || !tracks.length) return;
  const p = state.player;
  p.queue = tracks.slice();
  p.context = context;
  p.index = Math.max(0, Math.min(startIndex, tracks.length - 1));
  loadCurrent(true);
}

export function playTrack(track, queue = null, context = '') {
  if (!track) return;
  const list = queue && queue.length ? queue : [track];
  const at = Math.max(0, list.findIndex((t) => (t.id || t.videoId) === (track.id || track.videoId)));
  playQueue(list, at, context);
}

function loadCurrent(autoplay = true) {
  const p = state.player;
  const track = currentTrack();
  mini.hidden = !track;
  if (!track) return;
  p.position = 0;
  p.duration = track.durationMs || 0;
  const url = track.url && String(track.url).startsWith('blob:') ? track.url : streamUrl(track.id || track.videoId);
  audio.src = url;
  audio.volume = p.volume;
  if (autoplay) {
    audio.play().catch((err) => {
      toast('Playback was blocked. Press play to start audio.', true);
      state.player.playing = false;
      updatePlayState();
    });
  }
  addHistory(track);
  renderPlayer();
  updateMediaSession();
  document.dispatchEvent(new CustomEvent('aura:trackchange', { detail: track }));
}

export function togglePlay() {
  if (!currentTrack()) {
    toast('Pick a song to start playback.');
    return;
  }
  if (audio.paused) {
    audio.play().catch(() => toast('Unable to start playback. Try again.', true));
  } else {
    audio.pause();
  }
}

export function next() {
  const p = state.player;
  if (!p.queue.length) return;
  const logicalNext = p.index + 1;
  if (logicalNext >= p.queue.length) {
    if (p.repeat === 'all') p.index = 0;
    else return;
  } else {
    p.index = logicalNext;
  }
  loadCurrent(true);
}

export function prev() {
  const p = state.player;
  if (audio.currentTime > 4000) { audio.currentTime = 0; return; }
  if (p.index <= 0) { audio.currentTime = 0; return; }
  p.index -= 1;
  loadCurrent(true);
}

export function seek(ms) {
  if (!Number.isFinite(ms)) return;
  const duration = audio.duration || state.player.duration / 1000 || 0;
  const target = Math.max(0, Math.min(ms / 1000, duration || Infinity));
  try { audio.currentTime = target; } catch { /* not loaded */ }
  state.player.position = target * 1000;
  updateProgress();
  updateMediaSession();
}

export function setVolume(value) {
  const v = Math.max(0, Math.min(1, Number(value) || 0));
  state.player.volume = v;
  audio.volume = v;
  audio.muted = v === 0;
  persistVolume();
  updateVolumeUI();
}

export function toggleMute() {
  setVolume(audio.muted || state.player.volume === 0 ? (state.player.volume || 0.8) : 0);
}

export function toggleShuffle() {
  const p = state.player;
  const current = currentTrack();
  p.shuffle = !p.shuffle;
  if (p.shuffle && p.queue.length > 1) {
    const shuffled = p.queue.slice();
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    p.queue = shuffled;
    if (current) {
      p.index = Math.max(0, shuffled.findIndex((t) => (t.id || t.videoId) === (current.id || current.videoId)));
    }
  }
  renderPlayer();
  toast(p.shuffle ? 'Shuffle on' : 'Shuffle off');
}

export function cycleRepeat() {
  const p = state.player;
  p.repeat = p.repeat === 'off' ? 'all' : p.repeat === 'all' ? 'one' : 'off';
  renderPlayer();
  toast(p.repeat === 'off' ? 'Repeat off' : p.repeat === 'all' ? 'Repeat queue' : 'Repeat song');
}

export function addToQueue(track, playNext = false) {
  const p = state.player;
  const at = p.queue.findIndex((t) => (t.id || t.videoId) === (track.id || track.videoId));
  if (at >= 0 && !playNext) { toast('Already in the queue.'); return; }
  if (playNext) p.queue.splice(p.index + 1, 0, track);
  else p.queue.push(track);
  renderQueueBadge();
  toast(playNext ? 'Playing next' : 'Added to queue');
}

export function removeFromQueue(index) {
  const p = state.player;
  if (index === p.index || index < 0 || index >= p.queue.length) return;
  p.queue.splice(index, 1);
  if (index < p.index) p.index -= 1;
  emit();
}

export function toggleCurrentFavorite() {
  const track = currentTrack();
  if (!track) return;
  const added = toggleFavorite(track);
  toast(added ? 'Saved to favorites' : 'Removed from favorites');
  renderPlayer();
}

export function openFullPlayer() { overlay.hidden = false; renderPlayer(); }
export function closeFullPlayer() { overlay.hidden = true; }

/* ------------------------------------------------------------------ rendering */

export function renderPlayer() {
  const track = currentTrack();
  mini.hidden = !track;
  if (!track) { overlay.hidden = true; return; }
  const p = state.player;
  const art = track.artwork || '';
  const favorite = isFavorite(track.id || track.videoId);
  const playing = p.playing;

  mini.innerHTML = `
    <div class="mini-track" data-action="full">
      <img src="${art}" alt="" loading="lazy">
      <div class="mt"><strong>${escapeHtml(track.title)}</strong><span>${escapeHtml(track.artist)}</span></div>
    </div>
    <div class="mini-controls">
      <div class="mini-buttons">
        <button type="button" class="icon-btn tiny shuffle ${p.shuffle ? 'on' : ''}" data-action="shuffle" aria-label="Shuffle" aria-pressed="${p.shuffle}">${icon('shuffle', 17)}</button>
        <button type="button" class="icon-btn tiny" data-action="prev" aria-label="Previous track">${icon('prev', 17)}</button>
        <button type="button" class="icon-btn play" data-action="play" aria-label="${playing ? 'Pause' : 'Play'}">${icon(playing ? 'pause' : 'play', 18)}</button>
        <button type="button" class="icon-btn tiny" data-action="next" aria-label="Next track">${icon('next', 17)}</button>
        <button type="button" class="icon-btn tiny repeat ${p.repeat !== 'off' ? 'on' : ''}" data-action="repeat" aria-label="Repeat: ${p.repeat}">${icon(p.repeat === 'one' ? 'repeatOne' : 'repeat', 17)}</button>
      </div>
      <div class="mini-progress">
        <span class="time" data-role="cur">${fmt(p.position)}</span>
        <input class="seek" type="range" min="0" max="1000" value="${p.duration ? Math.round(p.position / p.duration * 1000) : 0}" aria-label="Seek" data-role="seek">
        <span class="time right" data-role="dur">${fmt(p.duration)}</span>
      </div>
    </div>
    <div class="mini-extras">
      <button type="button" class="icon-btn tiny ${favorite ? 'on' : ''}" data-action="favorite" aria-label="${favorite ? 'Remove from favorites' : 'Add to favorites'}" aria-pressed="${favorite}">${icon('heart', 18)}</button>
      <button type="button" class="icon-btn tiny" data-action="lyrics" aria-label="Open LyricsPlay">${icon('lyrics', 18)}</button>
      <div class="volume"><button type="button" class="icon-btn tiny" data-action="mute" aria-label="Mute">${icon(p.volume === 0 ? 'volumeMute' : 'volume', 18)}</button>
      <input type="range" min="0" max="1" step="0.01" value="${p.volume}" aria-label="Volume" data-role="volume"></div>
      <button type="button" class="icon-btn tiny" data-action="queue" aria-label="Open queue">${icon('queue', 18)}</button>
    </div>`;

  overlay.innerHTML = `
    <div class="player-bg" data-role="bg" style="background-image:url('${art}')"></div>
    <header class="player-head">
      <div><p class="eyebrow">NOW PLAYING · ${escapeHtml(track.provider || 'AURA')}</p></div>
      <div class="player-head-actions">
        <button type="button" class="icon-btn" data-action="lyrics" aria-label="LyricsPlay">${icon('lyrics')}</button>
        <button type="button" class="icon-btn" data-action="viz" aria-label="Cycle visualizer: ${VIZ_MODES[vizMode]}">${icon('waveform')}</button>
        <button type="button" class="icon-btn" data-action="close-full" aria-label="Close full player">${icon('close')}</button>
      </div>
    </header>
    <div class="player-body">
      <div class="player-art-wrap"><img src="${art}" alt="Artwork for ${escapeHtml(track.title)}"><span class="shine"></span></div>
      <div class="player-info">
        <p class="eyebrow">${escapeHtml(p.context || 'AURA MUSIC 2.0')}</p>
        <h1>${escapeHtml(track.title)}</h1>
        <div class="artist">${escapeHtml(track.artist)}</div>
        <div class="album">${escapeHtml(track.album || 'Single · source: ' + (track.sourceAttribution || 'YouTube'))}</div>
        <div class="tags">
          <button type="button" class="chip ${favorite ? 'active' : ''}" data-action="favorite">${icon('heart', 14)} ${favorite ? 'Saved' : 'Save'}</button>
          <a class="chip" href="#/artist/${encodeURIComponent(track.artist)}" data-action="close-full">${icon('artist', 14)} Artist</a>
          <a class="chip" href="#/video?id=${encodeURIComponent(track.id)}" data-action="close-full">${icon('video', 14)} Video</a>
        </div>
        <canvas class="viz-canvas" id="vizCanvas" aria-label="Audio reactive visualizer"></canvas>
        <p class="eyebrow" style="margin:6px 0 0;color:var(--faint)" data-role="viz-name">${VIZ_MODES[vizMode]}</p>
      </div>
    </div>
    <div class="player-controls">
      <div class="seek-row">
        <span class="time" data-role="cur">${fmt(p.position)}</span>
        <input class="seek" type="range" min="0" max="1000" value="${p.duration ? Math.round(p.position / p.duration * 1000) : 0}" aria-label="Seek" data-role="seek">
        <span class="time" data-role="dur">${fmt(p.duration)}</span>
      </div>
      <div class="player-buttons">
        <button type="button" class="icon-btn ${p.shuffle ? 'on' : ''}" data-action="shuffle" aria-label="Shuffle" aria-pressed="${p.shuffle}">${icon('shuffle', 22)}</button>
        <button type="button" class="icon-btn" data-action="prev" aria-label="Previous track">${icon('prev', 24)}</button>
        <button type="button" class="icon-btn big" data-action="play" aria-label="${playing ? 'Pause' : 'Play'}">${icon(playing ? 'pause' : 'play', 26)}</button>
        <button type="button" class="icon-btn" data-action="next" aria-label="Next track">${icon('next', 24)}</button>
        <button type="button" class="icon-btn ${p.repeat !== 'off' ? 'on' : ''}" data-action="repeat" aria-label="Repeat: ${p.repeat}">${icon(p.repeat === 'one' ? 'repeatOne' : 'repeat', 22)}</button>
      </div>
      <div class="player-foot-controls">
        <div class="grp"><button type="button" class="icon-btn" data-action="mute" aria-label="Mute">${icon(p.volume === 0 ? 'volumeMute' : 'volume')}</button>
          <input type="range" min="0" max="1" step="0.01" value="${p.volume}" aria-label="Volume" data-role="volume"></div>
        <div class="grp"><button type="button" class="icon-btn" data-action="queue" aria-label="Queue">${icon('queue')}</button>
          <button type="button" class="icon-btn" data-action="full-close-foot" aria-label="Minimize player">${icon('chevronLeft')}</button></div>
      </div>
    </div>`;

  hydrateIcons(mini);
  hydrateIcons(overlay);
  updateProgress();
  setupVisualizer();
}

function renderQueueBadge() {
  emit();
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function updatePlayState() {
  document.querySelectorAll('[data-action="play"]').forEach((btn) => {
    const label = state.player.playing ? 'Pause' : 'Play';
    btn.setAttribute('aria-label', label);
    btn.innerHTML = icon(state.player.playing ? 'pause' : 'play', btn.classList.contains('big') ? 26 : 18);
  });
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = state.player.playing ? 'playing' : 'paused';
  document.dispatchEvent(new CustomEvent('aura:playstate'));
}

export function updateProgress() {
  const p = state.player;
  const dur = audio.duration || p.duration / 1000 || 0;
  const pct = dur ? Math.min(1000, Math.round((p.position / 1000 / dur) * 1000)) : 0;
  document.querySelectorAll('[data-role="seek"]').forEach((el) => { if (!seeking) el.value = String(pct); });
  document.querySelectorAll('[data-role="cur"]').forEach((el) => { el.textContent = fmt(p.position); });
  document.querySelectorAll('[data-role="dur"]').forEach((el) => { el.textContent = fmt(dur * 1000 || p.duration); });
  document.dispatchEvent(new CustomEvent('aura:time', { detail: { position: p.position, duration: dur * 1000 || p.duration } }));
}

function updateVolumeUI() {
  document.querySelectorAll('[data-role="volume"]').forEach((el) => { el.value = String(state.player.volume); });
}

/* ---------------------------------------------------------- media session */
function updateMediaSession() {
  if (!('mediaSession' in navigator)) return;
  const track = currentTrack();
  if (!track) return;
  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.title,
    artist: track.artist,
    album: track.album || 'AURA MUSIC',
    artwork: track.artwork ? [
      { src: track.artwork, sizes: '512x512', type: 'image/jpg' },
      { src: track.artwork, sizes: '192x192', type: 'image/jpg' },
    ] : [],
  });
  navigator.mediaSession.playbackState = state.player.playing ? 'playing' : 'paused';
  const actions = [
    ['previoustrack', prev], ['nexttrack', next],
    ['seekbackward', () => seek(state.player.position - 10000)],
    ['seekforward', () => seek(state.player.position + 10000)],
    ['seekto', (d) => seek(d.seekTime * 1000)],
  ];
  for (const [name, handler] of actions) {
    try { navigator.mediaSession.setActionHandler(name, handler); } catch { /* unsupported */ }
  }
}

/* --------------------------------------------------------- audio analysis */
function ensureAnalyser() {
  if (analyser || audioCtx) return analyser;
  try {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const source = audioCtx.createMediaElementSource(audio);
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 1024;
    analyser.smoothingTimeConstant = 0.72;
    source.connect(analyser);
    analyser.connect(audioCtx.destination);
  } catch {
    analyser = null;
    audioCtx = null;
  }
  return analyser;
}

/** Smoother-than-timeupdate playhead, in milliseconds. */
export function getPositionMs() {
  return (audio.currentTime || 0) * 1000;
}

let energyData = null;
/** Low-frequency energy (0..1) used by the mobile word-sync engine for beat pops. */
export function getAudioEnergy() {
  if (!state.player.playing || audio.paused) return 0;
  const a = ensureAnalyser();
  if (!a) return 0;
  if (audioCtx?.state === 'suspended') audioCtx.resume().catch(() => {});
  if (!energyData || energyData.length !== a.frequencyBinCount) energyData = new Uint8Array(a.frequencyBinCount);
  a.getByteFrequencyData(energyData);
  let sum = 0;
  const bins = Math.min(8, energyData.length);
  for (let i = 0; i < bins; i++) sum += energyData[i];
  return Math.min(1, (sum / (bins * 255)) * 1.25);
}

/* -------------------------------------------------------------- visualizer */
function setupVisualizer() {
  const canvas = document.getElementById('vizCanvas');
  if (!canvas) return;
  ensureAnalyser();
  if (vizFrame) cancelAnimationFrame(vizFrame);
  const ctx = canvas.getContext('2d');
  const data = new Uint8Array(analyser ? analyser.frequencyBinCount : 64);
  let t = 0;
  const draw = () => {
    const rect = canvas.getBoundingClientRect();
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    if (canvas.width !== Math.floor(rect.width * dpr)) { canvas.width = Math.floor(rect.width * dpr); canvas.height = Math.floor(rect.height * dpr); }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const w = rect.width, h = rect.height;
    ctx.clearRect(0, 0, w, h);
    if (analyser) analyser.getByteFrequencyData(data);
    else for (let i = 0; i < data.length; i++) data[i] = 26 + Math.sin(t / 18 + i / 4) * 16;
    t += state.player.playing ? 1 : 0.25;
    drawMode(ctx, data, w, h, t);
    vizFrame = requestAnimationFrame(draw);
  };
  draw();
}

function drawMode(ctx, data, w, h, t) {
  const accent = '#6ce5ff', violet = '#b388ff', warm = '#ed5564';
  ctx.lineWidth = 1.5;
  if (vizMode === 0 || vizMode === 6) { // spectrum / bars
    const bars = vizMode === 6 ? 72 : 48;
    const bw = w / bars;
    for (let i = 0; i < bars; i++) {
      const val = data[i] / 255;
      const bh = Math.max(2, val * h);
      ctx.fillStyle = i % 3 === 0 ? warm : i % 3 === 1 ? accent : violet;
      ctx.globalAlpha = .35 + val * .6;
      ctx.fillRect(i * bw, h - bh, bw * .7, bh);
    }
  } else if (vizMode === 1 || vizMode === 4) { // radial / rings
    const cx = w / 2, cy = h / 2;
    for (let ring = 0; ring < 4; ring++) {
      ctx.beginPath();
      const base = 10 + ring * 9;
      for (let i = 0; i <= 64; i++) {
        const a = (i / 64) * Math.PI * 2;
        const val = data[(i + ring * 8) % data.length] / 255;
        const r = base + val * 14 + Math.sin(t / 20 + i / 5) * 2;
        const x = cx + Math.cos(a) * r * 2.4, y = cy + Math.sin(a) * r;
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      }
      ctx.closePath(); ctx.strokeStyle = ring % 2 ? accent : violet; ctx.globalAlpha = .7; ctx.stroke();
    }
  } else if (vizMode === 2) { // wave
    ctx.beginPath();
    for (let x = 0; x <= w; x += 2) {
      const idx = Math.floor((x / w) * data.length);
      const y = h / 2 + Math.sin(x / 26 + t / 8) * data[idx] / 255 * h * .35;
      x ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
    }
    ctx.strokeStyle = accent; ctx.lineWidth = 2; ctx.stroke();
  } else if (vizMode === 3) { // particles
    for (let i = 0; i < 46; i++) {
      const val = data[i * 2 % data.length] / 255;
      const x = (i * 53 + t * (0.4 + val)) % w;
      const y = h / 2 + Math.sin(i + t / 14) * h * (.15 + val * .3);
      ctx.fillStyle = i % 2 ? accent : warm; ctx.globalAlpha = .25 + val * .6;
      ctx.beginPath(); ctx.arc(x, y, 1 + val * 2.5, 0, Math.PI * 2); ctx.fill();
    }
  } else if (vizMode === 5) { // aurora
    for (let layer = 0; layer < 3; layer++) {
      ctx.beginPath();
      ctx.moveTo(0, h);
      for (let x = 0; x <= w; x += 6) {
        const idx = Math.floor((x / w) * data.length);
        const y = h - 12 - data[idx] / 255 * h * (.4 + layer * .15) - Math.sin(x / 60 + t / 20 + layer) * 12;
        ctx.lineTo(x, y);
      }
      ctx.lineTo(w, h); ctx.closePath();
      ctx.fillStyle = [warm, violet, accent][layer]; ctx.globalAlpha = .16; ctx.fill();
    }
  } else { // 3D PATTERN (8th visualizer style)
    const cx = w / 2, cy = h / 2;
    ctx.save(); ctx.translate(cx, cy);
    for (let ring = 0; ring < 6; ring++) {
      const depth = ring / 6;
      ctx.save(); ctx.rotate(t / 60 + ring * .18); ctx.scale(1, .34 + depth * .5);
      ctx.beginPath();
      for (let i = 0; i <= 40; i++) {
        const a = (i / 40) * Math.PI * 2;
        const val = data[(i * 3 + ring * 5) % data.length] / 255;
        const r = 14 + ring * 12 + val * 18;
        i ? ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r) : ctx.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      }
      ctx.closePath(); ctx.strokeStyle = ring % 2 ? accent : violet; ctx.globalAlpha = .7 - depth * .5; ctx.stroke(); ctx.restore();
    }
    ctx.restore();
  }
  ctx.globalAlpha = 1;
}

export function cycleVisualizer() {
  vizMode = (vizMode + 1) % VIZ_MODES.length;
  const label = document.querySelector('[data-role="viz-name"]');
  if (label) label.textContent = VIZ_MODES[vizMode];
  toast(`Visualizer: ${VIZ_MODES[vizMode]}`);
}

/* ------------------------------------------------------------------ wiring */
function onClick(event) {
  const btn = event.target.closest('[data-action]');
  if (!btn) return;
  const action = btn.dataset.action;
  if (action === 'full' || action === 'close-full' || action === 'full-close-foot') {
    action === 'full' ? openFullPlayer() : closeFullPlayer();
  } else if (action === 'play') togglePlay();
  else if (action === 'next') next();
  else if (action === 'prev') prev();
  else if (action === 'shuffle') toggleShuffle();
  else if (action === 'repeat') cycleRepeat();
  else if (action === 'favorite') toggleCurrentFavorite();
  else if (action === 'mute') toggleMute();
  else if (action === 'lyrics') document.dispatchEvent(new CustomEvent('aura:openlyrics'));
  else if (action === 'queue') location.hash = '#/queue';
  else if (action === 'viz') cycleVisualizer();
}

function onInput(event) {
  const el = event.target;
  // Range is 0-1000; state.player.duration is milliseconds. seek() expects ms.
  if (el.matches('[data-role="seek"]')) { seeking = true; seek(Number(el.value) / 1000 * state.player.duration); }
  else if (el.matches('[data-role="volume"]')) setVolume(Number(el.value));
}

function onKey(event) {
  const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName);
  if (event.key === 'Escape') { closeFullPlayer(); document.dispatchEvent(new CustomEvent('aura:closeoverlays')); return; }
  if (typing) return;
  if (event.key === ' ' || event.key.toLowerCase() === 'k') { event.preventDefault(); togglePlay(); }
  else if (event.key === 'ArrowRight') { event.preventDefault(); event.shiftKey ? next() : seek(state.player.position + 5000); }
  else if (event.key === 'ArrowLeft') { event.preventDefault(); event.shiftKey ? prev() : seek(state.player.position - 5000); }
  else if (event.key.toLowerCase() === 's') toggleShuffle();
  else if (event.key.toLowerCase() === 'r') cycleRepeat();
  else if (event.key.toLowerCase() === 'm') toggleMute();
  else if (event.key.toLowerCase() === 'l') document.dispatchEvent(new CustomEvent('aura:openlyrics'));
  else if (event.key.toLowerCase() === 'f') openFullPlayer();
  else if (event.key === '/') { event.preventDefault(); document.getElementById('topSearchInput')?.focus(); }
}

export function initPlayer() {
  audio.addEventListener('timeupdate', () => { state.player.position = (audio.currentTime || 0) * 1000; updateProgress(); });
  audio.addEventListener('durationchange', () => { if (Number.isFinite(audio.duration)) state.player.duration = audio.duration * 1000; });
  audio.addEventListener('play', () => {
    state.player.playing = true;
    updatePlayState();
    const a = ensureAnalyser();
    if (a && audioCtx?.state === 'suspended') audioCtx.resume().catch(() => {});
  });
  audio.addEventListener('pause', () => { state.player.playing = false; updatePlayState(); });
  audio.addEventListener('ended', () => {
    if (state.player.repeat === 'one') { audio.currentTime = 0; audio.play(); }
    else next();
  });
  audio.addEventListener('error', () => {
    if (!audio.src) return;
    toast('This stream could not be played. Press play to retry.', true);
    state.player.playing = false;
    updatePlayState();
  });
  mini.addEventListener('click', onClick);
  overlay.addEventListener('click', onClick);
  mini.addEventListener('input', onInput);
  overlay.addEventListener('input', onInput);
  mini.addEventListener('change', () => { seeking = false; });
  overlay.addEventListener('change', () => { seeking = false; });
  document.addEventListener('keydown', onKey);
  if ('mediaSession' in navigator) updateMediaSession();
}
