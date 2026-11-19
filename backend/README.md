# AURA MUSIC companion backend

A small, clean-room Python 3.12 **standard-library-only** backend for the AURA MUSIC app (developer Jordan).

It does two jobs:

1. `POST /api/groq/analyze` — sends track metadata/lyrics to Groq's OpenAI-compatible chat completions endpoint and returns a strict JSON analysis used by the app's cinematic engine. If Groq is unavailable, it returns a deterministic local fallback with `degraded: true` and HTTP 200, so playback never breaks.
2. A desktop **receiver** signalling relay — the phone publishes lyric/visual state, a browser polls it, and the receiver renders a cinematic visualizer. **The receiver never plays audio and never fetches media.** Audio stays on your phone.

All state is in memory. Restarting the process clears sessions, rate-limit buckets and the cache.

---

## Requirements

* Python 3.12 (no `pip install`; see `requirements.txt`)
* A Groq API key **only** if you want live analysis (otherwise the fallback is used)

## Environment variables

No credentials are hardcoded. Set these in your shell before starting the server.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `GROQ_API_KEY` | for live analysis | unset | Groq API key. If unset, `/api/groq/analyze` returns the local fallback. |
| `AURA_CLIENT_TOKEN` | for all `/api/*` POST calls from the app | unset | Shared bearer token. If unset, every client-authenticated endpoint returns 401. |
| `AURA_GROQ_MODEL` | no | `llama-3.3-70b-versatile` | Groq model id. **Verify availability — see below.** |
| `AURA_PORT` | no | `8787` | Listening port. |
| `AURA_BIND` | no | `127.0.0.1` | Bind address. Use `0.0.0.0` only with TLS and a firewall. |
| `AURA_ALLOWED_ORIGIN` | no | `*` | CORS `Access-Control-Allow-Origin`. Set to your exact web origin in production (e.g. `https://receiver.example.com`). |

> **Model availability:** Groq retires and adds models. Web verification was unavailable when this backend was written, so `llama-3.3-70b-versatile` is a *configurable default, not a verified guarantee*. Check it in your own environment with the curl command below and change `AURA_GROQ_MODEL` if needed.

### Verify the configured model

```bash
# PowerShell
$body = @{
  model = if ($env:AURA_GROQ_MODEL) { $env:AURA_GROQ_MODEL } else { "llama-3.3-70b-versatile" }
  messages = @(@{ role = "user"; content = "Reply with OK" })
  max_tokens = 8
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Uri "https://api.groq.com/openai/v1/chat/completions" `
  -Method Post -Headers @{ "Authorization" = "Bearer $env:GROQ_API_KEY" } `
  -ContentType "application/json" -Body $body
```

```bash
# bash
curl -sS https://api.groq.com/openai/v1/chat/completions \
  -H "Authorization: Bearer $GROQ_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"${AURA_GROQ_MODEL:-llama-3.3-70b-versatile}\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply with OK\"}],\"max_tokens\":8}"
```

A `model not found` error means you must set `AURA_GROQ_MODEL` to a currently available id.

---

## Running

From this directory:

```bash
# PowerShell
$env:AURA_CLIENT_TOKEN = "<a-long-random-token-you-generate>"
$env:GROQ_API_KEY = "<your-groq-key>"   # optional
python server.py
```

```bash
# bash
export AURA_CLIENT_TOKEN="<a-long-random-token-you-generate>"
export GROQ_API_KEY="<your-groq-key>"   # optional
python server.py
```

CLI overrides: `--host`, `--port`, `--model`, `--allowed-origin`, `--cert`, `--key`.

Check it:

```bash
curl http://127.0.0.1:8787/api/health
```

The health response contains the service status, whether a Groq key is configured, the **model name** (never the key), and the active receiver session count. It needs no auth.

### HTTPS/TLS for LAN use

The phone and laptop are usually on the same Wi-Fi. For LAN use, bind to your LAN address and serve HTTPS with a certificate the devices trust:

```bash
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout key.pem -out cert.pem \
  -subj "/CN=192.168.1.20" \
  -addext "subjectAltName=IP:192.168.1.20,DNS:aura.local"

python server.py --host 0.0.0.0 --cert cert.pem --key key.pem
```

Then use `https://192.168.1.20:8787/receiver` on the laptop/tablet and point the Android app at the same origin. Both `--cert` and `--key` must be supplied together. Install/accept the certificate on the client devices; a self-signed certificate will otherwise produce a trust warning. The Android client must trust this CA (user CA or a proper LAN certificate) for TLS to work.

Without TLS, keep the default bind (`127.0.0.1`) — that is safe for a single machine.

---

## Pairing workflow

1. In the app, choose **Cinematic receiver → Pair a screen**. The app calls
   `POST /api/receiver/create` with the client bearer token and receives
   `{code, session_id, expires_at, receiver_url}`.
2. Open `receiver_url` (or `/receiver`) on the laptop/browser. Enter the **6-digit code** and a receiver name (e.g. `Laptop`), then press **Pair device**.
   The browser receives `{session_token, session_id, receiver_url}`.
