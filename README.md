# Veil

Lock and unlock your desktop from your phone, gated by your fingerprint.

## About

Two pieces: an Android app and a small daemon on the desktop. The phone signs a
request, the daemon types your password into whatever holds keyboard focus, and
the locker's own PAM stack validates it. Nothing bypasses authentication.

Because it types instead of calling a locker's API, it works with any locker
that accepts a password — Omarchy's Quickshell lock, `hyprlock`, `swaylock`,
`gtklock`, `i3lock`. The same path answers `sudo`, `doas` and `pkexec` prompts.

Transport is whatever you have. Tailscale, a Cloudflare Tunnel, a reverse
proxy, or a plain LAN address.

## Setup

### Desktop

```bash
cd desktop
./setup.sh
```

Prints the address and a shared secret, then runs `veil-remote.service`. The
daemon binds `127.0.0.1` by default, which is what a proxy wants. If a tailnet
address exists it is also bound, so the phone can reach it directly.

Config lives in `~/.config/veil/remote.json`:

```json
{
  "bind": "127.0.0.1",
  "bind_extra": "100.x.y.z",
  "port": 8765,
  "secret": "<hex>"
}
```

### Reaching it from the phone

Any of these works — pick one:

| Route | Address to enter | TLS |
|---|---|---|
| Tailscale Serve | `tailscale serve --bg 8765` → `<machine>.<tailnet>.ts.net` | on, port 443 |
| Cloudflare Tunnel | `cloudflared tunnel --url http://127.0.0.1:8765` → `<name>.trycloudflare.com` | on, port 443 |
| Reverse proxy | your nginx/caddy hostname | on, port 443 |
| Direct | the `bind_extra` address, e.g. `100.x.y.z` | off, port 8765 |

If something terminates TLS for you, leave `bind` on `127.0.0.1` so the daemon
is never exposed directly.

A public tunnel is reachable by anyone who finds it, so keep the secret long
and TLS on.

### Phone

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Veil — it starts on Setup. Enter host, port, shared secret and your
desktop password. SAVE asks for your fingerprint.

Tailscale note: MagicDNS must be enabled in the Android app, otherwise the
`*.ts.net` name will not resolve.

## Features

- Fingerprint-gated unlock, with the password encrypted under a biometric-bound
  Keystore key
- Works over Tailscale, Cloudflare Tunnel, a reverse proxy, or plain LAN
- HMAC-SHA256 signed requests, replay-protected and rate limited
- Works with any locker that accepts a typed password
- Answers `sudo` / `doas` / `pkexec` prompts from the phone
- Notification when the desktop locks or asks for a password
- Nothing-inspired UI: pure black, dot grid, dot-matrix type

## License

MIT — see [LICENSE](LICENSE).
