#!/usr/bin/env bash
# Installs the Veil daemon as a systemd --user service.
#
# Binds to loopback by default, which is what a reverse proxy wants:
#   - Tailscale Serve:  tailscale serve --bg 8765
#   - Cloudflare Tunnel: cloudflared tunnel --url http://127.0.0.1:8765
#   - nginx / caddy on the same host
#
# If a tailnet address exists it is added as `bind_extra` so the phone can also
# reach it directly, without a proxy.
#
# Safe to re-run: keeps an existing secret.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CFG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/veil"
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
CFG="$CFG_DIR/remote.json"

mkdir -p "$CFG_DIR" "$UNIT_DIR"
chmod 700 "$CFG_DIR"

TAILNET_IP="$(tailscale ip -4 2>/dev/null | head -1 || true)"

if [[ -f "$CFG" ]]; then
  SECRET="$(sed -n 's/.*"secret"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$CFG")"
  PORT="$(sed -n 's/.*"port"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$CFG")"
  echo "keeping existing secret"
else
  SECRET="$(openssl rand -hex 32)"
  PORT="8765"
fi
PORT="${PORT:-8765}"

cat > "$CFG" <<EOF
{
  "bind": "127.0.0.1",
  "bind_extra": "$TAILNET_IP",
  "port": $PORT,
  "secret": "$SECRET"
}
EOF
chmod 600 "$CFG"

cat > "$UNIT_DIR/veil-remote.service" <<EOF
[Unit]
Description=Veil remote lock/unlock daemon
After=graphical-session.target
PartOf=graphical-session.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 $HERE/veil-daemon.py
Restart=always
RestartSec=2
PassEnvironment=WAYLAND_DISPLAY XDG_RUNTIME_DIR HYPRLAND_INSTANCE_SIGNATURE XDG_SESSION_TYPE XDG_SESSION_ID

[Install]
WantedBy=graphical-session.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now veil-remote.service

sleep 1
systemctl --user --no-pager --lines=0 status veil-remote.service || true

cat <<EOF

  Local       http://127.0.0.1:$PORT
  Direct      ${TAILNET_IP:-none}
  Secret      $SECRET

  Put a proxy in front for TLS, or point the app at the direct address.
  Paste the host, port and secret into Veil's Setup screen.

EOF
