# Veil

Remote lock/unlock for your Omarchy desktop, unlocked with your phone's fingerprint.

## About

Two pieces: an Android app and a small daemon on the desktop, talking over
Tailscale.

Locking runs the session locker. Unlocking requires a fingerprint first: the
phone decrypts your desktop password with a Keystore key that only a live
biometric can use, then the daemon types it into whatever holds keyboard focus.
PAM validates it, so nothing bypasses authentication.

Because it types instead of calling a locker's API, it works with any locker
that accepts a password — Omarchy's Quickshell lock, `hyprlock`, `swaylock`,
`gtklock`, `i3lock`. The same path answers `sudo`, `doas` and `pkexec` prompts.

## Setup

### Desktop

```bash
cd desktop
./setup.sh
```

Prints the bind address and a shared secret, then runs `veil-remote.service`.

Config lives in `~/.config/veil/remote.json`:

```json
{ "bind": "100.x.y.z", "port": 8765, "secret": "<hex>" }
```

### Phone

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Veil. It starts on Setup: enter the host, port, shared secret and your
desktop password. SAVE asks for your fingerprint.

### TLS (optional)

Tailscale Serve needs HTTPS certificates enabled for your tailnet
(admin console → DNS → HTTPS Certificates). Then:

```bash
tailscale serve --bg 8765
```

Set `bind` to `127.0.0.1` in the daemon config, and point the app at
`<machine>.<tailnet>.ts.net`, port `443`, TLS on.

Without it, traffic is plain HTTP inside the WireGuard tunnel — still encrypted
on the wire.

## Features

- Fingerprint-gated unlock, with the password encrypted under a biometric-bound
  Keystore key
- Lock and unlock over Tailscale, no port forwarding
- HMAC-SHA256 signed requests, replay-protected and rate limited
- Works with any locker that accepts a typed password
- Answers `sudo` / `doas` / `pkexec` prompts from the phone
- Notification when the desktop locks or asks for a password
- Nothing-inspired UI: pure black, dot grid, dot-matrix type

## License

MIT — see [LICENSE](LICENSE).
