#!/usr/bin/env python3
"""Safe ASCII transport for ONE read-only Railway pg_dump to LOCAL private storage.

Railway's SSH command is intended for terminal-style commands. Do not assume
raw binary stdout is byte-transparent on every client and operating system.
Encode PGDMP as base64 at the source and use random, framed delimiters; read
and decode the stream incrementally (never buffer the entire customer dump).
Do not upload a plaintext dump or print its bytes, credentials, or remote stderr.
"""
from __future__ import annotations

import base64
import binascii
from pathlib import Path
import secrets
import subprocess
import tempfile
import threading

from railway_pg18_snapshot_drill import (
    ENVIRONMENT, PROJECT, SERVICE, classify_ssh_error, validate_dump,
)

class ExportBlocked(RuntimeError):
    """Only constant-safe codes; raw stderr stays private."""


def dump_from_railway_ascii(railway: str, path: Path) -> dict:
    nonce = secrets.token_hex(16)
    begin = ("HELVOCA_C1_BEGIN_" + nonce).encode("ascii")
    end = ("HELVOCA_C1_END_" + nonce).encode("ascii")
    # bash pipefail propagates a failed pg_dump even if base64 exits 0.
    # Quoted variable names expand only ON the remote Railway database host.
    remote = (
        "set -o pipefail; "
        f"printf '%s\\n' '{begin.decode('ascii')}'; "
        'export PGPASSWORD="$POSTGRES_PASSWORD"; '
        'pg_dump --format=custom --no-owner --no-acl '
        '-U "$POSTGRES_USER" -d "$POSTGRES_DB" | base64; '
        'result=$?; '
        f"printf '\\n%s\\n' '{end.decode('ascii')}'; "
        'exit "$result"'
    )
    cmd = [railway, "ssh", "--project", PROJECT, "--environment", ENVIRONMENT,
           "--service", SERVICE, "--", "bash", "-c", remote]
    started = False
    complete = False
    archive_created = False
    deadline_reached = threading.Event()
    try:
        with path.open("xb") as archive, tempfile.TemporaryFile(mode="w+b") as errors:
            archive_created = True
            if __import__("os").name != "nt":
                path.chmod(0o600)
            with subprocess.Popen(cmd, stdin=subprocess.DEVNULL,
                                  stdout=subprocess.PIPE, stderr=errors) as proc:
                assert proc.stdout is not None

                def abort_on_timeout():
                    deadline_reached.set()
                    try:
                        proc.kill()
                    except OSError:
                        pass

                timer = threading.Timer(300, abort_on_timeout)
                timer.daemon = True
                timer.start()
                count = 0
                try:
                    while True:
                        raw = proc.stdout.readline(4097)
                        if not raw:
                            break
                        if len(raw) > 4096:
                            raise ExportBlocked("RAILWAY_ASCII_STREAM_LINE_TOO_LONG")
                        line = raw.strip(b"\r\n")
                        if not started:
                            # Ignore limited SSH banners before the authenticated
                            # random delimiter. Never interpret them as archive data.
                            count += len(raw)
                            if count > 16384:
                                raise ExportBlocked("RAILWAY_ASCII_BEGIN_MISSING")
                            if line == begin:
                                started = True
                            continue
                        if not complete:
                            if line == end:
                                complete = True
                                continue
                            if not line:
                                continue
                            if len(line) > 76:
                                raise ExportBlocked("RAILWAY_ASCII_INVALID_BASE64_LINE")
                            try:
                                data = base64.b64decode(line, validate=True)
                            except (ValueError, binascii.Error):
                                raise ExportBlocked("RAILWAY_ASCII_INVALID_BASE64") from None
                            archive.write(data)
                    code = proc.wait(timeout=20)
                finally:
                    timer.cancel()
                    if proc.poll() is None:
                        proc.kill()
                        proc.wait()
            if deadline_reached.is_set():
                raise ExportBlocked("RAILWAY_ASCII_TRANSFER_TIMEOUT")
            if code != 0:
                errors.seek(0)
                category = classify_ssh_error(errors.read(65536))
                raise ExportBlocked("RAILWAY_ASCII_REMOTE_EXIT_" + category)
            if not started or not complete:
                raise ExportBlocked("RAILWAY_ASCII_FRAME_INCOMPLETE")
            archive.flush()
            __import__("os").fsync(archive.fileno())
        try:
            return validate_dump(path)
        except (ValueError, OSError):
            raise ExportBlocked("RAILWAY_ASCII_PGDMP_VALIDATION_FAILED") from None
    except BaseException:
        # Never remove a backup that existed BEFORE exclusive creation.
        if archive_created:
            path.unlink(missing_ok=True)
        raise
