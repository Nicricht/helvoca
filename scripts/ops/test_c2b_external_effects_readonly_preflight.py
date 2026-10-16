"""C2-B external-effect flag auditor contract; all probes run locally/offline."""
import base64
import contextlib
import io
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import unittest
from unittest.mock import Mock, patch

import c2b_external_effects_readonly_preflight as c


def sample(changes=None):
    states = dict.fromkeys(c.FLAGS, "OFF")
    states.update(changes or {})
    lines = [c.START] + [c.PREFIX + flag + ":" + states[flag] for flag in c.FLAGS] + [c.END]
    return ("\n".join(lines) + "\n").encode()


def script():
    cmd = c.command("railway-cli")
    payload = cmd[-1].split("printf %s ", 1)[1].split(" | base64", 1)[0]
    return base64.b64decode(payload).decode()


class EffectFlagsTest(unittest.TestCase):
    def test_command_pins_only_prod_api_and_never_uses_secrets(self):
        argv = c.command("C:\\Tools\\railway.cmd")
        self.assertEqual(argv[:6], ["C:\\Tools\\railway.cmd", "ssh", "--project", c.PROJECT,
                                    "--environment", c.ENVIRONMENT])
        self.assertEqual(argv[6:9], ["--service", c.SERVICE, "--"])
        shell = script()
        self.assertIn("$" + "{RAILWAY_PROJECT_ID:-}", shell)
        self.assertIn("$" + "{RAILWAY_ENVIRONMENT_ID:-}", shell)
        self.assertIn("$" + "{RAILWAY_SERVICE_ID:-}", shell)
        self.assertIn('printenv "$name"', shell)
        for forbidden in ("PGPASSWORD", "DATABASE_URL", "pg_restore", "pg_dump",
                          "psql", "INSERT ", "DELETE ", "railway run",
                          "curl ", "wget "):
            self.assertNotIn(forbidden, shell)

    @unittest.skipUnless(shutil.which("sh"), "requires local POSIX shell")
    def test_remote_shell_redacts_values_and_fails_closed_on_identity(self):
        base = {"RAILWAY_PROJECT_ID": c.PROJECT, "RAILWAY_ENVIRONMENT_ID": c.ENVIRONMENT,
                "RAILWAY_SERVICE_ID": c.SERVICE}
        base.update({flag: "false" for flag in c.FLAGS})
        cmd = ["sh", "-c", script()]
        good = subprocess.run(cmd, env=base, capture_output=True, check=False)
        self.assertEqual(good.returncode, 0)
        self.assertTrue(all(state == "OFF" for _, state in c.parse(good.stdout)))
        base[c.FLAGS[0]] = "SENSITIVE_NEVER_PRINT"
        base.pop(c.FLAGS[1])
        base[c.FLAGS[2]] = "0"
        other = subprocess.run(cmd, env=base, capture_output=True, check=False)
        self.assertEqual(c.parse(other.stdout)[0][1], "NOT_OFF")
        self.assertEqual(c.parse(other.stdout)[1][1], "MISSING")
        self.assertEqual(c.parse(other.stdout)[2][1], "OFF")
        self.assertNotIn(b"SENSITIVE_NEVER_PRINT", other.stdout)
        base["RAILWAY_PROJECT_ID"] = "wrong"
        rejected = subprocess.run(cmd, env=base, capture_output=True, check=False)
        self.assertNotEqual(rejected.returncode, 0)
        self.assertEqual(rejected.stdout, b"")

    def test_parser_exact_all_off(self):
        parsed = c.parse(sample())
        self.assertEqual(len(parsed), len(c.FLAGS))
        self.assertEqual(parsed[0], (c.FLAGS[0], "OFF"))
        self.assertEqual(parsed[-1], (c.FLAGS[-1], "OFF"))

    def test_parser_recognizes_missing_not_off(self):
        out = c.parse(sample({c.FLAGS[0]: "MISSING", c.FLAGS[1]: "NOT_OFF"}))
        self.assertEqual(out[0][1], "MISSING")
        self.assertEqual(out[1][1], "NOT_OFF")

    def test_parser_rejects_big_and_non_utf8(self):
        with self.assertRaisesRegex(c.Blocked, "C2B_UNEXPECTED_OUTPUT_SIZE"):
            c.parse(b"A" * 8193)
        with self.assertRaisesRegex(c.Blocked, "C2B_INVALID_OUTPUT_ENCODING"):
            c.parse(b"\xff")

    def test_parser_rejects_wrong_framing(self):
        data = sample()
        for bad in (b"", data + b"UNTRUSTED\n", data.replace(c.START.encode(), b"WRONG"),
                    data.replace(c.END.encode(), b"WRONG")):
            with self.subTest(bad=bad[:12]), self.assertRaisesRegex(c.Blocked, "C2B_UNEXPECTED_PROBE_FRAMING"):
                c.parse(bad)

    def test_parser_rejects_unknown_duplicate_or_swapped_flags(self):
        data = sample()
        first = (c.PREFIX + c.FLAGS[0] + ":OFF").encode()
        for bad in (data.replace(first, b"C2B_EFFECT_FLAG_V1:SECRET:OFF", 1),
                    data.replace(first, (c.PREFIX + c.FLAGS[1] + ":OFF").encode(), 1),
                    data.replace(first, (c.PREFIX + c.FLAGS[0] + ":UNKNOWN").encode(), 1)):
            with self.subTest(bad=bad[:70]), self.assertRaisesRegex(c.Blocked, "C2B_UNEXPECTED_FLAG_RESPONSE"):
                c.parse(bad)

    def test_probe_success_has_no_stderr_capture(self):
        with patch.object(c.subprocess, "run", return_value=Mock(returncode=0, stdout=sample())) as proc:
            self.assertEqual(c.probe("railway"), c.parse(sample()))
        self.assertEqual(proc.call_args.kwargs["stderr"], subprocess.DEVNULL)
        self.assertEqual(proc.call_args.kwargs["timeout"], 120)
        self.assertFalse(proc.call_args.kwargs["check"])

    def test_probe_refuses_wrong_remote_identity(self):
        with patch.object(c.subprocess, "run", return_value=Mock(returncode=43, stdout=b"SENSITIVE")):
            with self.assertRaisesRegex(c.Blocked, "C2B_REMOTE_IDENTITY_OR_SHELL_FAILED"):
                c.probe("railway")

    def test_probe_redacts_oserror_and_timeout(self):
        for problem in (OSError("SECRET"), subprocess.TimeoutExpired("railway", 120)):
            with self.subTest(kind=type(problem).__name__), patch.object(c.subprocess, "run", side_effect=problem):
                with self.assertRaisesRegex(c.Blocked, "C2B_RAILWAY_SSH_UNAVAILABLE"):
                    c.probe("railway")

    def test_main_requires_explicit_flag(self):
        with patch.object(c, "probe") as probe, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(c.main([]), 2)
            probe.assert_not_called()

    def test_main_requires_local_railway_cli(self):
        with patch.object(c.shutil, "which", return_value=None), patch.object(c, "probe") as probe, contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(c.main(["--probe-read-only"]), 2)
            probe.assert_not_called()
        self.assertIn("C2B_RAILWAY_CLI_MISSING", out.getvalue())

    def test_main_all_off_is_not_commercial_go(self):
        with patch.object(c.shutil, "which", return_value="C:\\Railway\\railway.cmd"), patch.object(c, "probe", return_value=c.parse(sample())):
            with contextlib.redirect_stdout(io.StringIO()) as out:
                self.assertEqual(c.main(["--probe-read-only"]), 0)
        self.assertIn("C2B_NO_RELEASE_APPROVAL", out.getvalue())
        self.assertNotIn("C2B_STARTUP_FLAGS_NOT_OFF", out.getvalue())

    def test_main_unsafe_flags_are_named_only(self):
        observed = c.parse(sample({c.FLAGS[0]: "NOT_OFF", c.FLAGS[2]: "MISSING"}))
        with patch.object(c.shutil, "which", return_value="railway"), patch.object(c, "probe", return_value=observed):
            with contextlib.redirect_stdout(io.StringIO()) as out:
                self.assertEqual(c.main(["--probe-read-only"]), 2)
        self.assertIn(c.FLAGS[0], out.getvalue())
        self.assertIn(c.FLAGS[2], out.getvalue())
        self.assertNotIn("SENSITIVE", out.getvalue())

    def test_main_safe_blocker(self):
        with patch.object(c.shutil, "which", return_value="railway"), patch.object(c, "probe", side_effect=c.Blocked("SAFE_CODE")):
            with contextlib.redirect_stdout(io.StringIO()) as out:
                self.assertEqual(c.main(["--probe-read-only"]), 2)
        self.assertIn("C2B_BLOCKED: SAFE_CODE", out.getvalue())

    def test_main_guard_when_module_invoked_without_flag(self):
        with patch.object(sys, "argv", ["c2b_external_effects_readonly_preflight.py"]):
            with contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit) as exited:
                    runpy.run_path(str(Path(__file__).with_name("c2b_external_effects_readonly_preflight.py")), run_name="__main__")
        self.assertEqual(exited.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
