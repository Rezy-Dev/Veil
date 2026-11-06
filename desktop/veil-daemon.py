#!/usr/bin/env python3
"""
Veil remote lock/unlock daemon.

Lock-screen agnostic. It never talks to a specific locker's IPC. It:

  lock    -> runs whatever locker this session provides
  unlock  -> types the password into the focused surface, then Return,
             so the locker's own PAM stack validates it

Because wtype (Wayland) / xdotool (X11) inject at the keyboard-focus level,
this works with hyprlock, swaylock, gtklock, i3lock, xsecurelock, and any
Quickshell ext-session-lock implementation (Omarchy's omarchy.lock or a clone
such as living-lock) — anything that accepts a typed password.

Endpoints, HMAC-signed (see verify_request):

    GET  /status   -> {"ok":true,"locked":bool,"detected_by":"..."}
    POST /lock     -> locks the session
    POST /unlock   -> {"password":"..."} typed into the focused lock screen

Config: ~/.config/veil/remote.json

    {
      "bind": "100.x.y.z",       # required
      "port": 8765,              # required
      "secret": "<hex>",         # required
      "lock_command": "",        # optional, overrides auto-detection
      "locked_probe": "auto"     # auto | omarchy | logind | process
    }
"""

from __future__ import annotations

import fcntl
import hashlib
import hmac
import json
import os
import shlex
import shutil
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

CONFIG_DIR = os.path.join(
    os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")), "veil"
)
CONFIG_PATH = os.path.join(CONFIG_DIR, "remote.json")

MAX_SKEW_SECONDS = 45
MAX_UNLOCK_PER_MINUTE = 6

# Lockers that hold the session by themselves. Ext-session-lock clients are
# covered by the logind/Omarchy probes above; this list is the last resort.
LOCKER_PROCESSES = (
    "hyprlock",
    "swaylock",
    "swaylock-effects",
    "gtklock",
    "i3lock",
    "i3lock-color",
    "xsecurelock",
    "physlock",
)

CONFIG: dict = {}


# --------------------------------------------------------------------------- #
# process helpers
# --------------------------------------------------------------------------- #

_seen_nonces: dict[str, float] = {}
_nonce_lock = threading.Lock()
_unlock_times: list[float] = []
_unlock_lock = threading.Lock()


def log(message: str) -> None:
    print(f"[veil] {message}", file=sys.stderr, flush=True)


