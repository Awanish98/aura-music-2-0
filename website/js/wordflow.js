/* WordFlowVisualizer — cinematic scattered-word renderer.
 * Presentation only: it consumes an already-resolved word stream plus clock
 * callbacks and never touches audio, routing or app state. Deterministic
 * placement keeps every word anchored across seeks, resizes and re-renders.
 */

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const easeOut = (t) => 1 - (1 - t) ** 2;

function hash32(text) {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h >>> 0;
}

function gcd(a, b) {
  while (b) { const t = a % b; a = b; b = t; }
  return a;
}

/** Redistribute line-only timings using word length and punctuation weight.
 * The result is always flagged approximate; exact word timings are untouched. */
function refineApproximate(lines) {
  return lines.map((line) => {
    const words = Array.isArray(line.words) ? line.words : [];
    if (!words.length) return line;
    if (!line.approximate && !words.some((w) => w.approximate)) return line;

    const start = Number(line.startMs) || 0;
    const end = Number(line.endMs) || start + 1200;
    const span = Math.max(240, end - start);
    const weights = words.map((word) => {
      const bare = String(word.text ?? '').replace(/[^\p{L}\p{N}']/gu, '');
      let weight = clamp(bare.length || 1, 1, 12);
      if (/[,;:]$/.test(String(word.text ?? '').trim())) weight += 1.5;
      if (/[.!?…]$/.test(String(word.text ?? '').trim())) weight += 2.5;
      return weight;
    });
    const total = weights.reduce((sum, weight) => sum + weight, 0) || words.length;
    const budget = Math.max(120, span - 40);
    let cursor = start;
    const refined = words.map((word, index) => {
      const slice = Math.max(70, (budget * weights[index]) / total);
      const ws = Math.round(cursor);
      const we = Math.round(Math.min(end, cursor + slice));
      cursor = we + 12;
      return { ...word, startMs: ws, endMs: Math.max(ws + 60, we), approximate: true };
    });
    return { ...line, words: refined, approximate: true };
  });
}

function flattenWords(lines, trackId) {
  const out = [];
  lines.forEach((line, lineIndex) => {
    const words = Array.isArray(line.words) ? line.words : [];
    words.forEach((word, wordIndex) => {
      const text = String(word.text ?? '');
      const startMs = Number(word.startMs);
      const endMs = Number(word.endMs);
      if (!text.trim() || !Number.isFinite(startMs) || !Number.isFinite(endMs)) return;
      out.push({
        index: out.length,
        lineIndex,
        wordIndex,
        text,
        startMs,
        endMs: Math.max(startMs + 60, endMs),
        hash: hash32(`${trackId}|${lineIndex}|${wordIndex}|${text}`),
      });
    });
  });
  return out;
}

export class WordFlowVisualizer {
  constructor(host, options = {}) {
    this.host = host;
    this.options = options;
    this.words = [];
    this.nodes = new Map();
    this.before = 8;
    this.after = 22;
    this.active = -2;
    this.destroyed = false;

    this.layer = document.createElement('div');
    this.layer.className = 'wf-layer';
    this.layer.setAttribute('aria-hidden', 'true');
    host.appendChild(this.layer);

    this._onResize = () => this._scheduleLayout();
    window.addEventListener('resize', this._onResize, { passive: true });
    if (typeof ResizeObserver !== 'undefined') {
      this._observer = new ResizeObserver(this._onResize);
      this._observer.observe(host);
    }
  }

  get size() { return this.words.length; }
  get activeWord() { return this.words[this.active] || null; }

  setData(lines = [], trackId = '') {
    this._clearNodes();
    this.words = flattenWords(refineApproximate(Array.isArray(lines) ? lines : []), String(trackId || ''));
    this.before = 8;
    this.after = (this.host.clientWidth || window.innerWidth) < 640 ? 16 : 22;
    this.active = -2;
    this.layout();
  }

  layout() {
    if (this.destroyed || !this.words.length) return;
    const rect = this.host.getBoundingClientRect();
    const width = Math.max(280, rect.width || window.innerWidth);
    const height = Math.max(360, rect.height || window.innerHeight);
    const mobile = width < 640;
    const cols = mobile ? 4 : width < 1100 ? 5 : 6;
    const rows = mobile ? 12 : 9;
    const cells = cols * rows;
    const padX = width * (mobile ? 0.045 : 0.065);
    const padY = height * (mobile ? 0.09 : 0.11);
    const cellW = (width - padX * 2) / cols;
    const cellH = (height - padY * 2 - (mobile ? 56 : 76)) / rows;
    const guard = this.before + this.after + 3;
    const boxes = new Map();

    const overlaps = (a, b) =>
      a.left < b.right + 8 && a.right > b.left - 8 &&
      a.top < b.bottom + 6 && a.bottom > b.top - 6;

    this.words.forEach((word) => {
      const h = word.hash;
      const rawSize = (mobile ? 15 : 18) + (((h >>> 13) & 255) / 255) * (mobile ? 12 : 17);
      const fitWidth = (cellW * 1.6) / Math.max(1, word.text.length * 0.52);
      const size = clamp(Math.min(rawSize, fitWidth), mobile ? 14 : 17, mobile ? 27 : 35);
      const halfW = Math.min(cellW * 0.8, word.text.length * size * 0.28);
      const halfH = size * 0.72;

      let step = 1 + ((h >>> 8) % 11);
      while (gcd(step, cells) !== 1) step += 1;
      const start = h % cells;
      let best = null;
      let bestConflicts = Infinity;

      for (let offset = 0; offset < cells; offset++) {
        const cell = (start + offset * step) % cells;
        const col = cell % cols;
        const row = Math.floor(cell / cols);
        const jitterX = ((((h >>> 11) & 255) / 255) - 0.5) * cellW * 0.26;
        const jitterY = ((((h >>> 19) & 255) / 255) - 0.5) * cellH * 0.2;
        const x = padX + (col + 0.5) * cellW + jitterX;
        const y = padY + (row + 0.5) * cellH + jitterY;
        const box = { left: x - halfW, right: x + halfW, top: y - halfH, bottom: y + halfH };
        let conflicts = 0;
        for (const [index, other] of boxes) {
          if (Math.abs(word.index - index) > guard) continue;
          if (overlaps(box, other)) conflicts += 1;
        }
        if (conflicts < bestConflicts) {
          bestConflicts = conflicts;
          best = { cell, x, y, box };
        }
        if (conflicts === 0) break;
      }

      word.x = best.x;
      word.y = best.y;
      word.rot = ((((h >>> 3) & 255) / 255) - 0.5) * 9;
      word.size = size;
      word.floatDuration = 5.5 + (((h >>> 23) & 255) / 255) * 3.5;
      word.floatDelay = -((((h >>> 17) & 255) / 255) * word.floatDuration);
      boxes.set(word.index, best.box);
    });
  }

  update(positionMs) {
    if (this.destroyed || !this.words.length) return;
    const time = Number.isFinite(positionMs) ? positionMs : 0;
    const active = this._findIndex(time);
    const from = Math.max(0, (active < 0 ? 0 : active) - this.before);
    const to = Math.min(this.words.length - 1, (active < 0 ? this.before : active) + this.after);
    this._setWindow(from, to);

    for (let index = from; index <= to; index++) {
      const word = this.words[index];
      const node = this.nodes.get(index);
      if (!node) continue;
      const progress = clamp((time - word.startMs) / Math.max(1, word.endMs - word.startMs), 0, 1);
      let opacity;
      let scale;
      let blur;
      let underline;
      let drift;
      let state;

      if (time < word.startMs) {
        const near = clamp(1 - (word.startMs - time) / 2600, 0, 1);
        opacity = 0.06 + near * 0.11;
        scale = 0.9 + near * 0.06;
        blur = 1.1 + (1 - near) * 1.3;
        underline = 0;
        drift = 0;
        state = 'upcoming';
      } else if (time <= word.endMs) {
        const eased = easeOut(progress);
        opacity = 0.3 + 0.7 * eased;
        scale = 1 + 0.26 * eased;
        blur = (1 - eased) * 2.4;
        underline = eased;
        drift = (1 - eased) * 4;
        state = 'current';
      } else {
        const fade = clamp(1 - (time - word.endMs) / 3200, 0, 1);
        opacity = 0.06 + 0.52 * fade;
        scale = 0.96 + 0.26 * fade;
        blur = (1 - fade) * 1.9;
        underline = fade * 0.35;
        drift = -(1 - fade) * 3;
        state = 'sung';
      }

      const signature = `${state}|${opacity.toFixed(3)}|${scale.toFixed(3)}|${blur.toFixed(2)}|${underline.toFixed(3)}|${drift.toFixed(1)}`;
      if (node._wfState !== signature) {
        node.style.setProperty('--wf-opacity', opacity.toFixed(3));
        node.style.setProperty('--wf-scale', scale.toFixed(3));
        node.style.setProperty('--wf-blur', `${blur.toFixed(2)}px`);
        node.style.setProperty('--wf-underline', underline.toFixed(3));
        node.style.setProperty('--wf-drift', `${drift.toFixed(1)}px`);
        node._wfState = signature;
      }
      node.classList.toggle('is-current', state === 'current');
      node.classList.toggle('is-sung', state === 'sung');
      node.classList.toggle('is-upcoming', state === 'upcoming');
    }

    if (active !== this.active) {
      this.active = active;
      this.options.onActive?.(this.words[active] || null, active);
    }
  }

  destroy() {
    if (this.destroyed) return;
    this.destroyed = true;
    this._clearNodes();
    this.layer.remove();
    window.removeEventListener('resize', this._onResize);
    this._observer?.disconnect();
    this.words = [];
    this.nodes.clear();
  }

  _scheduleLayout() {
    if (this._layoutFrame) return;
    this._layoutFrame = requestAnimationFrame(() => {
      this._layoutFrame = null;
      this.layout();
    });
  }

  _findIndex(time) {
    const words = this.words;
    if (!words.length || time < words[0].startMs) return -1;
    let low = 0;
    let high = words.length - 1;
    let result = -1;
    while (low <= high) {
      const mid = (low + high) >> 1;
      if (words[mid].startMs <= time) { result = mid; low = mid + 1; }
      else high = mid - 1;
    }
    return result;
  }

  _setWindow(from, to) {
    for (const [index, node] of this.nodes) {
      if (index < from || index > to) { node.remove(); this.nodes.delete(index); }
    }
    for (let index = from; index <= to; index++) {
      if (this.nodes.has(index)) continue;
      const node = this._createNode(this.words[index]);
      this.nodes.set(index, node);
      this.layer.appendChild(node);
    }
  }

  _createNode(word) {
    const node = document.createElement('div');
    node.className = 'wf-word is-upcoming';
    node.dataset.wfIndex = String(word.index);
    node.style.setProperty('--wf-left', `${word.x.toFixed(1)}px`);
    node.style.setProperty('--wf-top', `${word.y.toFixed(1)}px`);
    node.style.setProperty('--wf-rot', `${word.rot.toFixed(2)}deg`);
    node.style.setProperty('--wf-size', `${word.size.toFixed(1)}px`);
    node.style.setProperty('--wf-dur', `${word.floatDuration.toFixed(2)}s`);
    node.style.setProperty('--wf-delay', `${word.floatDelay.toFixed(2)}s`);
    node.style.setProperty('--wf-opacity', '0.06');
    node.style.setProperty('--wf-scale', '0.92');
    node.style.setProperty('--wf-blur', '2px');
    node.style.setProperty('--wf-underline', '0');
    node.style.setProperty('--wf-drift', '0px');

    const inner = document.createElement('span');
    inner.className = 'wf-word-inner';
    const float = document.createElement('span');
    float.className = 'wf-word-float';
    const text = document.createElement('span');
    text.className = 'wf-text';
    text.textContent = word.text;
    float.appendChild(text);
    inner.appendChild(float);
    node.appendChild(inner);
    return node;
  }

  _clearNodes() {
    this.nodes.forEach((node) => node.remove());
    this.nodes.clear();
  }
}

export default WordFlowVisualizer;
