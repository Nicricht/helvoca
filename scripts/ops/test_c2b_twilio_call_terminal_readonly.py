"""Offline C2-B Twilio call terminal GET contract, zero external network."""
import io
import runpy
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import c2b_twilio_call_terminal_readonly as tool
ACCOUNT = "AC" + "a" * 32
CHILD = "AC" + "b" * 32
CALL = "CA" + "c" * 32
TOKEN = "never-publish-this-secret"
TENANT = "044a5d73-2df7-4e77-849f-8639d05ac30b"


def environment(**changes):
    return {"C2B_CALL_READBACK_APPROVED": "yes",
            "C2B_EXPECTED_ACCOUNT_SID": ACCOUNT, "C2B_EXPECTED_CALL_SID": CALL,
            "C2B_PILOT_TENANT_ID": TENANT,
            "TWILIO_ACCOUNT_SID": ACCOUNT, "TWILIO_AUTH_TOKEN": TOKEN,
            **changes}


class TwilioCallReadbackTest(unittest.TestCase):
    def test_provider_one_get_exact_resource_auth_not_url(self):
        url = tool.BASE + f"/2010-04-01/Accounts/{ACCOUNT}/Calls/{CALL}.json"
        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *a): pass
            def geturl(self): return url
            def read(self, n):
                self.read_limit = n
                return ('{"sid":"'+CALL+'","account_sid":"'+ACCOUNT+'","status":"completed"}').encode()
        class Opener:
            def open(self, request, timeout):
                self.request, self.timeout = request, timeout
                return Response()
        opener = Opener()
        with patch.object(tool, "build_opener", return_value=opener) as mocked:
            result = tool.get_call(ACCOUNT, CALL, ACCOUNT, TOKEN)
        self.assertEqual("completed", result["status"])
        self.assertEqual("GET", opener.request.get_method())
        self.assertEqual(url, opener.request.full_url)
        self.assertNotIn(TOKEN, opener.request.full_url)
        self.assertEqual(12, opener.timeout)
        self.assertEqual(1, len(mocked.call_args.args))

    def test_reject_other_call_path_without_network(self):
        for account, call in (("ACbad", CALL), (ACCOUNT, "CAinvalid"),
                              (ACCOUNT + "..", CALL), (ACCOUNT, CALL + "?")):
            with self.subTest(account=account, call=call):
                with patch.object(tool, "build_opener") as mock:
                    with self.assertRaisesRegex(tool.Blocked, "C2B_INVALID_CALL_IDENTITY"):
                        tool.get_call(account, call, ACCOUNT, TOKEN)
                    mock.assert_not_called()

    def test_all_terminal_and_nonterminal_statuses(self):
        for status in tool.TERMINAL | tool.NONTERMINAL:
            self.assertEqual(status, tool.classify(
                {"sid": CALL, "account_sid": ACCOUNT, "status": status}, ACCOUNT, CALL))
        self.assertEqual(5, len(tool.TERMINAL))
        self.assertEqual(3, len(tool.NONTERMINAL))

    def test_invalid_or_wrong_call_response_never_terminal(self):
        for data, code in (({"sid": CALL, "account_sid": CHILD, "status":"completed"},
                            "C2B_PROVIDER_IDENTITY_MISMATCH"),
                           ({"sid": "CA" + "d" * 32, "account_sid": ACCOUNT, "status":"completed"},
                            "C2B_PROVIDER_IDENTITY_MISMATCH"),
                           ({"sid": CALL, "account_sid": ACCOUNT, "status":"bogus"},
                            "C2B_PROVIDER_STATUS_UNKNOWN")):
            with self.assertRaisesRegex(tool.Blocked, code):
                tool.classify(data, ACCOUNT, CALL)

    def test_redirect_denied_without_leaking_provider_details(self):
        class OpenRedirect:
            def open(self, req, timeout):
                handler = self.handler
                return handler.redirect_request(req, None, 302, "Moved", {}, "https://evil.test")
        opener = OpenRedirect()
        def builder(handler):
            opener.handler = handler
            return opener
        with patch.object(tool, "build_opener", side_effect=builder):
            with self.assertRaisesRegex(tool.Blocked, "C2B_PROVIDER_REDIRECT_BLOCKED"):
                tool.get_call(ACCOUNT, CALL, ACCOUNT, TOKEN)

    def test_http_failure_fail_closed(self):
        class Failing:
            def open(self, req, timeout):
                raise OSError("PRIVATE credential details")
        with patch.object(tool, "build_opener", return_value=Failing()):
            with self.assertRaisesRegex(tool.Blocked, "C2B_PROVIDER_READ_UNAVAILABLE") as ctx:
                tool.get_call(ACCOUNT, CALL, ACCOUNT, TOKEN)
        self.assertNotIn("PRIVATE", str(ctx.exception))

    def test_private_report_does_not_persist_identifiers_or_go(self):
        with tempfile.TemporaryDirectory() as root:
            p = tool.write_private_report("completed", root)
            value = p.read_text(encoding="utf-8")
            self.assertIn('"carrier_terminal_get_observed": true', value)
            self.assertIn('"commercial_go": false', value)
            self.assertIn('"authenticated_callback": "UNVERIFIED"', value)
            for secret in (ACCOUNT, CALL, TENANT, TOKEN):
                self.assertNotIn(secret, value)
            p2 = tool.write_private_report("in-progress", root)
            self.assertIn('"carrier_terminal_get_observed": false', p2.read_text())

    def test_missing_opt_in_does_not_access_network_or_write(self):
        for argv, env in (([], environment()),
                          (["--probe-read-only"], environment(C2B_CALL_READBACK_APPROVED="no")),
                          (["--probe-read-only"], environment(C2B_EXPECTED_ACCOUNT_SID="invalid")),
                          (["--probe-read-only"], environment(C2B_PILOT_TENANT_ID="invalid"))):
            with patch.object(tool, "get_call") as reader, patch.object(tool,"write_private_report") as writer:
                with redirect_stdout(io.StringIO()) as out:
                    self.assertEqual(2, tool.main(argv, env))
                reader.assert_not_called()
                writer.assert_not_called()
                self.assertIn("C2B_NO_RELEASE_APPROVAL", out.getvalue())

    def test_explicit_single_terminal_readback_remains_no_go(self):
        for status, expected in (("completed", 0), ("in-progress", 2)):
            with tempfile.TemporaryDirectory() as root:
                with patch.object(tool,"get_call",return_value={
                    "sid":CALL,"account_sid":ACCOUNT,"status":status}) as read:
                    with redirect_stdout(io.StringIO()) as out:
                        self.assertEqual(expected, tool.main(["--probe-read-only"],
                            environment(LOCALAPPDATA=root)))
                read.assert_called_once_with(ACCOUNT, CALL, ACCOUNT, TOKEN)
                self.assertIn("C2B_NO_RELEASE_APPROVAL",out.getvalue())
                self.assertNotIn(CALL,out.getvalue())
                self.assertNotIn(ACCOUNT,out.getvalue())
                self.assertNotIn(TOKEN,out.getvalue())
                self.assertEqual(1,len(list(Path(root).rglob("*.private.json"))))

    def test_subaccount_must_match_known_configuration(self):
        with patch.object(tool,"get_call") as read:
            with redirect_stdout(io.StringIO()):
                self.assertEqual(2,tool.main(["--probe-read-only"],environment(
                    C2B_EXPECTED_ACCOUNT_SID=CHILD)))
            read.assert_not_called()

    def test_malformed_oversized_or_non_object_provider_responses(self):
        class Response:
            status = 200
            def __init__(self, content): self.content = content
            def __enter__(self): return self
            def __exit__(self, *a): pass
            def geturl(self): return tool.BASE + f"/2010-04-01/Accounts/{ACCOUNT}/Calls/{CALL}.json"
            def read(self, n): return self.content
        class Opener:
            def __init__(self, content): self.content = content
            def open(self, req, timeout): return Response(self.content)
        for raw, code in ((b"invalid-json", "C2B_PROVIDER_RESPONSE_INVALID"),
                          (b"\xff", "C2B_PROVIDER_RESPONSE_INVALID"),
                          (b"[]", "C2B_PROVIDER_RESPONSE_INVALID"),
                          (b"x" * 65537, "C2B_PROVIDER_RESPONSE_TOO_LARGE")):
            with self.subTest(code=code, size=len(raw)):
                with patch.object(tool, "build_opener", return_value=Opener(raw)):
                    with self.assertRaisesRegex(tool.Blocked, code):
                        tool.get_call(ACCOUNT, CALL, ACCOUNT, TOKEN)

    def test_private_destination_symlink_and_permission_fail_closed(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.object(tool.Path, "mkdir", side_effect=OSError("do not expose path")):
                with self.assertRaisesRegex(tool.Blocked, "C2B_PRIVATE_STORAGE_WRITE_FAILED"):
                    tool.write_private_report("completed", root)
            with self.assertRaisesRegex(tool.Blocked, "C2B_PRIVATE_STORAGE_MISSING"):
                tool.write_private_report("completed", "")
            folder = Path(root) / "Helvoca"
            folder.symlink_to(Path(root), target_is_directory=True)
            with self.assertRaisesRegex(tool.Blocked, "C2B_PRIVATE_STORAGE_UNSAFE"):
                tool.write_private_report("completed", root)

    def test_missing_credentials_or_provider_status_remain_blocked(self):
        with self.assertRaisesRegex(tool.Blocked, "C2B_CREDENTIALS_MISSING"):
            tool.get_call(ACCOUNT, CALL, ACCOUNT, "")
        with patch.object(tool, "get_call", return_value={
                "sid":CALL, "account_sid":ACCOUNT, "status":"unrecognized"}):
            with redirect_stdout(io.StringIO()) as output:
                result=tool.main(["--probe-read-only"], environment())
            self.assertEqual(2, result)
            self.assertIn("C2B_PROVIDER_STATUS_UNKNOWN", output.getvalue())

    def test_script_entry_point_requires_opt_in_before_provider(self):
        with patch.object(tool.sys, "argv", [str(Path(__file__).with_name("c2b_twilio_call_terminal_readonly.py"))]), \
             patch.object(tool, "get_call") as read:
            with redirect_stdout(io.StringIO()) as output:
                with self.assertRaises(SystemExit) as exit_:
                    runpy.run_path(str(Path(__file__).with_name("c2b_twilio_call_terminal_readonly.py")), run_name="__main__")
            self.assertEqual(2, exit_.exception.code)
            read.assert_not_called()
            self.assertIn("C2B_NO_RELEASE_APPROVAL", output.getvalue())


if __name__ == "__main__":
    unittest.main()