def run(argv: list[str], timeout: float = 10.0) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(
            argv, capture_output=True, text=True, timeout=timeout, check=False
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return subprocess.CompletedProcess(argv, 127, "", "")


def load_config() -> dict:
    with open(CONFIG_PATH, "r", encoding="utf-8") as fh:
        cfg = json.load(fh)
    for key in ("bind", "port", "secret"):
        if key not in cfg:
            raise SystemExit(f"remote.json missing '{key}'")
    return cfg


# --------------------------------------------------------------------------- #
# lock detection — try every probe we can, report which one answered
# --------------------------------------------------------------------------- #


def _probe_omarchy() -> bool | None:
    if not shutil.which("omarchy-shell"):
        return None
    result = run(["omarchy-shell", "lock", "isLocked"], timeout=4)
    value = result.stdout.strip().lower()
    if value in ("true", "false"):
        return value == "true"
    return None


def _probe_hyprland() -> bool | None:
    """Hyprland reports an active ext-session-lock on every monitor.

    This is locker-agnostic: hyprlock, swaylock and Quickshell's lock all show
    up as the LOCK entry in solitaryBlockedBy.
    """
    if not shutil.which("hyprctl"):
        return None
    result = run(["hyprctl", "-j", "monitors"], timeout=4)
    if result.returncode != 0:
        return None
    try:
        monitors = json.loads(result.stdout)
    except json.JSONDecodeError:
        return None
    if not isinstance(monitors, list) or not monitors:
        return None
    for monitor in monitors:
        if "LOCK" in (monitor.get("solitaryBlockedBy") or []):
            return True
    return False


def _probe_logind() -> bool | None:
    session_id = os.environ.get("XDG_SESSION_ID")
    if not session_id or not shutil.which("loginctl"):
        return None
    result = run(
        ["loginctl", "show-session", session_id, "--property=LockedHint", "--value"],
        timeout=4,
    )
    value = result.stdout.strip().lower()
    if value in ("yes", "no"):
        return value == "yes"
    return None


def _probe_process() -> bool | None:
    for name in LOCKER_PROCESSES:
        result = run(["pgrep", "-x", name], timeout=3)
        if result.returncode == 0 and result.stdout.strip():
            return True
    return None


def session_locked() -> tuple[bool | None, str]:
    """Return (locked?, probe-name). None means 'cannot tell'."""
    probe = (CONFIG.get("locked_probe") or "auto").lower()

    order = {
        "omarchy": [("omarchy", _probe_omarchy)],
        "hyprland": [("hyprland", _probe_hyprland)],
        "logind": [("logind", _probe_logind)],
        "process": [("process", _probe_process)],
    }.get(probe)

    if order is None:  # auto
        # hyprland first: an active ext-session-lock is authoritative there and
        # covers every locker, including Omarchy's. omarchy-shell is known to
        # answer "false" while its lock is up, so it must not lead.
        order = [
            ("hyprland", _probe_hyprland),
            ("omarchy", _probe_omarchy),
            ("logind", _probe_logind),
            ("process", _probe_process),
        ]

    for name, fn in order:
        answer = fn()
        if answer is not None:
            return answer, name
    return None, "unknown"


# --------------------------------------------------------------------------- #
# lock / unlock
# --------------------------------------------------------------------------- #


def lock_candidates() -> list[list[str]]:
    configured = CONFIG.get("lock_command") or ""
    if configured.strip():
        return [shlex.split(configured)]

    candidates: list[list[str]] = []
    if shutil.which("omarchy-shell"):
        candidates.append(["omarchy-shell", "lock", "lock"])
    if shutil.which("omarchy-launch-screensaver"):
        candidates.append(["omarchy-launch-screensaver"])
    for name in ("hyprlock", "swaylock", "gtklock", "i3lock"):
        if shutil.which(name):
            candidates.append([name])
    if shutil.which("loginctl"):
        candidates.append(["loginctl", "lock-session"])
    return candidates


def do_lock() -> tuple[bool, str]:
    candidates = lock_candidates()
    if not candidates:
        return False, "no lock command found; set lock_command in remote.json"

    last_error = "no lock command found"
    for argv in candidates:
        result = run(argv, timeout=10.0)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            last_error = f"{argv[0]}: {output or result.returncode}"
            continue

        # Confirm it actually took effect rather than trusting the exit code.
        for _ in range(12):
            time.sleep(0.25)
            state, _ = session_locked()
            if state is True:
                return True, f"locked via {argv[0]}"
            if state is None:
                return True, f"{argv[0]} ran (state unknown)"

        last_error = f"{argv[0]} ran but session did not lock {output}".strip()

    return False, last_error


def type_text(text: str) -> bool:
    session_type = os.environ.get("XDG_SESSION_TYPE", "").lower()

    use_x11 = session_type == "x11" or (
        shutil.which("wtype") is None and shutil.which("xdotool") is not None
    )

    if use_x11:
        if shutil.which("xdotool") is None:
            return False
        typed = run(
            ["xdotool", "type", "--clearmodifiers", "--delay", "30", "--", text],
            timeout=25.0,
        )
        if typed.returncode != 0:
            return False
        run(["xdotool", "key", "--clearmodifiers", "Return"], timeout=5.0)
        return True

    if shutil.which("wtype") is None:
        return False
    typed = run(["wtype", "-s", "30", "--", text], timeout=25.0)
    if typed.returncode != 0:
        return False
    run(["wtype", "-k", "Return"], timeout=5.0)
    return True


# Processes that block waiting for a typed password.
PASSWORD_PROMPTERS = ("sudo", "doas", "pkexec")


def pending_password_prompt() -> bool:
    """True when something is actually waiting for a password."""
    for name in PASSWORD_PROMPTERS:
        result = run(["pgrep", "-x", name], timeout=3)
        if result.returncode == 0 and result.stdout.strip():
            return True
    return False


def focused_window() -> dict:
    if not shutil.which("hyprctl"):
        return {}
    result = run(["hyprctl", "activewindow", "-j"], timeout=3)
    if result.returncode != 0:
        return {}
    try:
        data = json.loads(result.stdout)
    except json.JSONDecodeError:
        return {}
    return {"class": data.get("class", ""), "title": data.get("title", "")}


def do_unlock(password: str) -> tuple[bool, str]:
    locked, probe = session_locked()

    if locked is True:
        if not type_text(password):
            return False, "typing-failed"

        # The locker dismisses itself once PAM accepts.
        deadline = time.monotonic() + 12.0
        while time.monotonic() < deadline:
            time.sleep(0.25)
            state, _ = session_locked()
            if state is False:
                return True, f"unlocked ({probe})"
        return False, f"still-locked ({probe})"

    # Session is unlocked. The useful case is a password prompt: sudo, doas or
    # pkexec waiting on the focused terminal. We refuse otherwise so a stray tap
    # can never type the password into an editor or a chat window.
    if pending_password_prompt():
        if not type_text(password):
            return False, "typing-failed"
        return True, "typed (password prompt)"

    if locked is None:
        return False, "cannot-tell"

    return False, "nothing-is-asking"


def snapshot() -> dict:
    """Current state, cheap enough to poll once a second."""
    locked, probe = session_locked()
    return {
        "locked": bool(locked),
        "known": locked is not None,
        "detected_by": probe,
        "prompting": pending_password_prompt(),
        "focus": focused_window(),
    }


def signature(snap: dict) -> str:
    return f"{int(snap['locked'])}{int(snap['prompting'])}"


# --------------------------------------------------------------------------- #
# request signing
# --------------------------------------------------------------------------- #


def _prune_nonces(now: float) -> None:
    stale = [n for n, ts in _seen_nonces.items() if now - ts > MAX_SKEW_SECONDS]
    for nonce in stale:
        _seen_nonces.pop(nonce, None)


def verify_request(secret: str, handler: "Handler", body: bytes) -> str | None:
    """Return an error string, or None when the request is authentic."""
    ts_raw = handler.headers.get("X-Veil-Ts", "")
    nonce = handler.headers.get("X-Veil-Nonce", "")
    sig = handler.headers.get("X-Veil-Sig", "")

    if not ts_raw or not nonce or not sig:
        return "missing-signature"

    try:
        ts = int(ts_raw)
    except ValueError:
        return "bad-timestamp"

    now = time.time()
    if abs(now - ts) > MAX_SKEW_SECONDS:
        return "stale-timestamp"

    signed = "\n".join(
        [ts_raw, nonce, handler.command, handler.path, body.decode("utf-8", "replace")]
    )
    expected = hmac.new(
        secret.encode("utf-8"), signed.encode("utf-8"), hashlib.sha256
    ).hexdigest()

    if not hmac.compare_digest(expected, sig):
        return "bad-signature"

    with _nonce_lock:
        _prune_nonces(now)
        if nonce in _seen_nonces:
            return "replayed-nonce"
        _seen_nonces[nonce] = now

    return None


def unlock_rate_limited() -> bool:
    now = time.monotonic()
    with _unlock_lock:
        _unlock_times[:] = [t for t in _unlock_times if now - t < 60.0]
        if len(_unlock_times) >= MAX_UNLOCK_PER_MINUTE:
            return True
        _unlock_times.append(now)
    return False


# --------------------------------------------------------------------------- #
# http
# --------------------------------------------------------------------------- #


class Handler(BaseHTTPRequestHandler):
    server_version = "Veil/1.1"
    protocol_version = "HTTP/1.1"

    # keep the noise out of the journal; we log our own lines
    def log_message(self, fmt: str, *args) -> None:  # noqa: A003
        return

    def _reply(self, code: int, payload: dict) -> None:
        data = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return b""
        if length > 8192:
            raise ValueError("body-too-large")
        return self.rfile.read(length)

    def _handle(self, method: str) -> None:
        try:
            body = self._read_body()
        except ValueError as exc:
            self._reply(413, {"ok": False, "error": str(exc)})
            return

        error = verify_request(CONFIG["secret"], self, body)
        if error:
            log(f"rejected {method} {self.path}: {error}")
            self._reply(401, {"ok": False, "error": error})
            return

        if self.path.startswith("/watch") and method == "GET":
            # Long poll: hold the request open until the state changes, so the
            # phone can raise a notification the moment a prompt appears
            # without hammering the daemon.
            query = parse_qs(urlparse(self.path).query)
            since = (query.get("since") or [""])[0]

            deadline = time.monotonic() + 25.0
            while True:
                snap = snapshot()
                sig = signature(snap)
                if sig != since or time.monotonic() >= deadline:
                    self._reply(200, {"ok": True, "sig": sig, **snap})
                    return
                time.sleep(1.0)

        if self.path == "/status" and method == "GET":
            locked, probe = session_locked()
            self._reply(
                200,
                {
                    "ok": True,
                    "locked": locked if locked is not None else False,
                    "known": locked is not None,
                    "detected_by": probe,
                    "prompting": pending_password_prompt(),
                    "focus": focused_window(),
                },
            )
            return

        if self.path == "/lock" and method == "POST":
            ok, detail = do_lock()
            log(f"lock -> {detail}")
            self._reply(200 if ok else 500, {"ok": ok, "detail": detail})
            return

        if self.path == "/unlock" and method == "POST":
            if unlock_rate_limited():
                log("unlock rejected: rate limit")
                self._reply(429, {"ok": False, "error": "rate-limited"})
                return
            try:
                payload = json.loads(body or b"{}")
                password = payload.get("password") or ""
            except json.JSONDecodeError:
                self._reply(400, {"ok": False, "error": "bad-json"})
                return
            if not password:
                self._reply(400, {"ok": False, "error": "no-password"})
                return

            started = time.monotonic()
            ok, detail = do_unlock(password)
            log(f"unlock -> {detail} ({time.monotonic() - started:.1f}s)")
            self._reply(200 if ok else 403, {"ok": ok, "detail": detail})
            return

        self._reply(404, {"ok": False, "error": "not-found"})

    def do_GET(self) -> None:  # noqa: N802
        self._handle("GET")

    def do_POST(self) -> None:  # noqa: N802
        self._handle("POST")


def main() -> None:
    global CONFIG

    os.makedirs(CONFIG_DIR, exist_ok=True)

    # single instance
    lock_file = open(os.path.join(CONFIG_DIR, "remote.lock"), "w", encoding="utf-8")
    try:
        fcntl.flock(lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise SystemExit("veil daemon already running")

    CONFIG = load_config()

    bind = CONFIG["bind"]
    port = int(CONFIG["port"])
    server = ThreadingHTTPServer((bind, port), Handler)
    server.daemon_threads = True

    log(f"listening on http://{bind}:{port}")
    locked, probe = session_locked()
    log(f"lock detection: {probe} -> locked={locked}")
    candidates = lock_candidates()
    log(f"lock candidates: {candidates or 'none'}")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
