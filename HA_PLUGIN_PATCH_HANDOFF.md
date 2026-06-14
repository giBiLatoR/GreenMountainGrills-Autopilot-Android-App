# GMG Home Assistant Plugin — Patch Handoff

**For:** an agent patching the Home Assistant integration
[`giBiLatoR/Green-Mountain-Grills`](https://github.com/giBiLatoR/Green-Mountain-Grills)
(fork of `hallyaus/Green-Mountain-Grills`, MIT).

**Source of these findings:** building a standalone Android port of this integration
([`giBiLatoR/GreenMountainGrills-Autopilot-Android-App`](https://github.com/giBiLatoR/GreenMountainGrills-Autopilot-Android-App))
and running it against a **real grill**. The port re-implemented the cook
physics/state-machine/protocol in Kotlin; in doing so it (a) verified the wire
protocol against captured hardware frames and (b) found several cook-logic bugs and
UX shortcomings that apply equally to the Python integration. This doc lists each as
an actionable patch with file/function targets, the fix, the rationale, and how to
verify.

Repo layout referenced below (paths under `custom_components/gmg/` unless noted):
`api/protocol.py`, `api/const.py`, `cook_physics.py`, `cook_manager.py`, `sensor.py`,
`number.py`, `select.py`, `static/gmg-smoker-strategy.js`, `docs/PROTOCOL.md`,
`tests/test_protocol.py`, `tests/test_cook_physics.py`.

---

## 0. Hardware ground truth (verified this session)

- Real grill: serial **GMG12137138**, **Trek** (`grill_type = 1`), firmware
  `UNDC01SUF0_7.1`, on UDP `:8080`.
- The status reply to `UR001!` is **55 bytes**, not 36. The first 36 bytes are the
  documented status frame; the tail is firmware ASCII (`...DC01SUF07.1...`).
  `parse_status_frame` already slices `data[:STATUS_FRAME_LEN]`, so this is fine —
  **but `is_status_frame`/length checks must accept `len >= 36`, not `== 36`** (they
  already use `>=`; keep it that way).
- **Golden frame** (real capture, grill idle/off), keep as a test fixture:
  ```
  55 52 48 00 4a 00 96 00 06 03 14 32 19 19 00 00 00 00 00 00
  ff ff ff ff 00 00 00 00 00 00 00 00 01 00 00 01 00 00 f7 00
  fd 44 43 30 31 53 55 46 30 37 2e 31 00 1c fe
  ```
  Decodes to: grill **72°F** (`@2`), probe1 **74** (`@4`), setpoint **150** (`@6`),
  warn **0/NONE** (`@24` u32), probe1_target 0 (`@28`), **power OFF** (`@30`=0),
  **fire OFF** (`@32`=1), hopper 0 (`@33`), **grill_type 1 = Trek** (`@35`).
- The committed **`api/protocol.py` offsets are CORRECT** and match this frame:
  grill@2, probe1@4, setpoint@6, probe2@16, probe2_target@18, profile_time@20 (u32),
  warn@24 (u32), probe1_target@28, **power@30, fire@32, hopper@33, grill_type@35**.
  **Do not change `protocol.py`'s parse offsets.**

---

## 1. HIGH — Protocol tests are dead, and `docs/PROTOCOL.md` is wrong

Two defects; the *code* is fine, the *tests and docs* are not.

### 1a. `tests/test_protocol.py` self-skips → zero protocol coverage
It imports `build_set_grill_temp` / `build_set_probe_target`, but the real functions
are **`encode_set_grill_temp` / `encode_set_probe_target`**. The `try/except
ImportError` therefore fires `pytest.skip(allow_module_level=True)` and the **entire
file is skipped**. Nobody noticed because it skips silently.

Also its `_make_frame` helper writes fields at **stale offsets** (`power_state` at 26,
`fire_state` at 28, `warn_code` at 20, `grill_type` at 31). These do **not** match the
real parser (power@30, fire@32, warn@24, grill_type@35), so even if the import were
fixed, the parse assertions would fail.

**Fix:**
1. Rename the imported builders to `encode_set_grill_temp` / `encode_set_probe_target`
   (and the call sites in the command-builder tests).
2. Rewrite `_make_frame` to the **correct live offsets**:
   `header@0, grill@2, probe1@4, grill_set@6, probe2@16, probe2_set@18,
   profile_remaining@20 (u32), warn@24 (u32), probe1_set@28, power@30, fire@32,
   hopper@33, grill_type@35`.
3. Add a **golden-frame test** using the hex in §0 — assert grill=72, setpoint=150,
   power OFF, fire OFF, grill_type=1, warn NONE, probe sentinel handling.

Reference Kotlin implementation that already does this correctly:
`app/src/test/java/com/gibilator/gmg/ProtocolTest.kt` in the Android repo (the
`makeFrame` helper + `parsesRealCapturedFrame`).

### 1b. `docs/PROTOCOL.md` status-frame table uses the OLD offsets
The "Status frame layout" table lists power@26, grill_mode@27, fire@28, hopper@29,
profile_end@30, grill_type@31, warn@20, probe1_set@24. **All wrong** vs the code +
hardware. Correct it to match `protocol.py` / §0:
`profile_time_remaining @20–23 (u32)`, `warn @24–27 (u32)`, `probe1_target @28–29`,
`power @30`, `fire @32`, `hopper @33`, `grill_type @35`; `probe2 @16–17`,
`probe2_target @18–19`. Note `89` = probe-unplugged sentinel and `128` = low-pellet
alias of `8`.

**Verify:** `pytest tests/test_protocol.py` now *runs* (not skipped) and passes,
including the golden-frame case.

---

## 2. HIGH — Hold the pit setpoint during the cold-start sequence

**Bug (confirmed on hardware):** the GMG controller mishandles a grill **setpoint
written while it's still in the cold-start / ignition sequence** (grill below its
~150°F minimum operating temp) — it "gets confused." Today `cook_manager.start_cook`
calls `await self._set_pit_target(target, reason="preheat")` **immediately** at
PLANNED→PREHEATING, while the grill is cold. That's the trigger.

**Fix in `cook_manager.py`:**
- Add a constant `LAUNCH_READY_TEMP_F = 150` (== the grill's minimum operating temp).
- Add `pit_target_applied: bool = False` to `CookSession`.
- In `start_cook` (non-coach branch): power on as today, but only push the setpoint
  immediately if already hot, else defer:
  ```python
  if snapshot.power_state is PowerState.OFF:
      try:
          await self.coordinator.async_power_on()
      except Exception:
          LOGGER.exception("auto power-on failed at PLANNED→PREHEATING")
  if snapshot.grill_temp >= LAUNCH_READY_TEMP_F:
      await self._set_pit_target(target, reason="preheat")
      session.pit_target_applied = True
  # else: applied later by update() once the grill clears 150°F
  session.state = CookState.PREHEATING
  ```
- In `update()` at the **top of the `PREHEATING` branch**, apply the deferred target
  once the grill is up to temp:
  ```python
  if (session.mode is not CookMode.COACH
          and not session.pit_target_applied
          and snapshot.grill_temp >= LAUNCH_READY_TEMP_F):
      await self._set_pit_target(session.pit_target_f, reason="preheat (launch complete)")
      session.pit_target_applied = True
  ```
- On session restore (if you add resume), set `pit_target_applied = state is not PREHEATING`.

**Also (defence in depth):** the `number.grill_setpoint` / `climate.set_temperature`
paths let the user write a setpoint anytime. Consider rejecting a manual setpoint write
while `coordinator.data.grill_temp < LAUNCH_READY_TEMP_F` (raise `HomeAssistantError`
with a "wait until 150°F" message), and the `static/gmg-smoker-strategy.js` card can
disable/grey the grill-setpoint row until then. The Android app greys the grill-heat
control until ≥150°F.

**Why:** prevents the documented controller confusion; the autonomous adjust loop is
unaffected (it only runs in `COOKING`, well above 150°F).

---

## 3. MED — Budget the ~10 min startup into finish-time planning

`cook_manager.pre_flight` currently does:
```python
cook_hrs = finish_in_hours - (meat.rest_min / 60) - 0.5
```
The `0.5` is a vague "preheat + slack" budget. Real light-up-to-temp is ~**10 min**.
Make it explicit so a finish-time plan lands on time:
```python
PREHEAT_MINUTES = 10  # near the constants block
...
cook_hrs = finish_in_hours - (meat.rest_min / 60) - (PREHEAT_MINUTES / 60)
```
Surface it to the user (card / a sensor / the start notification): the total window is
`startup (~10m) + cook + rest`. The Android wizard shows
*"Cook time ~Xh. Plus ~10 min startup + Ym rest before serving."*

---

## 4. MED — "By-the-piece" meats: fixed pit temp, no weight question

**Two coupled problems for thin items (sausage/brats, chicken breast):**
1. Their `lfn` (half-thickness) is **constant** — weight does not change the time — so
   asking for weight is meaningless. The card already hides weight for `sausage_brats`
   (`buildControls`, `state_not: "sausage_brats"`) but not `chicken_breast`, and the
   *number entity* still exists.
2. `find_exact_temp` for a short finish picks a **too-hot pit** (a 1-hour sausage
   solved to ~300°F on hardware; it should be ~250°F).

**Fix:**
- `cook_physics.py` — add two fields to the `Meat` dataclass:
  ```python
  by_the_piece: bool = False   # constant thickness → don't ask weight
  fixed_pit_f: int | None = None  # cook at this temp instead of solving from finish time
  ```
  Set them for the thin items in `CP_MEATS`:
  ```python
  "sausage_brats": Meat(..., 3.0, by_the_piece=True, fixed_pit_f=250),
  "chicken_breast": Meat(..., 4.0, by_the_piece=True, fixed_pit_f=275),
  ```
  (Sausage at fixed 250°F computes to ~0.65 h cook + 10 min startup + 5 min rest ≈ **1 h
  total** — matches the real-world recommendation.)
- `cook_manager.pre_flight` — use the fixed pit when present, else the existing solve:
  ```python
  if meat.fixed_pit_f is not None:
      pit_target = max(PIT_CLAMP_MIN_F, min(self._max_pit_f, meat.fixed_pit_f))
  else:
      if finish_in_hours <= 0.5: raise CookManagerError("finish time too soon (>0.5h required)")
      cook_hrs = finish_in_hours - (meat.rest_min / 60) - (PREHEAT_MINUTES / 60)
      if cook_hrs <= 0.25: raise CookManagerError("not enough cook time after startup + rest budget")
      pit_target = max(PIT_CLAMP_MIN_F, min(self._max_pit_f, round(find_exact_temp(meat_key, weight_lbs, cook_hrs))))
  projection = compute_at(meat_key, weight_lbs, pit_target)
  ```
- UI: in `static/gmg-smoker-strategy.js` `buildControls`, hide the cook-weight row for
  **both** `sausage_brats` and `chicken_breast` (and, ideally, for any `by_the_piece`
  meat — but the card has no access to that flag, so hard-code the list to match
  `CP_MEATS`). The finish-in-hours input is also moot for fixed-pit meats.

**Note:** the `meats` SQLite table doesn't need the new columns — `pre_flight` reads
`CP_MEATS` in code. Add columns only if you want the DB to mirror it.

---

## 5. HIGH — "Time remaining" should be a live forecast, not a static countdown

**Bug:** `sensor.py` `_cook_remaining_minutes` returns `total_min - elapsed_min` — a
**static countdown** off the original projection. It never re-forecasts from how the
food is actually progressing, so it just ticks the initial estimate down regardless of
ahead/behind.

**Fix:**
- `cook_physics.py` — add the inverse of `expected_probe_at`:
  ```python
  def elapsed_at_probe(projection: CookProjection, probe_f: float) -> float:
      """Elapsed hours at which the projection expects the probe to read probe_f.
      Inverse of expected_probe_at; used for a live time-remaining that tracks the
      food's actual position on the curve."""
      first = projection.phases[0]
      if probe_f <= first.start_internal_f:
          return 0.0
      cum = 0.0
      for ph in projection.phases:
          if probe_f <= ph.end_internal_f:
              if ph.hours <= 0:
                  return cum  # flat phase (e.g. stall) — hold the estimate
              frac = (probe_f - ph.start_internal_f) / (ph.end_internal_f - ph.start_internal_f)
              return cum + max(0.0, min(1.0, frac)) * ph.hours
          cum += ph.hours
      return projection.total_hours
  ```
- `sensor.py` `_cook_remaining_minutes` — anchor to the probe, fall back to the
  countdown when there's no probe reading:
  ```python
  def _cook_remaining_minutes(c):
      s = c.cook_manager.session
      if s is None or s.cook_started_at is None:
          return None
      probe = c.data.probe_1_temp if s.probe_index == 1 else c.data.probe_2_temp
      if probe is not None:
          elapsed_h = elapsed_at_probe(s.projection, float(probe))
          return round(max(0.0, (s.projection.total_hours - elapsed_h) * 60), 1)
      total_min = s.projection.total_hours * 60
      return round(max(0.0, total_min - (time.time() - s.cook_started_at) / 60), 1)
  ```

**Behaviour:** behind → more time left; ahead → less; in the stall the estimate holds
steady (the flat phase can't be inverted to a single time) instead of ticking to zero.

**Test:** add to `tests/test_cook_physics.py` — on a single-phase meat (chicken, strictly
increasing) `elapsed_at_probe(start)==0`, `elapsed_at_probe(pull)≈total_hours`, and it
round-trips with `expected_probe_at` mid-cook.

---

## 6. LOW / FYI — Directional schedule text (app-side bug, not HA)

The Android UI had a bug where an off-schedule cook always said "running behind" even
when **ahead** (the badge ignored the sign of `expected − actual`). **In the HA
integration this is already correct:** the `binary_sensor cook_on_schedule` is a binary
`abs(delta) <= 10`, and coach mode (`_maybe_advise_pit`) is sign-aware. **No HA change
needed.** Just a caution for anyone adding directional "ahead/behind" text to the card:
drive it off the sign of `expected_probe_at(...) − actual_probe`, not the binary sensor.

---

## 7. Other confirmations (no change needed, recorded for the next maintainer)

- **Single-threaded controller**: one request in flight; the client's 1.0 s timeout / 5
  retries / per-request socket is correct — keep it.
- **Server Mode interlock**: confirmed — when Server Mode is on, the LAN socket goes
  silent; the repair-issue flow is right. There is **no LAN command to toggle Server
  Mode or join Wi-Fi**.
- **Wi-Fi provisioning**: not part of the control protocol. It *was* reverse-engineered
  and lives in community Go clients ([`FeatherKing/grillsrv`](https://github.com/FeatherKing/grillsrv))
  if a provisioning feature is ever wanted — out of scope for the HA integration.
- **Protocol RE provenance**: the wire format was originally derived via *"network
  packet sniffing + Android APK decompilation"* (FeatherKing), cross-referenced by
  `brandenc40/green-mountain-grill`, `Aenima4six2/gmg` (has a controller emulator),
  `toddq/grillsrv`. `hallyaus` wrote the integration from observed behaviour + vendor
  docs against those references.

---

## Patch checklist (for the patching agent)

| # | Priority | Files | Verify |
|---|----------|-------|--------|
| 1a | HIGH | `tests/test_protocol.py` | `pytest tests/test_protocol.py` runs (not skipped) + passes incl. golden frame |
| 1b | HIGH | `docs/PROTOCOL.md` | offsets table matches `api/protocol.py` |
| 2 | HIGH | `cook_manager.py` (+ optionally `number.py`/`climate.py`/card) | setpoint not sent below 150°F; deferred preheat applies once ≥150 |
| 3 | MED | `cook_manager.py` | `pre_flight` reserves 10 min startup |
| 4 | MED | `cook_physics.py`, `cook_manager.py`, `static/gmg-smoker-strategy.js` | sausage→250°F & chicken-breast→275°F, no weight asked |
| 5 | HIGH | `cook_physics.py`, `sensor.py`, `tests/test_cook_physics.py` | remaining re-forecasts from probe; new test passes |
| 6 | — | (none) | n/a — HA already correct |

Run the full suite after: `pytest` (the protocol file will now actually execute).
The Android repo's `cook/` and `protocol/` Kotlin ports are a working reference for
every one of these (functions: `elapsedAtProbe`, `preFlight` fixed-pit branch,
`LAUNCH_READY_TEMP_F` gate, `Meat.byThePiece/fixedPitF`, `ProtocolTest.makeFrame`).