3. The browser stores the session token **in `sessionStorage` only** and starts polling `GET /api/receiver/state?session_token=...`.
4. The app calls `POST /api/receiver/publish` on lyric/theme/position changes. The receiver renders word-by-word lyrics, album-art placeholder, beat/bass reactive background, ambient particles and smoothly interpolated theme colours.
5. `POST /api/receiver/disconnect` removes the session.

Codes are cryptographically random, **single-use** and expire after ~15 minutes. Sessions expire too. The receiver deliberately shows *"Waiting for your phone…"* until a successful poll actually returns state — it never claims a connection just because pairing succeeded.

Select 16:9, 16:10 or 21:9 with the buttons in the receiver's top-right corner (pure CSS aspect-ratio).

### Receiver API summary

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `GET` | `/api/health` | none | Status, `groq_configured`, `model`, session count. |
| `POST` | `/api/groq/analyze` | bearer client token | Returns strict JSON; fallback with `degraded: true` on any Groq failure. |
| `POST` | `/api/receiver/create` | bearer client token | Creates code + session. |
| `POST` | `/api/receiver/pair` | none (code is the capability) | Single-use code → session token. |
| `POST` | `/api/receiver/publish` | bearer client token + `session_token` in body | Stores latest visual state. |
| `GET` | `/api/receiver/state?session_token=...` | session token | `200` state, `204` waiting, `404` expired/unknown. |
| `POST` | `/api/receiver/disconnect` | bearer client token | Removes the session. |
| `GET` | `/receiver`, `/receiver.js` | none | Static receiver assets. |
| `OPTIONS` | any | none | CORS preflight → 204. |

`POST /api/groq/analyze` body:

```json
{
  "title": "Song",
  "artist": "Artist",
  "lyrics": "... max 12000 chars ...",
  "genre_tags": ["indie", "dream pop"],
  "language": "en",
  "audio": { "energy": 0.6, "bass": 0.4, "tempo": 0.7 }
}
```

Response fields: `mood` (one of the app's 12 `CinematicMood` names), `confidence` (0..1), `energy_hint` (0..1), `genre_tags`, `theme_hint` (one of the app's 15 `CinematicTheme` names), `sections` (`[{name,start_ms,end_ms}]`), `explanation`, `degraded`.

`POST /api/receiver/publish` body (excerpt):

```json
{
  "session_token": "...",
  "title": "Song", "artist": "Artist", "lyrics": "...",
  "lyrics_visible": true, "playing": true,
  "position_ms": 42100, "duration_ms": 210000,
  "theme": { "background": "#050407", "primary": "#e8c77b" },
  "features": { "bass": 0.4, "beat": 0.8, "energy": 0.6 },
  "words": [{ "text": "Hello", "start_ms": 42000, "end_ms": 42500 }]
}
```

---

## Important hardware / capability limits

* **USB charging or USB data alone is not video output.** A cable from the phone to a display does not magically make a video feed; this receiver works over the network, not over USB display protocols.
* **Google Cast / Chromecast visual output is NOT provided by this generic receiver.** Casting requires the Cast SDK and a registered Cast receiver app. This backend is a plain HTTP signalling server plus a browser page — it does not speak the Cast protocol.
* **Audio stays on your phone.** The receiver displays lyrics and reactive visuals only. No audio stream is sent to the backend or played by the browser.

---

## Security notes

* **Never put `GROQ_API_KEY` or the real `AURA_CLIENT_TOKEN` in the APK.** Ship no secrets in the client. The client token is a shared demo token: anyone who extracts it can call the API. Rotate `AURA_CLIENT_TOKEN` regularly and treat it as a shared secret, not per-user identity. For real deployments, replace it with per-device tokens and scoped permissions.
* **Use HTTPS on LAN** (`--cert` / `--key`) whenever the phone is not on the same machine. Bearer tokens and session tokens travel in requests; plain HTTP exposes them to anyone on the network.
* The receiver stores only the short-lived session token, in `sessionStorage` (cleared when the tab closes). It is never placed in a URL or `localStorage`.
* Requests are rate-limited (20/min per token or IP), bodies are size-capped (64 KB), lyrics are capped at 12,000 characters, Groq calls have a fixed 15 s timeout, and responses are cached by SHA-256 of the normalized request for 6 h.
* Logs contain method, sanitized path, status and error *types* only — never keys, tokens, request bodies or lyrics. Query strings are stripped from logged paths because state requests carry a token in the query.
* CORS defaults to `*` for local development; set `AURA_ALLOWED_ORIGIN` to your exact receiver origin in production. This server is not a substitute for an auth gateway or WAF.

---

## Tests

```bash
cd backend
python test_server.py
```

The tests start the real `ThreadingHTTPServer` on an ephemeral local port and cover health, auth, fallback, single-use pairing, publish/state/disconnect, validation and CORS preflight.
