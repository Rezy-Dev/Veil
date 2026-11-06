#!/usr/bin/env bash
# Installs the Veil remote daemon as a systemd --user service.
#
# Safe to re-run: keeps an existing secret, only creates one when missing.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CFG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/veil"
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
CFG="$CFG_DIR/remote.json"

mkdir -p "$CFG_DIR" "$UNIT_DIR"
chmod 700 "$CFG_DIR"

BIND="$(tailscale ip -4 2>/dev/null | head -1 || true)"
if [[ -z "$BIND" ]]; then
  BIND="$(ip -4 addr show scope global | grep -oE 'inet [0-9.]+' | head -1 | cut -d' ' -f2)"
fi
if [[ -z "$BIND" ]]; then
  echo "could not determine a bind address" >&2
  exit 1
fi

if [[ -f "$CFG" ]]; then
  SECRET="$(sed -n 's/.*"secret"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$CFG")"
  PORT="$(sed -n 's/.*"port"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$CFG")"
  echo "keeping existing config"
else
  SECRET="$(openssl rand -hex 32)"
  PORT="8765"
fi
PORT="${PORT:-8765}"

cat > "$CFG" <<EOF
{
  "bind": "$BIND",
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
PassEnvironment=WAYLAND_DISPLAY XDG_RUNTIME_DIR HYPRLAND_INSTANCE_SIGNATURE

[Install]
WantedBy=graphical-session.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now veil-remote.service

sleep 1
systemctl --user --no-pager --lines=0 status veil-remote.service || true

cat <<EOF

  Bind      $BIND:$PORT
  Secret    $SECRET

  Paste both into the Veil app (Setup).

EOF
