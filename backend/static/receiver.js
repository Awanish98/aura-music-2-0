/* AURA MUSIC cinematic receiver.
 * Visual state only. No audio is fetched, decoded or played here.
 * The session token lives in sessionStorage and is never written to a URL.
 */
(() => {
  "use strict";

  const TOKEN_KEY = "aura.receiver.token";
  const SID_KEY = "aura.receiver.sessionId";
  const ASPECT_KEY = "aura.receiver.aspect";
  const POLL_MS = 1000;

  const $ = (id) => document.getElementById(id);
  const els = {
    pairScreen: $("pairScreen"), stageScreen: $("stageScreen"),
    pairForm: $("pairForm"), code: $("code"), name: $("name"),
    pairButton: $("pairButton"), pairError: $("pairError"),
    stage: $("stage"), canvas: $("fx"),
    statusText: $("statusText"), statusDot: $("statusDot"),
    title: $("title"), artist: $("artist"), lyrics: $("lyrics"),
    art: $("art"),
  };

  const DEFAULT_COLORS = {
    background: "#050407", primary: "#e8c77b", secondary: "#ffb95c",
    accent: "#8a6a2f", text: "#f3ede2", muted: "#8a8278",
  };

  let sessionToken = sessionStorage.getItem(TOKEN_KEY) || "";
  let sessionId = sessionStorage.getItem(SID_KEY) || "";
  let connected = false;
  let polling = false;
  let lastState = null;
  let receivedAt = 0;
  let wordEls = [];
  let wordSignature = "";
  let activeIndex = -1;
  let targetFeatures = { bass: 0.15, beat: 0.1, energy: 0.3, tempo: 0.3 };

  function setStatus(text, kind) {
    els.statusText.textContent = text;
    els.statusDot.className = kind || "";
  }

  function showPair(message) {
    connected = false;
    els.pairScreen.classList.add("active");
    els.stageScreen.classList.remove("active");
    if (message) els.pairError.textContent = message;
  }

  function showStage() {
    els.pairScreen.classList.remove("active");
    els.stageScreen.classList.add("active");
    setStatus("Waiting for your phone…", "");
    resizeCanvas();
  }

  function clearSession(message) {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(SID_KEY);
    sessionToken = "";
    sessionId = "";
    lastState = null;
    wordSignature = "";
    wordEls = [];
    showPair(message || "Pairing ended. Enter a new code from your phone.");
  }

  // ------------------------------------------------------------- pairing
  els.pairForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    els.pairError.textContent = "";
    const code = els.code.value.trim();
    const name = els.name.value.trim();
    if (!/^\d{6}$/.test(code)) {
      els.pairError.textContent = "Enter the 6-digit code shown on your phone.";
      return;
    }
    els.pairButton.disabled = true;
    els.pairButton.textContent = "Pairing…";
    try {
      const response = await fetch("/api/receiver/pair", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code, name }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.error || "Pairing failed. Try a new code.");
      sessionToken = data.session_token;
      sessionId = data.session_id;
      sessionStorage.setItem(TOKEN_KEY, sessionToken);
      sessionStorage.setItem(SID_KEY, sessionId);
      els.code.value = "";
      showStage();
    } catch (err) {
      els.pairError.textContent = err && err.message ? err.message : "Pairing failed. Check the backend connection.";
    } finally {
      els.pairButton.disabled = false;
      els.pairButton.textContent = "Pair device";
    }
  });

  // --------------------------------------------------------------- aspect
  function applyAspect(name) {
    const allowed = ["aspect-16-9", "aspect-16-10", "aspect-21-9"];
    const chosen = allowed.includes(name) ? name : "aspect-16-9";
    els.stage.classList.remove(...allowed);
    els.stage.classList.add(chosen);
    document.querySelectorAll(".aspect-picker button").forEach((button) => {
      button.classList.toggle("on", button.dataset.aspect === chosen);
    });
    sessionStorage.setItem(ASPECT_KEY, chosen);
  }
  document.querySelectorAll(".aspect-picker button").forEach((button) => {
    button.addEventListener("click", () => applyAspect(button.dataset.aspect));
  });
  applyAspect(sessionStorage.getItem(ASPECT_KEY) || "aspect-16-9");

  // ----------------------------------------------------------------- poll
  async function poll() {
    if (!sessionToken || polling) return;
    polling = true;
    try {
      const response = await fetch(
        "/api/receiver/state?session_token=" + encodeURIComponent(sessionToken),
        { cache: "no-store", headers: { "Accept": "application/json" } }
      );
      if (response.status === 404) {
        clearSession("That pairing expired. Start a new one from your phone.");
        return;
      }
      if (response.status === 204) {
        connected = false;
        setStatus("Waiting for your phone…", "");
        return;
      }
      if (!response.ok) throw new Error("state unavailable");
      const state = await response.json();
      connected = true; // only after a successful state response
      receivedAt = performance.now();
      lastState = state;
      setStatus("Connected · receiving visuals", "live");
      render(state);
    } catch (_) {
      setStatus("Reconnecting…", "issue");
    } finally {
      polling = false;
    }
  }

  // --------------------------------------------------------------- render
  function groupedWords(words) {
    const lines = [];
    let current = [];
    for (const word of words) {
      const previous = current[current.length - 1];
      if (previous && word.start_ms - previous.end_ms > 1400) {
        lines.push(current);
        current = [];
      }
      current.push(word);
    }
    if (current.length) lines.push(current);
    return lines;
  }

  function renderLyrics(state) {
    const words = Array.isArray(state.words) ? state.words : [];
    const visible = state.lyrics_visible !== false;
    if (!visible) {
      els.lyrics.className = "lyrics";
      els.lyrics.textContent = "Lyrics are hidden on your phone.";
      wordEls = [];
      wordSignature = "hidden";
      return;
    }
    if (!words.length) {
      const text = typeof state.lyrics === "string" ? state.lyrics : "";
      els.lyrics.className = "lyrics plain";
      els.lyrics.textContent = text || "Lyrics appear once your phone publishes a song.";
      wordEls = [];
      wordSignature = "plain:" + text.length;
      return;
    }

    const signature = words.length + "|" + words[0].text + "|" + words[words.length - 1].text +
      "|" + words[words.length - 1].end_ms;
    if (signature !== wordSignature) {
      wordSignature = signature;
      const fragment = document.createDocumentFragment();
      wordEls = [];
      for (const line of groupedWords(words)) {
        const lineEl = document.createElement("div");
        lineEl.className = "lyric-line";
        for (const word of line) {
          const span = document.createElement("span");
          span.className = "word";
          span.textContent = word.text + " ";
          span.dataset.start = String(word.start_ms);
          span.dataset.end = String(word.end_ms);
          lineEl.appendChild(span);
          wordEls.push(span);
        }
        fragment.appendChild(lineEl);
      }
      els.lyrics.className = "lyrics";
      els.lyrics.replaceChildren(fragment);
      activeIndex = -1;
    }

    const elapsed = state.playing === false ? 0 : Math.max(0, performance.now() - receivedAt);
    const position = (state.position_ms || 0) + elapsed;
    let found = -1;
    for (let i = 0; i < wordEls.length; i++) {
      const start = Number(wordEls[i].dataset.start);
      const end = Number(wordEls[i].dataset.end);
      if (position >= start && position <= end + 180) { found = i; break; }
      if (start > position + 400) break;
    }
    if (found !== activeIndex) {
      if (activeIndex >= 0 && wordEls[activeIndex]) wordEls[activeIndex].classList.remove("active");
      for (let i = 0; i < wordEls.length; i++) {
        wordEls[i].classList.toggle("past", i < found);
      }
      if (found >= 0 && wordEls[found]) {
        wordEls[found].classList.add("active");
        const line = wordEls[found].parentElement;
        const target = line.offsetTop - els.lyrics.clientHeight / 2 + line.offsetHeight / 2;
        els.lyrics.scrollTop += (target - els.lyrics.scrollTop) * 0.25;
      }
      activeIndex = found;
    }
  }

  function render(state) {
    els.title.textContent = state.title || "Untitled";
    els.artist.textContent = state.artist || "—";
    const theme = state.theme || {};
    for (const key of Object.keys(DEFAULT_COLORS)) {
      if (typeof theme[key] === "string" && /^#[0-9a-f]{6}$/i.test(theme[key])) {
        targetColors[key] = theme[key];
      }
    }
    targetFeatures = Object.assign({ bass: 0.15, beat: 0.1, energy: 0.3, tempo: 0.3 }, state.features || {});
    renderLyrics(state);
  }

  // ------------------------------------------------------- canvas visuals
  const canvas = els.canvas;
  const ctx = canvas.getContext("2d");
  let width = 0, height = 0;
  const targetColors = Object.assign({}, DEFAULT_COLORS);
  const currentColors = {};
  for (const key of Object.keys(DEFAULT_COLORS)) currentColors[key] = hexToRgb(DEFAULT_COLORS[key]);
  const particles = Array.from({ length: 72 }, () => ({
    x: Math.random(), y: Math.random(), r: 0.6 + Math.random() * 1.9,
    speed: 0.00004 + Math.random() * 0.00016,
    drift: (Math.random() - 0.5) * 0.00012,
    phase: Math.random() * Math.PI * 2,
  }));

  function hexToRgb(hex) {
    const value = parseInt(hex.slice(1), 16);
    return [(value >> 16) & 255, (value >> 8) & 255, value & 255];
  }
  function rgba(rgb, alpha) {
    return "rgba(" + (rgb[0] | 0) + "," + (rgb[1] | 0) + "," + (rgb[2] | 0) + "," + alpha + ")";
  }
  function cssRgb(rgb) {
    return "rgb(" + (rgb[0] | 0) + " " + (rgb[1] | 0) + " " + (rgb[2] | 0) + ")";
  }
  function resizeCanvas() {
    const rect = els.stage.getBoundingClientRect();
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    width = Math.max(1, Math.round(rect.width));
    height = Math.max(1, Math.round(rect.height));
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  window.addEventListener("resize", resizeCanvas);
  if ("ResizeObserver" in window) new ResizeObserver(resizeCanvas).observe(els.stage);

  function frame(now) {
    for (const key of Object.keys(targetColors)) {
      const target = hexToRgb(targetColors[key]);
      const current = currentColors[key];
      for (let i = 0; i < 3; i++) current[i] += (target[i] - current[i]) * 0.045;
      document.documentElement.style.setProperty("--" + key, cssRgb(current));
    }

    const bg = currentColors.background;
    const primary = currentColors.primary;
    const secondary = currentColors.secondary;
    const bass = Math.max(0, Math.min(1, Number(targetFeatures.bass) || 0));
    const beat = Math.max(0, Math.min(1, Number(targetFeatures.beat) || 0));
    const energy = Math.max(0, Math.min(1, Number(targetFeatures.energy) || 0));

    ctx.fillStyle = cssRgb(bg);
    ctx.fillRect(0, 0, width, height);

    const cx = width * 0.5;
    const cy = height * 0.46;
    const radius = Math.max(width, height) * (0.38 + bass * 0.19 + Math.sin(now / 520) * 0.012);
    const glow = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
    glow.addColorStop(0, rgba(primary, 0.10 + bass * 0.16));
    glow.addColorStop(0.5, rgba(secondary, 0.035 + beat * 0.07));
    glow.addColorStop(1, rgba(bg, 0));
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, width, height);

    if (beat > 0.08) {
      ctx.beginPath();
      ctx.arc(cx, cy, radius * (0.62 + beat * 0.16), 0, Math.PI * 2);
      ctx.strokeStyle = rgba(primary, 0.04 + beat * 0.13);
      ctx.lineWidth = 1.2;
      ctx.stroke();
    }

    for (const particle of particles) {
      particle.y -= particle.speed * (0.55 + energy * 1.8);
      particle.x += particle.drift * 0.35;
      if (particle.y < -0.03) { particle.y = 1.03; particle.x = Math.random(); }
      if (particle.x < -0.03) particle.x = 1.03;
      if (particle.x > 1.03) particle.x = -0.03;
      const sway = Math.sin(now / 1400 + particle.phase) * 0.006;
      ctx.beginPath();
      ctx.arc((particle.x + sway) * width, particle.y * height, particle.r, 0, Math.PI * 2);
      ctx.fillStyle = rgba(primary, 0.10 + energy * 0.26);
      ctx.fill();
    }

    requestAnimationFrame(frame);
  }

  // ------------------------------------------------------------------ init
  if (sessionToken) {
    showStage();
  } else {
    const prefilled = new URLSearchParams(location.search).get("code");
    if (/^\d{6}$/.test(prefilled || "")) els.code.value = prefilled;
  }
  resizeCanvas();
  requestAnimationFrame(frame);
  setInterval(poll, POLL_MS);
  poll();
})();
