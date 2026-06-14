# GMG Control

**A standalone Android app to monitor and control a Green Mountain Grills (GMG) pellet smoker directly over your local Wi-Fi — no Home Assistant, no GMG cloud, no internet.**

GMG Control talks to the grill's controller over plain UDP on your LAN. Once your
smoker is on Wi-Fi (and Server Mode is off), the official GMG app is never needed
again. The app is built for someone who has **never smoked meat before**: plain
language, big visuals, and a guided "tell me when you want to eat and I'll handle the
rest" Auto-Cook planner.

> Not affiliated with or endorsed by Green Mountain Grills. Use at your own risk, and
> never leave a live fire unattended.

---

## Features

- **Live monitoring** — grill temp, food-probe temps, hopper level, fire/flame state,
  and warnings (low pellets, fan/auger/ignitor faults), refreshed on a background
  service so it keeps working when the app is closed.
- **Manual control** — power on/off, set the grill temperature, set probe targets, and
  cold-smoke mode. Setpoints are clamped to safe hardware limits.
- **Auto-Cook planner** — pick a meat, its weight, and *when you want to eat*; a
  heat-diffusion physics model computes the grill temperature and a phase-by-phase
  timeline (Smoke → Stall → Render), then runs the cook in one of three modes:
  - **Auto-pilot** — the app nudges the grill to hit your finish time (autonomous).
  - **Set & forget** — set the heat once and watch progress.
  - **Coach me** — the app tells you what to adjust; you turn the dial.
- **Cook resume** — if you walk out of Wi-Fi range or the app gets killed, the in-flight
  cook is restored on reconnect and keeps tracking on the correct timeline.
- **Cook history + graphs** — every cook is logged; review past cooks with a
  temperature-over-time chart.
- **Grill discovery** — finds grills on your network via UDP broadcast; supports
  multiple grills and manual IP entry.
- **Smart notifications** — choose how chatty alerts are (Everything / Milestones / Only
  when done) to cut noise and save battery.
- **First-time-friendly UI** — onboarding, a smoker illustration with live overlays and
  procedural smoke/flame animation, a big progress ring, plain-language "what's happening
  now" coaching, and a guided New-Cook wizard. All artwork and animation is drawn in-app
  (no asset downloads), so the app is fully offline.

---

## How it works

The grill controller exposes a **proprietary UDP protocol on port 8080** — ASCII
commands terminated with `!`, and a binary status frame in reply. There is no auth, no
TLS, and no cloud involved when the grill is in local Wi-Fi mode.

