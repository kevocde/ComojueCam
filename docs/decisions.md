# Decision Log

Chronological record of feature decisions and trade-offs. Newest entries first.
Use this file to recall *why* the code is the way it is.

## 2026-10-16 — Low-power mode re-arm (PR #11)

- Bug: the app entered the dimmed idle display only once; after waking, it never returned.
- Cause: `resetInactivityTimer()` early-returned after exiting low power without re-posting the
  `Handler` callback, and `idleOverlay.onWake` called `exitLowPowerMode()` directly.
- Fix: waking and any interaction now always re-arms the 90 s countdown.

## 2026-10-16 — Magnetometer + screen-frame axes + socket timeouts (PR #10)

- Telemetry payload adds `"magnetometer": {x, y, z}` in microteslas (peak with sign per axis).
- Sensor values arrive in the portrait device frame regardless of display rotation. Since the app
  is landscape-locked, all vectors are remapped to the screen frame via
  `WindowManager.defaultDisplay.rotation` (`ROTATION_90`: `x' = -y, y' = x`; z unchanged).
  Collision G-force magnitude is rotation-invariant and was left untouched.
- `Socket(host, port)` had no connect timeout: an unreachable host blocked the telemetry
  HandlerThread for minutes, freezing ticks and sensor events (payload showed zeros). Now connect
  and handshake are capped at 8 s.
- Samples published within the same second collided as JSON keys; publishing now dedupes per second.

## 2026-10-16 — Gyro zeros: peak instead of average (PR #9)

- Root cause of always-zero gyro: the signed *average* of a 10 s window cancels vibration/noise,
  and 2-decimal rounding crushed the residue to 0. Accelerometer looked fine only because gravity
  dominates it.
- Aggregation is now the *signed peak per axis* (largest |value| in the window), precision 3
  decimals for vectors, 5 for coordinates.

## 2026-10-16 — Persistent telemetry outbox (PR #8)

- Pattern: Outbox. Packets (JSON batches) are created **only while recording** and persisted as
  files in `filesDir/telemetry_outbox/` (atomic tmp+rename, oldest-first drain, cap of 288 files
  ≈ 24 h).
- Drain triggers: `ConnectivityManager.registerDefaultNetworkCallback` (app-wide; also fires at
  registration, so pending data goes out at app start), REC start, each EACH tick, REC stop.
- Backoff on send failure: 30, 60, 120, 240, 480 s (x2, max 5 retries), pause until the next
  trigger; any success resets the counter.
- Side fix: `TelemetryManager.stop()` used to run network I/O on the UI thread
  (`NetworkOnMainThreadException`); all drain work is posted to the telemetry `HandlerThread`.

## 2026-10-16 — Telemetry over WebSocket (PR #7)

- Feature: sends gyroscope, accelerometer, GPS speed/position grouped by epoch-second timestamps.
- Sampling window configurable 2-30 s (default 10 s); send interval EACH configurable 1-5 min;
  both with retro +/- controls in a 4th settings tab "TEL" plus ON/OFF toggle and token display.
- Token format `xxx-xxx`, generated once on first launch; TEL settings persist in
  SharedPreferences (the only persisted settings in the app).
- Telemetry lifecycle: collection and packet creation run only while recording; battery-friendly.
- `android.net.ws.WebSocket` was rejected: it is `@SystemApi`, not in the public SDK android.jar
  (verified against API 37 platform). Instead a minimal RFC 6455 client (~110 lines) over
  `Socket`/`SSLContext`, keeping zero dependencies and `minSdk = 24`.
- Short-lived connections: each send does open → frame → close; a persistent socket adds battery
  cost without benefit at one burst per EACH minutes.
- Server URL precedence: env `TELEMETRY_URL` → gradle property `telemetryUrl` → `local.properties`
  `telemetryUrl=` → default `ws://10.0.2.2:8080` (emulator host). Endpoint path: `/ws/{token}`.
- New permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION` (runtime request,
  only when TEL is enabled).
- No test libraries added (project stays dependency-free; user agreed to skip the TDD rule).

## Earlier

See `git log main --merges` for PRs #1-#6 (loop recording, retro UI, collision detection,
low-power display, rename).
