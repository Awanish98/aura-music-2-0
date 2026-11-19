/* ============================================================
   AURA MUSIC — site interactions
   Original code. No external runtime dependencies.
   ============================================================ */
(() => {
  "use strict";

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  // Canvases below the fold can be laid out after the first paint. Keeping
  // their backing store in sync prevents a blank/frozen demo canvas.
  function fitCanvas(canvas, ctx) {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = canvas.getBoundingClientRect();
    const w = Math.max(1, Math.round(canvas.clientWidth || rect.width || 1));
    const h = Math.max(1, Math.round(canvas.clientHeight || rect.height || 1));
    const pw = Math.round(w * dpr);
    const ph = Math.round(h * dpr);
    if (canvas.width !== pw || canvas.height !== ph) {
      canvas.width = pw;
      canvas.height = ph;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { w, h, dpr };
  }

  function watchCanvas(canvas, ctx, onResize) {
    const resize = () => {
      const size = fitCanvas(canvas, ctx);
      if (onResize) onResize(size);
    };
    resize();
    requestAnimationFrame(resize);
    if (window.ResizeObserver) {
      new ResizeObserver(resize).observe(canvas);
    } else {
      window.addEventListener("resize", resize);
    }
  }

  /* ── shared pointer glow + nav ─────────────────────────── */
  const glow = $("#cursorGlow");
  if (glow && !reduce) {
    window.addEventListener("pointermove", (e) => {
      glow.style.opacity = "1";
      glow.style.transform = `translate3d(${e.clientX - 260}px, ${e.clientY - 260}px, 0)`;
    }, { passive: true });
    document.addEventListener("pointerleave", () => { glow.style.opacity = "0"; });
  }

  const nav = $("#nav");
  const menuBtn = $("#menuBtn");
  const navLinks = $("#navLinks");
  const onScroll = () => nav.classList.toggle("scrolled", window.scrollY > 24);
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });
  menuBtn?.addEventListener("click", () => {
    const open = navLinks.classList.toggle("open");
    menuBtn.setAttribute("aria-expanded", String(open));
  });
  $$(".nav-links a").forEach((a) => a.addEventListener("click", () => {
    navLinks.classList.remove("open");
    menuBtn?.setAttribute("aria-expanded", "false");
  }));

  const navAnchors = $$('.nav-links a[href^="#"]').map((a) => [a, $(a.getAttribute("href"))]).filter((x) => x[1]);
  const navObserver = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      navAnchors.forEach(([a, sec]) => a.classList.toggle("active", sec === e.target));
    });
  }, { rootMargin: "-45% 0px -50% 0px" });
  navAnchors.forEach(([, sec]) => navObserver.observe(sec));

  /* ── scroll reveal + counters ──────────────────────────── */
  const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      e.target.classList.add("in");
      revealObserver.unobserve(e.target);
      if (e.target.dataset.decrypt) decrypt(e.target);
      if (e.target.dataset.split && !reduce) splitChars(e.target);
      $$("[data-count]", e.target).forEach(countUp);
    });
  }, { threshold: 0.12 });
  $$(".reveal").forEach((el) => revealObserver.observe(el));

  function countUp(el) {
    const target = Number(el.dataset.count);
    if (reduce || target === 0) { el.textContent = String(target); return; }
    const start = performance.now();
    const step = (now) => {
      const p = clamp((now - start) / 1100, 0, 1);
      el.textContent = String(Math.round(target * (1 - Math.pow(1 - p, 3))));
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  function splitChars(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) {
      const n = walker.currentNode;
      if (n.parentElement?.closest("[data-rotating]")) continue;
      if (!n.textContent.trim()) continue;
      nodes.push(n);
    }
    nodes.forEach((node) => {
      const frag = document.createDocumentFragment();
      [...node.textContent].forEach((ch, i) => {
        if (ch === " ") { frag.appendChild(document.createTextNode(" ")); return; }
        const s = document.createElement("span");
        s.className = "split-char";
        s.textContent = ch;
        s.style.animationDelay = `${i * 26 + 120}ms`;
        frag.appendChild(s);
      });
      node.replaceWith(frag);
    });
  }

  function decrypt(el) {
    const finalText = el.dataset.decrypt || el.textContent;
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/\\<>*+=";
    let frame = 0;
    const timer = setInterval(() => {
      frame++;
      el.textContent = [...finalText].map((c, i) => {
        if (c === " " || i < frame * 3) return c;
        return chars[Math.floor(Math.random() * chars.length)];
      }).join("");
      if (frame * 3 >= finalText.length) { clearInterval(timer); el.textContent = finalText; }
    }, 22);
  }

  /* ── rotating hero word ────────────────────────────────── */
  const rotating = $("#rotatingWord");
  if (rotating && !reduce) {
    const words = (rotating.dataset.rotating || "SEE").split("|");
    let idx = 0;
    setInterval(() => {
      idx = (idx + 1) % words.length;
      rotating.textContent = words[idx];
    }, 2400);
  }

  /* ── 3D tilt cards ─────────────────────────────────────── */
  if (!reduce) {
    $$("[data-tilt]").forEach((card) => {
      card.addEventListener("pointermove", (e) => {
        const r = card.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - 0.5;
        const y = (e.clientY - r.top) / r.height - 0.5;
        card.style.transform = `perspective(900px) rotateX(${-y * 7}deg) rotateY(${x * 9}deg) translateY(-4px)`;
      });
      card.addEventListener("pointerleave", () => { card.style.transform = ""; });
    });
    const stage = $("#orbStage");
    if (stage) stage.addEventListener("pointermove", (e) => {
      const r = stage.getBoundingClientRect();
      const orb = $(".orb");
      if (!orb) return;
      orb.style.setProperty("--rx", `${14 + ((e.clientY - r.top) / r.height - 0.5) * -34}deg`);
      orb.style.setProperty("--ry", `${-18 + ((e.clientX - r.left) / r.width - 0.5) * 46}deg`);
    });
  }

  /* ── starfield + hero eq ───────────────────────────────── */
  const stars = $("#starfield");
  if (stars) {
    const ctx = stars.getContext("2d");
    let pts = [], w = 0, h = 0;
    const resize = ({ w: nextW, h: nextH }) => {
      w = nextW; h = nextH;
      const n = Math.min(120, Math.floor(w * h / 14000));
      pts = Array.from({ length: n }, () => ({
        x: Math.random() * w, y: Math.random() * h,
        r: Math.random() * 1.3 + 0.25, v: Math.random() * 0.22 + 0.04,
        hue: Math.random() > 0.7 ? "179,136,255" : "108,229,255",
      }));
    };
    watchCanvas(stars, ctx, resize);
    const drawStars = () => {
      ctx.clearRect(0, 0, w, h);
      pts.forEach((p) => {
        p.y -= p.v;
        if (p.y < -4) { p.y = h + 4; p.x = Math.random() * w; }
        ctx.beginPath();
        ctx.fillStyle = `rgba(${p.hue},${0.18 + p.r * 0.3})`;
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fill();
      });
      requestAnimationFrame(drawStars);
    };
    drawStars();
  }
  const orbEq = $("#orbEq");
  if (orbEq) {
    for (let i = 0; i < 22; i++) {
      const bar = document.createElement("i");
      bar.style.animationDuration = `${0.55 + Math.random() * 0.9}s`;
      bar.style.animationDelay = `${Math.random() * -1.4}s`;
      orbEq.appendChild(bar);
    }
  }

  /* ============================================================
     PREMIUM LYRICS LAB — all 15 Android looks + AUTO
     ============================================================ */
  const voices = [
    ["all", "ALL"], ["sans", "SIGNATURE"], ["display", "DISPLAY"],
    ["serif", "SERIF"], ["poster", "POSTER"], ["mono", "MONO"],
  ];
  const fontLabel = {
    sans: "SIGNATURE SANS",
    display: "GEOMETRIC DISPLAY",
    serif: "CINEMATIC SERIF",
    poster: "GRAPHIC POSTER",
    mono: "TERMINAL MONO",
  };

  // name|look|font|primary|accent|glow|speed|glowAmount|signature
  const catalogText = `
Aurora|aurora|sans|EAF2FF|6FE7FF|A78BFA|0.7|0.95|Flowing ribbons of cool light
Noir|noir|serif|F5F1E8|E8C77B|8B7355|0.45|0.45|Quiet luxury, one gold hairline
Golden Hour|golden|serif|FFF6E5|F5C86B|FF8A5B|0.6|1.05|Amber halo and letterbox bars
Neon|neon|display|F0FDFF|22D3EE|F472B6|1|1.5|Cyan tubes with magenta bloom
Ember|ember|sans|FFF4EC|FF8A4C|FF3D5A|0.9|1.3|Rising sparks and a heat haze
Prism|prism|sans|FFFFFF|7DD3FC|F0ABFC|0.85|1|Chromatic split around every word
Chrome|chrome|display|FFFFFF|CBD5E1|818CF8|0.8|1.1|Polished metal and extruded layers
Terminal|terminal|mono|DCFCE7|34D399|6EE7B7|0.9|0.9|Green phosphor and a block cursor
Hologram|hologram|display|F0F9FF|7DD3FC|A78BFA|1|1.3|Travelling scan bar and ghost layers
Orbit|orbit|sans|F0FDFA|5EEAD4|818CF8|0.85|1.05|Elliptical rings and a satellite dot
Tidal|tidal|sans|EFF6FF|60A5FA|C084FC|0.85|1|Words riding a beat-driven sine bed
Depth|depth|display|F8FAFC|94A3B8|38BDF8|0.9|1|Layered extrusion with parallax
Echo|echo|serif|F8FAFC|C4B5FD|67E8F9|0.8|1.1|Ghost trails and a mirrored reflection
Poster|poster|poster|FFF7ED|FFB4A2|F472B6|0.7|1.05|Bold graphic type with a hard offset
Stardust|stardust|sans|F8FAFC|E0E7FF|7DD3FC|0.8|1.15|Drifting stars and cool dust
`.trim();

  const styles = catalogText.split("\n").map((line) => {
    const [name, look, font, primary, accent, glowColor, speed, glowAmount, signature] = line.split("|");
    return {
      name, look, font,
      primary: `#${primary}`, accent: `#${accent}`, glow: `#${glowColor}`,
      speed: +speed, glowAmount: +glowAmount, signature,
    };
  });
  const AUTO = {
    name: "AUTO · Mood Match", look: "aurora", font: "sans",
    primary: "#F2F5FF", accent: "#6CE5FF", glow: "#B388FF",
    speed: 1, glowAmount: 1, signature: "Matches sad, love, chill, party and dark songs",
  };

  const labStage = $("#lyricCanvas");
  const styleList = $("#styleList");
  const voiceTabs = $("#voiceTabs");
  const autoCard = $("#autoCard");
  const stageStyleName = $("#stageStyleName");
  const stageLook = $("#stageLook");
  const stageFont = $("#stageFont");
  const stageMode = $("#stageMode");
  const lookNote = $("#lookNote");
  const stageBars = $("#stageBars");
  const lyricParticles = $("#lyricParticles");
  const stageProgress = $("#stageProgress");
  const stageTime = $("#stageTime");
  const labPlay = $("#labPlay");
  const labShuffle = $("#labShuffle");

  let selected = styles[0];
  let activeTab = "all";
  let autoMode = false;
  let playing = true;
  let elapsedMs = 72000;
  const DURATION = 228000;

  for (let i = 0; i < 34; i++) stageBars.appendChild(document.createElement("i"));
  for (let i = 0; i < 26; i++) {
    const p = document.createElement("i");
    p.style.left = `${Math.random() * 100}%`;
    p.style.top = `${Math.random() * 100}%`;
    lyricParticles.appendChild(p);
  }

  function applyStyle(style, auto = false, modeLabel = null) {
    selected = style;
    labStage.style.setProperty("--c1", style.primary);
    labStage.style.setProperty("--c2", style.accent);
    labStage.style.setProperty("--c3", style.glow);
    labStage.style.setProperty("--spd", String(style.speed));
    labStage.style.setProperty("--glow", String(style.glowAmount));
    labStage.dataset.look = style.look;
    labStage.dataset.font = style.font;
    stageStyleName.textContent = style.name;
    stageLook.textContent = style.look.toUpperCase();
    stageFont.textContent = fontLabel[style.font] || "AUTO";
    stageMode.textContent = modeLabel || (auto ? "MOOD MATCH AUTO" : "MANUAL");
    if (lookNote) lookNote.textContent = style.signature;
    $$("#styleList button").forEach((b) => b.classList.toggle("active", b.dataset.name === style.name));
  }

  function renderTabs() {
    voiceTabs.innerHTML = "";
    voices.forEach(([key, label]) => {
      const b = document.createElement("button");
      b.type = "button"; b.textContent = label; b.setAttribute("role", "tab");
      b.className = key === activeTab ? "active" : "";
      b.addEventListener("click", () => { activeTab = key; renderTabs(); renderList(); });
      voiceTabs.appendChild(b);
    });
  }

  function renderList() {
    styleList.innerHTML = "";
    styles.filter((s) => activeTab === "all" || s.font === activeTab).forEach((s) => {
      const b = document.createElement("button");
      b.type = "button"; b.setAttribute("role", "option"); b.dataset.name = s.name;
      if (s === selected) b.classList.add("active");
      const label = document.createElement("span");
      label.textContent = s.name;
      const dot = document.createElement("i");
      dot.style.setProperty("--dot", s.accent);
      b.append(label, dot);
      b.addEventListener("click", () => { setAuto(false); applyStyle(s); });
      styleList.appendChild(b);
    });
  }

  function setAuto(on) {
    autoMode = on;
    autoCard.setAttribute("aria-pressed", String(on));
    autoLabel = "MOOD MATCH AUTO";
    stageMode.textContent = on ? "MOOD MATCH AUTO" : "MANUAL";
    if (on) applyStyle(AUTO, true);
  }
  autoCard.addEventListener("click", () => setAuto(!autoMode));
  labShuffle.addEventListener("click", () => {
    setAuto(false);
    applyStyle(styles[Math.floor(Math.random() * styles.length)]);
  });
  labPlay.addEventListener("click", () => {
    playing = !playing;
    labPlay.textContent = playing ? "❚❚" : "▶";
    labPlay.setAttribute("aria-label", playing ? "Pause demo" : "Play demo");
  });

  // AUTO demo mirrors the app's mood families, with hold hysteresis so the
  // preview never strobes between looks on a single transient.
  const moodDemo = [
    { name: "SAD", looks: ["echo", "stardust", "noir"] },
    { name: "LOVE", looks: ["aurora", "orbit", "golden"] },
    { name: "CHILL", looks: ["tidal", "hologram", "depth"] },
    { name: "PARTY", looks: ["neon", "poster", "prism"] },
    { name: "DARK", looks: ["terminal", "chrome", "ember"] },
  ];
  let autoHoldStart = performance.now();
  let autoLook = "noir";
  function chooseAuto(now, energy, drop, quiet) {
    const mood = quiet
      ? moodDemo[0]
      : drop > 0.7 || energy > 0.72
        ? moodDemo[3]
        : moodDemo[Math.floor(now / 11000) % moodDemo.length];
    const look = mood.looks[Math.floor(now / (mood === moodDemo[3] ? 5000 : 8000)) % mood.looks.length];
    autoLabel = `MOOD MATCH · ${mood.name}`;
    const held = now - autoHoldStart;
    const strong = drop > 0.7 && held >= 1800;
    if (look !== autoLook && !strong && held < 4000) return null;
    if (look === autoLook && !strong && held < 6000) return null;
    autoLook = look; autoHoldStart = now;
    return styles.find((s) => s.look === look) || styles[0];
  }
  let autoLabel = "MOOD MATCH AUTO";

  let lastLabTick = performance.now();
  function labTick(now) {
    const dt = Math.min(now - lastLabTick, 80);
    lastLabTick = now;
    if (playing) elapsedMs = (elapsedMs + dt) % DURATION;
    const pos = elapsedMs / DURATION;
    stageProgress.style.width = `${pos * 100}%`;
    const total = Math.floor(elapsedMs / 1000);
    stageTime.textContent = `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")} / 03:48`;

    const t = now / 1000;
    const beat = 0.5 + 0.5 * Math.sin(t * 6.2);
    const drop = (t % 4.6) < 0.22 ? 1 : 0;
    const quiet = Math.sin(t * 0.31) > 0.93;
    const energy = clamp(0.45 + 0.35 * Math.sin(t * 0.7) + beat * 0.2, 0, 1);

    $$("#stageBars i").forEach((b, i) => {
      const v = playing ? clamp(12 + beat * 46 * (0.4 + 0.6 * Math.abs(Math.sin(i * 0.7 + t * 2))) + drop * 28, 8, 100) : 10;
      b.style.height = `${v}%`;
    });
    if (["stardust", "aurora", "ember"].includes(selected.look)) {
      $$("#lyricParticles i").forEach((p, i) => {
        p.style.transform = `translate(${Math.sin(t + i) * 14}px, ${Math.cos(t * 0.8 + i) * 12}px)`;
        p.style.opacity = String(0.2 + 0.5 * Math.abs(Math.sin(t * 1.4 + i)));
      });
    }
    if (autoMode && playing) {
      const next = chooseAuto(now, energy, drop, quiet);
      if (next) applyStyle(next, true, autoLabel);
    }
    requestAnimationFrame(labTick);
  }

  renderTabs(); renderList(); applyStyle(styles[0]); requestAnimationFrame(labTick);

  /* ============================================================
     CODE POETRY — typed editor preview (demo tokens only)
     ============================================================ */
  const editorBody = $("#editorBody");
  if (editorBody) {
    const code = [
      [["com", "// CODE POETRY · real provider words"]],
      [["key", "import"], ["punc", " { engine } "], ["key", "from"], ["str", ' "aura/music"'], ["punc", ";"]],
      [],
      [["key", "const"], ["punc", " clock  = "], ["fn", "engine"], ["punc", "."], ["fn", "positionMs"], ["punc", ";"]],
      [["key", "const"], ["punc", " words  = "], ["fn", "provider"], ["punc", "."], ["fn", "words"], ["punc", ";  "], ["com", "// verbatim"]],
      [["key", "const"], ["punc", " mood   = "], ["fn", "analysis"], ["punc", "."], ["fn", "mood"], ["punc", ";     "], ["com", "// visual only"]],
      [],
      [["fn", "render"], ["punc", "("], ["fn", "words"], ["punc", ", "], ["fn", "clock"], ["punc", ", {"]],
      [["punc", "  palette: "], ["str", '"Midnight Aurora"'], ["punc", ","]],
      [["punc", "  motion:  "], ["str", '"Neon"'], ["punc", ","]],
      [["punc", "  glow:    "], ["num", "0.85"], ["punc", ","]],
      [["punc", "  timing:  "], ["str", '"verified"'], ["punc", ","]],
      [["punc", "});"]],
    ];

    const totalChars = code.reduce((n, line) => n + line.reduce((m, t) => m + t[1].length, 0) + 1, 0);
    let typed = reduce ? totalChars : 0;
    let activeLine = reduce ? 8 : 2;

    function renderCode() {
      editorBody.innerHTML = "";
      let remaining = typed;
      code.forEach((line, li) => {
        const row = document.createElement("div");
        row.className = "code-line" + (li === activeLine ? " active" : "");
        const ln = document.createElement("span");
        ln.className = "ln"; ln.textContent = String(li + 1).padStart(2, "0");
        row.appendChild(ln);
        const content = document.createElement("span");
        if (remaining > 0 && line) {
          line.forEach(([cls, text]) => {
            const take = Math.max(0, Math.min(text.length, remaining));
            if (take > 0) {
              const sp = document.createElement("span");
              sp.className = `tok-${cls}`; sp.textContent = text.slice(0, take);
              content.appendChild(sp);
              remaining -= take;
            }
          });
        }
        if (li === activeLine) {
          const cursor = document.createElement("span");
          cursor.className = "cursor"; content.appendChild(cursor);
        }
        row.appendChild(content);
        editorBody.appendChild(row);
        if (line) remaining -= 1;
      });
    }
    if (!reduce) {
      const typeTimer = setInterval(() => {
        typed = Math.min(totalChars, typed + 2);
        if (typed >= totalChars) clearInterval(typeTimer);
        renderCode();
      }, 26);
      setInterval(() => { activeLine = (activeLine % 11) + 1; renderCode(); }, 1900);
    }
    renderCode();
  }

  /* ============================================================
     3D VISUALIZER — real Web Audio FFT or simulated beat
     ============================================================ */
  const vizCanvas = $("#vizCanvas");
  const vizChips = $("#vizChips");
  const vizLabel = $("#vizLabel");
  const vizHint = $("#vizHint");
  const audioFile = $("#audioFile");
  const vizPlayBtn = $("#vizPlay");
  const vizMicBtn = $("#vizMic");

  const vizModes = ["AUTO", "SPECTRUM", "RADIAL", "WAVE", "PARTICLES", "RINGS", "AURORA", "3D PATTERN"];
  let vizMode = "3D PATTERN";
  let vizPlaying = true;
  let audioCtx = null, analyser = null, freq = null;
  let audioEl = null, mediaSource = null, micStream = null;
  let vizW = 0, vizH = 0, vizDpr = 1;
  let vizMotionMs = 0, lastVizFrameMs = performance.now();

  const vizCtx = vizCanvas.getContext("2d");
  watchCanvas(vizCanvas, vizCtx, ({ w, h, dpr }) => {
    vizW = w; vizH = h; vizDpr = dpr;
  });

  vizModes.forEach((m) => {
    const b = document.createElement("button");
    b.type = "button"; b.textContent = m; b.setAttribute("role", "tab");
    if (m === vizMode) b.classList.add("active");
    b.addEventListener("click", () => {
      vizMode = m;
      $$("#vizChips button").forEach((x) => x.classList.toggle("active", x === b));
      vizLabel.textContent = m;
    });
    vizChips.appendChild(b);
  });

  function ensureContext() {
    if (audioCtx) return audioCtx;
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 1024;
    analyser.smoothingTimeConstant = 0.82;
    freq = new Uint8Array(analyser.frequencyBinCount);
    analyser.connect(audioCtx.destination);
    return audioCtx;
  }

  function readSpectrum(t) {
    if (analyser && vizPlaying) analyser.getByteFrequencyData(freq);
    if (freq && analyser) return freq;
    const n = 256;
    const fake = new Uint8Array(n);
    const beat = Math.pow(0.5 + 0.5 * Math.sin(t * 5.4), 3);
    for (let i = 0; i < n; i++) {
      const fall = Math.exp(-i / 60);
      fake[i] = Math.min(255, (18 + 110 * fall + 90 * beat * fall) * (0.7 + 0.3 * Math.sin(i * 0.31 + t * 2)));
    }
    return fake;
  }

  function band(data, a, b) {
    let sum = 0;
    for (let i = a; i < b; i++) sum += data[i] || 0;
    return sum / Math.max(1, b - a) / 255;
  }

  function project3D(angle, radius, zOffset, focal) {
    const x = Math.cos(angle) * radius;
    const z = Math.sin(angle) * radius + zOffset;
    const s = focal / (focal + z + 260);
    return { x: vizW / 2 + x * s, z, s };
  }

  function draw3DRing(data, t, colorA, colorB) {
    const count = 72;
    const rotation = t * 0.42;
    const items = [];
    for (let i = 0; i < count; i++) {
      const angle = (i / count) * Math.PI * 2 + rotation;
      const v = data[Math.floor((i / count) * 130)] / 255;
      const h = 18 + v * 150;
      items.push({ angle, h, v, i });
    }
    items.sort((a, b) => Math.sin(a.angle) - Math.sin(b.angle));
    const baseY = vizH * 0.54;
    items.forEach((it) => {
      const p = project3D(it.angle, Math.min(vizW, vizH) * 0.29, 0, 620);
      const topY = baseY - it.h * p.s;
      const hueMix = it.i / count;
      const col = hueMix < 0.5 ? colorA : colorB;
      vizCtx.globalAlpha = clamp(0.35 + p.s * 0.7, 0.25, 1);
      vizCtx.strokeStyle = col;
      vizCtx.lineWidth = Math.max(1, 3.4 * p.s);
      vizCtx.shadowBlur = 16 * p.s; vizCtx.shadowColor = col;
      vizCtx.beginPath();
      vizCtx.moveTo(p.x, baseY + 10 * p.s);
      vizCtx.lineTo(p.x, topY);
      vizCtx.stroke();
    });
    vizCtx.globalAlpha = 1; vizCtx.shadowBlur = 0;
  }

  function drawSpectrum(data, t, a, b) {
    const n = 64, gap = 4, bw = (vizW - 40) / n;
    for (let i = 0; i < n; i++) {
      const v = data[Math.floor(i * 1.7)] / 255;
      const h = 8 + v * vizH * 0.68;
      const x = 20 + i * (bw + gap) * (bw / (bw + gap));
      const grad = vizCtx.createLinearGradient(0, vizH, 0, vizH - h);
      grad.addColorStop(0, a); grad.addColorStop(1, b);
      vizCtx.fillStyle = grad;
      vizCtx.shadowBlur = 14; vizCtx.shadowColor = b;
      vizCtx.fillRect(x, vizH - h, Math.max(2, bw - gap), h);
    }
    vizCtx.shadowBlur = 0;
  }

  function drawRadial(data, t, a, b) {
    const cx = vizW / 2, cy = vizH / 2;
    const base = Math.min(vizW, vizH) * 0.17;
    const n = 96;
    vizCtx.lineWidth = 2;
    for (let i = 0; i < n; i++) {
      const angle = (i / n) * Math.PI * 2;
      const v = data[Math.floor((i / n) * 120)] / 255;
      const r1 = base;
      const r2 = base + 12 + v * Math.min(vizW, vizH) * 0.2;
      vizCtx.strokeStyle = i % 2 ? a : b;
      vizCtx.globalAlpha = 0.55 + v * 0.45;
      vizCtx.beginPath();
      vizCtx.moveTo(cx + Math.cos(angle) * r1, cy + Math.sin(angle) * r1);
      vizCtx.lineTo(cx + Math.cos(angle) * r2, cy + Math.sin(angle) * r2);
      vizCtx.stroke();
    }
    vizCtx.globalAlpha = 1;
  }

  function drawWave(data, t, a, b) {
    const bass = band(data, 0, 20);
    for (let layer = 0; layer < 3; layer++) {
      vizCtx.beginPath();
      vizCtx.lineWidth = 2 - layer * 0.4;
      vizCtx.strokeStyle = layer === 1 ? b : a;
      vizCtx.globalAlpha = 0.8 - layer * 0.2;
      vizCtx.shadowBlur = 18; vizCtx.shadowColor = b;
      for (let x = 0; x <= vizW; x += 4) {
        const p = x / vizW;
        const y = vizH / 2
          + Math.sin(p * 12 + t * 2.4 + layer) * (26 + bass * 90)
          + Math.sin(p * 29 - t * 1.3) * 14 - layer * 18;
        x === 0 ? vizCtx.moveTo(x, y) : vizCtx.lineTo(x, y);
      }
      vizCtx.stroke();
    }
    vizCtx.globalAlpha = 1; vizCtx.shadowBlur = 0;
  }

  function drawParticles(data, t, a, b) {
    const energy = band(data, 0, 60);
    const n = 170;
    for (let i = 0; i < n; i++) {
      const fi = i / n;
      const theta = fi * Math.PI * 2 * 5 + t * 0.3;
      const phi = Math.acos(1 - 2 * fi);
      const r = Math.min(vizW, vizH) * (0.16 + energy * 0.22) * (0.6 + 0.4 * Math.sin(i * 2.3 + t));
      const x = vizW / 2 + r * Math.sin(phi) * Math.cos(theta);
      const y = vizH / 2 + r * Math.sin(phi) * Math.sin(theta) * 0.62 + r * Math.cos(phi) * 0.35;
      const sz = 1 + (data[i % 120] / 255) * 2.6;
      vizCtx.fillStyle = i % 3 === 0 ? b : a;
      vizCtx.globalAlpha = 0.35 + (data[i % 120] / 255) * 0.6;
      vizCtx.beginPath(); vizCtx.arc(x, y, sz, 0, Math.PI * 2); vizCtx.fill();
    }
    vizCtx.globalAlpha = 1;
  }

  function drawRings(data, t, a, b) {
    const bass = band(data, 0, 30);
    for (let i = 0; i < 6; i++) {
      const r = Math.min(vizW, vizH) * (0.1 + i * 0.065) + bass * 36 * (i + 1) / 6;
      vizCtx.save();
      vizCtx.translate(vizW / 2, vizH / 2);
      vizCtx.rotate(t * (i % 2 ? 0.4 : -0.28) + i);
      vizCtx.scale(1, 0.36 + i * 0.07);
      vizCtx.beginPath();
      vizCtx.arc(0, 0, r, 0, Math.PI * 2);
      vizCtx.strokeStyle = i % 2 ? a : b;
      vizCtx.globalAlpha = 0.75 - i * 0.08;
      vizCtx.lineWidth = 1.6;
      vizCtx.shadowBlur = 16; vizCtx.shadowColor = b;
      vizCtx.stroke();
      vizCtx.restore();
    }
    vizCtx.globalAlpha = 1; vizCtx.shadowBlur = 0;
  }

  function drawAurora(data, t, a, b) {
    const energy = band(data, 0, 80);
    const grad = vizCtx.createLinearGradient(0, 0, 0, vizH);
    grad.addColorStop(0, "rgba(0,0,0,0)");
    grad.addColorStop(1, a);
    vizCtx.fillStyle = grad;
    vizCtx.globalAlpha = 0.28 + energy * 0.3;
    for (let k = 0; k < 4; k++) {
      vizCtx.beginPath();
      vizCtx.moveTo(0, vizH);
      for (let x = 0; x <= vizW; x += 8) {
        const p = x / vizW;
        const y = vizH * (0.28 + k * 0.12)
          + Math.sin(p * 5 + t * (0.6 + k * 0.15)) * (40 + energy * 60)
          + Math.sin(p * 13 - t) * 16;
        vizCtx.lineTo(x, y);
      }
      vizCtx.lineTo(vizW, vizH); vizCtx.closePath();
      vizCtx.fillStyle = k % 2 ? b : a;
      vizCtx.globalAlpha = 0.06 + energy * 0.08;
      vizCtx.fill();
    }
    vizCtx.globalAlpha = 1;
  }

  function vizFrame(ms) {
    const delta = clamp(ms - lastVizFrameMs, 0, 80);
    lastVizFrameMs = ms;
    if (vizPlaying) vizMotionMs += delta;
    const t = vizMotionMs / 1000;
    const data = readSpectrum(t);
    vizCtx.clearRect(0, 0, vizW, vizH);
    const bg = vizCtx.createRadialGradient(vizW / 2, vizH / 2, 0, vizW / 2, vizH / 2, Math.max(vizW, vizH) * 0.7);
    bg.addColorStop(0, "rgba(30,14,54,0.55)");
    bg.addColorStop(1, "rgba(0,0,0,0)");
    vizCtx.fillStyle = bg; vizCtx.fillRect(0, 0, vizW, vizH);

    const bass = band(data, 0, 24);
    const mode = vizMode === "AUTO"
      ? (bass > 0.72 ? "3D PATTERN" : bass > 0.52 ? "RINGS" : band(data, 24, 90) > 0.4 ? "SPECTRUM" : "WAVE")
      : vizMode;
    if (vizLabel.textContent !== vizMode) vizLabel.textContent = vizMode;

    const a = "#6CE5FF", b = "#B388FF";
    if (mode === "SPECTRUM") drawSpectrum(data, t, a, b);
    else if (mode === "RADIAL") drawRadial(data, t, a, b);
    else if (mode === "WAVE") drawWave(data, t, a, b);
    else if (mode === "PARTICLES") drawParticles(data, t, a, b);
    else if (mode === "RINGS") drawRings(data, t, a, b);
    else if (mode === "AURORA") drawAurora(data, t, a, b);
    else draw3DRing(data, t, a, b);

    requestAnimationFrame(vizFrame);
  }
  requestAnimationFrame(vizFrame);

  vizPlayBtn.addEventListener("click", async () => {
    vizPlaying = !vizPlaying;
    vizPlayBtn.textContent = vizPlaying ? "Pause" : "Play";
    if (audioCtx?.state === "suspended" && vizPlaying) await audioCtx.resume();
    if (audioEl) vizPlaying ? audioEl.play().catch(() => {}) : audioEl.pause();
  });

  audioFile.addEventListener("change", async () => {
    const file = audioFile.files?.[0];
    if (!file) return;
    try {
      ensureContext();
      if (audioCtx.state === "suspended") await audioCtx.resume();
      if (micStream) { micStream.getTracks().forEach((t) => t.stop()); micStream = null; }
      if (mediaSource) { mediaSource.disconnect(); mediaSource = null; }
      if (audioEl) { audioEl.pause(); URL.revokeObjectURL(audioEl.src); }
      audioEl = new Audio(URL.createObjectURL(file));
      audioEl.loop = true;
      mediaSource = audioCtx.createMediaElementSource(audioEl);
      mediaSource.connect(analyser);
      vizMotionMs = 0;
      lastVizFrameMs = performance.now();
      await audioEl.play();
      vizPlaying = true; vizPlayBtn.textContent = "Pause";
      vizHint.textContent = `LOCAL FILE · ${file.name.slice(0, 28).toUpperCase()}`;
    } catch (err) {
      vizHint.textContent = "AUDIO UNAVAILABLE · SIMULATED BEAT";
    }
  });

  vizMicBtn.addEventListener("click", async () => {
    try {
      ensureContext();
      if (audioCtx.state === "suspended") await audioCtx.resume();
      if (audioEl) audioEl.pause();
      if (mediaSource) { mediaSource.disconnect(); mediaSource = null; }
      micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const src = audioCtx.createMediaStreamSource(micStream);
      src.connect(analyser);
      vizPlaying = true; vizPlayBtn.textContent = "Pause";
      vizHint.textContent = "MIC INPUT · ON THIS DEVICE ONLY";
    } catch (err) {
      vizHint.textContent = "MIC BLOCKED · SIMULATED BEAT";
    }
  });
})();