The protocol, cook-time physics, and auto-cook state machine are a faithful port of the
[Green Mountain Grills Home Assistant integration](https://github.com/HallyAus/Green-Mountain-Grills),
re-implemented in Kotlin and covered by unit tests (including a parse test against a real
status frame captured from hardware). All the Home-Assistant-specific machinery
(coordinator, entities, options flow, persistent notifications) is replaced with native
Android equivalents.

### Protocol reference

| Purpose            | Command (ASCII) | Reply                          |
|--------------------|-----------------|--------------------------------|
| Status poll        | `UR001!`        | 36-byte little-endian frame (`UR…`) |
| Serial / discovery | `UL!`           | ASCII starting `GMG…`          |
| Firmware           | `UN!`           | ASCII                          |
| Power on           | `UK001!`        | status frame                   |
| Cold smoke         | `UK002!`        | status frame                   |
| Power off          | `UK004!`        | status frame                   |
| Grill setpoint °F  | `UT###!` (150–550) | status frame                |
| Probe 1 target °F  | `UF###!` (32–257)  | status frame                |
| Probe 2 target °F  | `Uf###!` (32–257)  | status frame                |

Discovery broadcasts `UL!` to `255.255.255.255:8080` and treats every reply starting
with `GMG` as a grill (de-duplicated by source IP).

Status-frame fields (little-endian) include: grill temp, probe 1/2 temps (a value of
`89` means "probe unplugged"), grill setpoint, probe targets, power state, fire state,
hopper %, warning code, and a model-id byte used to pick the smoker image.

### Architecture

```
com.gibilator.gmg
├── protocol/   Frame parser + command encoders, enums, exceptions   (pure, JVM-tested)
├── cook/       Heat-diffusion physics + auto-cook state machine      (pure, JVM-tested)
├── units/      °F/°C, kg/lb conversion + formatting                  (pure, JVM-tested)
├── net/        GmgClient (UDP + coroutine Mutex + retry), Discovery
├── data/       SQLite store, DataStore prefs, GrillRepository (the spine)
├── service/    Foreground poll/control service + local notifications
├── vm/         GrillViewModel (StateFlow UI state + actions)
└── ui/         Jetpack Compose: onboarding, smoker hero, wizard, history, grills, settings
```

The pure `protocol`, `cook`, and `units` packages have no Android dependencies and are
exercised by fast JVM unit tests under `app/src/test/`.

### Safety guardrails (enforced in code)

- Grill setpoint clamped to a safe window; the auto-cook ceiling never exceeds **375 °F**.
- The app **never automatically powers the grill off**.
- Automatic power-**on** happens only at the planned → preheating transition of an
  Auto-Cook, never spontaneously.

---

## Requirements

- A Wi-Fi-capable Green Mountain Grills controller (Davy Crockett, Daniel Boone, Jim
  Bowie, Trek, Ledge, Peak, and the Prime/Prime+ variants).
- The phone and grill on the **same Wi-Fi network**.
- Android **8.0 (API 26)** or newer.

---

## First-time setup

The local protocol cannot join the grill to Wi-Fi or toggle Server Mode — there are no
commands for that. So the **one-time** Wi-Fi setup is done in the official GMG app:

1. Open the official GMG app and connect your smoker to your home Wi-Fi (same network as
   your phone).
2. In the GMG app's Wi-Fi settings, turn **Server Mode OFF** (Server Mode routes
   everything through GMG's cloud and blocks local control).
3. Open GMG Control and tap **Find my grill**.

After that, GMG Control talks to the grill directly on your Wi-Fi — no cloud, no GMG app.
The app surfaces this guidance in onboarding and detects Server Mode at runtime.

---

## Build & run

Requires **Android Studio** (with its bundled JDK) or a JDK 17 + the Android SDK.

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests (no device needed)
./gradlew :app:testDebugUnitTest
```

The debug APK lands in `app/build/outputs/apk/debug/`. Or open the project in Android
Studio and press **Run**.

**Building from a network share?** Gradle's incremental build is unreliable on SMB/NFS.
Redirect build output to a local disk:

```bash
./gradlew -Pgmg.localBuildDir=/path/to/local/builds :app:assembleDebug
```

---

## Roadmap / ideas

- In-app Wi-Fi pairing (would require reverse-engineering the official app's provisioning
  exchange — not currently part of the local protocol).
- Manual-cook history (today history is tied to Auto-Cook sessions).
- Real meat photos (the app ships hand-drawn vector art behind stable keys).

---

## Credits

Protocol, cook-physics, and auto-cook logic are ported from the original
[Green Mountain Grills Home Assistant integration](https://github.com/HallyAus/Green-Mountain-Grills)
by **[hallyaus](https://github.com/HallyAus)**, which is MIT-licensed. Smoker model
artwork originates from that project. Huge thanks to hallyaus for the reverse-engineering
and the clean, well-documented implementation that made this port possible.

---

## License

[MIT](LICENSE). This project is a derivative of hallyaus's MIT-licensed
[Green Mountain Grills Home Assistant integration](https://github.com/HallyAus/Green-Mountain-Grills);
the original copyright notice is retained in [`LICENSE`](LICENSE) as the MIT terms require.

---

## Disclaimer

This is an independent project and is not affiliated with, authorized, or supported by
Green Mountain Grills. Cooking with fire carries inherent risk. Always follow your
grill's safety instructions and never leave it unattended while lit.
