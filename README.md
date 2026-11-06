# Veil

Lock and unlock your Omarchy (or any Wayland/X11) session from your phone.
Unlocking requires your fingerprint first.

Nothing-OS-inspired UI: pure black, dot grid, dot-matrix clock.

```
┌──────────────────────┐        Tailscale / WireGuard        ┌────────────────────┐
│  Android app         │ ──────────────────────────────────▶ │ veil-daemon        │
│  biometric → decrypt │      HMAC-signed HTTP request       │ (systemd --user)   │
└──────────────────────┘                                     └─────────┬──────────┘
                                                                       │
                                                        wtype / xdotool │ types the
                                                                       ▼ password
                                                              ┌──────────────────┐
                                                              │ focused locker   │
                                                              │ → PAM validates  │
                                                              └──────────────────┘
```

---

## It is not tied to a lock screen

Veil never talks to a locker's IPC. It types the password into whatever holds
keyboard focus and presses Return, so the locker's own PAM stack validates it
exactly as if you had typed it. That makes it locker-agnostic:

| Locker | Works | Mechanism |
|---|---|---|
| Omarchy Quickshell lock (`omarchy.lock`, `living-lock`) | ✅ | ext-session-lock + PAM |
| `hyprlock` | ✅ | wayland keyboard focus |
| `swaylock` / `swaylock-effects` | ✅ | wayland keyboard focus |
| `gtklock` | ✅ | wayland keyboard focus |
| `i3lock` / `xsecurelock` | ✅ | X11 via `xdotool` |
| fingerprint-only lock | ❌ | there is no password to type |

Nothing bypasses authentication. A wrong password simply leaves the session
locked.

### Password prompts (sudo)

The same keystroke path answers `sudo`, `doas` and `pkexec`. When one of those
is waiting, the app's status flips to **PASSWORD PROMPT**, shows the focused
window (`foot · hacks:rezydev`), and the main button becomes **SEND PASSWORD**.

It deliberately refuses to type unless something is actually asking:

```
locked            -> unlock the session
sudo/doas/pkexec  -> type the password into the focused window
otherwise         -> "nothing is asking for a password"
```

That guard is what stops a stray tap from typing your password into an editor,
a chat window, or a browser form.

### Locking

Auto-detected, first available wins:

1. `omarchy-shell lock lock` (Omarchy)
2. `omarchy-launch-screensaver`
3. `hyprlock` / `swaylock` / `gtklock` / `i3lock`
4. `loginctl lock-session`

Override with `lock_command` in `~/.config/veil/remote.json`.

### Detecting "is it locked?"

Tried in order, first definitive answer wins:

1. **`hyprctl -j monitors`** — an active ext-session-lock appears as `LOCK` in
   `solitaryBlockedBy` on every monitor. Authoritative on Hyprland and covers
   every locker.
2. **`omarchy-shell lock isLocked`** — Omarchy's own IPC.
3. **`loginctl ... LockedHint`** — generic systemd-logind hint.
4. **process scan** — `hyprlock`, `swaylock`, `gtklock`, `i3lock`, …

`omarchy-shell` is *not* trusted first: it can report `false` while its own lock
is up. Override with `locked_probe` (`hyprland` | `omarchy` | `logind` | `process`).

---

## Security model

- **Biometric-bound password.** The desktop password is encrypted with an
  Android Keystore AES-GCM key marked `setUserAuthenticationRequired(true)` and
  `setInvalidatedByBiometricEnrollment(true)`. The key lives in the TEE; every
  encrypt/decrypt needs a fresh fingerprint. The stored ciphertext is useless on
  its own — not to a backup, not to `adb pull`.
- **No password on the desktop.** The daemon has no credential; it only types
  what the phone sends.
- **Transport.** Tailscale (WireGuard). The daemon binds to the tailnet address.
- **Requests are signed.** `HMAC-SHA256(secret, ts \n nonce \n method \n path \n body)`
  plus a 45 s timestamp window and a nonce cache, so forged or replayed requests
  are rejected.
- **Rate limited.** Six unlock attempts per minute.
- **Failures are silent.** A wrong password is just a failed unlock.

What this does *not* protect against: someone holding your unlocked phone while
forcing your finger onto the sensor. Treat the phone as a key.

---

## Install

### Desktop

```bash
cd desktop
./setup.sh          # generates the secret, installs + starts the user service
```

It prints the bind address and secret. Keep the secret private.

Config lives at `~/.config/veil/remote.json` (mode 600):

```json
{
  "bind": "100.x.y.z",
  "port": 8765,
  "secret": "<hex>"
}
```

Optional keys:

```json
{
  "lock_command": "hyprlock",
  "locked_probe": "hyprland"
}
```

### Phone

1. Build and install:

   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Open **Veil → SETUP**.
3. Enter the Tailscale host, port, shared secret, and your desktop password.
4. **SAVE** prompts for a fingerprint — that encrypts the password.

Then **LOCK** / **UNLOCK** from the main screen.

---

## Layout

```
app/src/main/java/dev/rezy/veil/
├── MainActivity.kt        remote screen + biometric + request flow
├── crypto/Vault.kt        Keystore AES-GCM, biometric-bound
├── data/Settings.kt       host / port / secret
├── net/VeilClient.kt      HMAC-signed HTTP over Tailscale
└── ui/                    RemoteScreen, SettingsScreen, AboutScreen, theme

desktop/
├── veil-daemon.py         lock/unlock daemon, locker-agnostic probes
└── setup.sh               secret + systemd user unit
```

## Type & licence

- **Handjet** — dot-matrix display face, SIL OFL 1.1 (`LICENSE-handjet.txt`).
- **Silkscreen** — pixel label face, SIL OFL 1.1.

## Build

JDK 17+, Android SDK 36, min SDK 29.
