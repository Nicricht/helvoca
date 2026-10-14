"""Unit and local HTTP tests, entirely fake data, no external payments."""
import hashlib
import hmac
import http.client
import json
import threading
import time
import unittest
from email.message import Message
from urllib.parse import urlencode

try:
    from c5_ephemeral_webhook_ingress import Collector, InvalidWebhook, verify, strict_json, parse_signature, require_sandbox_event, safe_rejection_reason
except ModuleNotFoundError:
    from scripts.ops.c5_ephemeral_webhook_ingress import Collector, InvalidWebhook, verify, strict_json, parse_signature, require_sandbox_event, safe_rejection_reason

SECRET = "c5-ephemeral-local-fixture-not-provider-secret"

def signed(topic="subscription_authorized_payment", event_id="123456", request_id="req-c5-1",
           ts=None, secret=SECRET, **kw):
    stamp = str(int(time.time()*1000) if ts is None else ts)
    text = f"id:{event_id.lower()};request-id:{request_id};ts:{stamp};".encode()
    digest = hmac.new(secret.encode(), text, hashlib.sha256).hexdigest()
    headers = Message()
    headers.add_header("x-signature", f"ts={stamp},v1={digest}")
    headers.add_header("x-request-id", request_id)
    body = json.dumps({"type": topic, "data": {"id": event_id}}).encode()
    return body, headers

class HmacContract(unittest.TestCase):
    def test_valid_authenticated_authorized_payment(self):
        body, headers = signed()
        self.assertEqual(verify(body, "", headers, SECRET),
                         ("subscription_authorized_payment", "123456"))

    def test_valid_preapproval(self):
        body, headers = signed("subscription_preapproval", "abc-123")
        self.assertEqual(verify(body, "", headers, SECRET),
                         ("subscription_preapproval", "abc-123"))

    def test_signature_changes_with_id(self):
        body, headers = signed(event_id="123456")
        body = json.dumps({"type":"subscription_authorized_payment", "data":{"id":"999999"}}).encode()
        with self.assertRaises(InvalidWebhook):
            verify(body, "", headers, SECRET)

    def test_signature_changes_with_request_id(self):
        body, headers = signed()
        headers.replace_header("x-request-id", "attacker")
        with self.assertRaises(InvalidWebhook):
            verify(body, "", headers, SECRET)

    def test_disallowed_topic_cannot_be_counted(self):
        body, headers = signed("payment", "123456")
        with self.assertRaises(InvalidWebhook):
            verify(body, "", headers, SECRET)

    def test_stale_and_future_timestamps_rejected(self):
        now = int(time.time()*1000)
        for stamp in (now-300_001, now+300_001):
            body, headers = signed(ts=stamp)
            with self.assertRaises(InvalidWebhook):
                verify(body, "", headers, SECRET, now_ms=now)

    def test_seconds_timestamp_is_accepted(self):
        secs = int(time.time())
        body, headers = signed(ts=secs)
        self.assertEqual(verify(body, "", headers, SECRET)[1], "123456")

    def test_duplicate_json_keys_fail(self):
        with self.assertRaises(InvalidWebhook):
            strict_json(b'{"type":"x","type":"y"}')

    def test_duplicate_signature_fields_fail(self):
        with self.assertRaises(InvalidWebhook):
            parse_signature("ts=1700000000,ts=1700000000,v1="+"a"*64)

    def test_duplicate_signature_headers_fail(self):
        body, headers = signed()
        headers.add_header("x-signature", headers.get("x-signature"))
        with self.assertRaises(InvalidWebhook):
            verify(body, "", headers, SECRET)

    def test_conflicting_body_query_fields_fail(self):
        body, headers = signed()
        with self.assertRaises(InvalidWebhook):
            verify(body, "type=subscription_authorized_payment&data.id=999999", headers, SECRET)

    def test_bad_signature_has_priority_over_a_bad_topic(self):
        body, headers = signed(event_id="123456", secret="wrong-test-secret")
        with self.assertRaisesRegex(InvalidWebhook, "HMAC mismatch"):
            verify(body, "type=orders", headers, SECRET)

    def test_signed_conflicting_topic_is_classified(self):
        body, headers = signed("subscription_preapproval", "123456")
        with self.assertRaisesRegex(InvalidWebhook, "topic conflict"):
            verify(body, "type=subscription_authorized_payment", headers, SECRET)

    def test_signed_query_topic_not_allowed(self):
        body, headers = signed("subscription_preapproval", "123456")
        with self.assertRaisesRegex(InvalidWebhook, "query topic unsupported"):
            verify(body, "type=orders", headers, SECRET)

    def test_signed_body_topic_not_allowed(self):
        body, headers = signed("subscription_preapproval", "123456")
        modified = json.dumps({"type":"orders","data":{"id":"123456"}}).encode()
        with self.assertRaisesRegex(InvalidWebhook, "body topic unsupported"):
            verify(modified, "", headers, SECRET)

    def test_real_email_or_token_not_in_logs(self):
        from scripts.ops import c5_ephemeral_webhook_ingress as mod
        self.assertEqual(mod.Handler.log_message(None, "fake email %s", "hello@example.com"), None)

def mp_simulator_fixture():
    """Fixed synthetic example from MP Developers' webhook simulator."""
    return {
        "action": "updated",
        "application_id": "1234509876",
        "data": {"id": "123456"},
        "date": "2021-11-01T02:02:02Z",
        "entity": "preapproval",
        "id": "123456",
        "type": "subscription_preapproval",
        "version": 8,
    }


class DiagnosticNoSecretLeakContract(unittest.TestCase):
    def test_hmac_mismatch_is_classified_safely(self):
        self.assertEqual(safe_rejection_reason(InvalidWebhook("HMAC mismatch")), "HMAC_MISMATCH")

    def test_event_shape_is_not_called_credential_failure(self):
        self.assertEqual(safe_rejection_reason(InvalidWebhook("event id required")), "EVENT_ID_MISSING")

    def test_structural_rejections_have_distinct_safe_classes(self):
        cases = {
            "topic missing": "TOPIC_MISSING",
            "query topic unsupported": "TOPIC_QUERY_UNSUPPORTED",
            "body topic unsupported": "TOPIC_BODY_UNSUPPORTED",
            "topic conflict": "TOPIC_CONFLICT",
            "event id required": "EVENT_ID_MISSING",
            "conflicting event id": "EVENT_ID_CONFLICT",
            "event id invalid": "EVENT_ID_FORMAT",
            "event id shape invalid": "EVENT_ID_FORMAT",
        }
        for problem, expected in cases.items():
            with self.subTest(problem=problem):
                self.assertEqual(safe_rejection_reason(InvalidWebhook(problem)), expected)

    def test_unrecognized_values_never_emerge_in_logs(self):
        private = "not-for-logs-identity-or-token-123"
        self.assertEqual(safe_rejection_reason(InvalidWebhook(private)), "UNKNOWN")


class StrictSandboxEventContract(unittest.TestCase):
    def test_only_explicit_sandbox_event_is_eligible(self):
        self.assertTrue(require_sandbox_event(b'{"live_mode":false}'))

    def test_productive_flag_rejected(self):
        with self.assertRaisesRegex(InvalidWebhook, "non-test"):
            require_sandbox_event(b'{"live_mode":true}')

    def test_missing_flag_rejected(self):
        with self.assertRaisesRegex(InvalidWebhook, "non-test"):
            require_sandbox_event(b'{"type":"subscription_authorized_payment"}')

    def test_string_false_does_not_count_as_sandbox(self):
        with self.assertRaisesRegex(InvalidWebhook, "non-test"):
            require_sandbox_event(b'{"live_mode":"false"}')


    def test_only_exact_mpsimulator_fixture_without_live_mode_is_allowed(self):
        data = json.dumps(mp_simulator_fixture()).encode()
        self.assertEqual(require_sandbox_event(data), "SIMULATOR_ONLY")

    def test_suspected_real_event_with_missing_live_mode_is_rejected(self):
        data = mp_simulator_fixture()
        data["date"] = "2026-10-14T10:55:00Z"
        with self.assertRaisesRegex(InvalidWebhook, "non-test"):
            require_sandbox_event(json.dumps(data).encode())

    def test_simulator_with_explicit_live_true_is_always_rejected(self):
        data = mp_simulator_fixture()
        data["live_mode"] = True
        with self.assertRaisesRegex(InvalidWebhook, "non-test"):
            require_sandbox_event(json.dumps(data).encode())

    def test_altered_simulator_identifier_is_rejected(self):
        data = mp_simulator_fixture()
        data["data"]["id"] = "987654"
        with self.assertRaisesRegex(InvalidWebhook, "non-test"):
            require_sandbox_event(json.dumps(data).encode())


class RenderStrictHttpContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = Collector(("127.0.0.1", 0), SECRET, require_test_mode=True)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def send(self, body, headers):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        conn.request("POST", "/webhooks/v1/mercadopago", body=body,
                     headers={k: v for k, v in headers.items()})
        res = conn.getresponse()
        status = res.status
        res.read()
        conn.close()
        return status

    def test_valid_signed_fake_test_webhook_is_acknowledged_and_counted(self):
        body, headers = signed(event_id="981234")
        body = json.dumps({"live_mode":False,"type":"subscription_authorized_payment",
                           "data":{"id":"981234"}}).encode()
        from io import StringIO
        from unittest.mock import patch
        out = StringIO()
        with patch("builtins.print", side_effect=lambda *v, **k: out.write(v[0]+"\\n")):
            self.assertEqual(self.send(body,headers),200)
        self.assertIn("C5_AUTHENTICATED_TEST_RECEIPT_TOPIC=subscription_authorized_payment",out.getvalue())
        self.assertNotIn("981234",out.getvalue())

    def test_signed_production_event_is_rejected_and_not_counted(self):
        body,headers = signed(event_id="987654")
        body = json.dumps({"live_mode":True, "type":"subscription_authorized_payment",
                           "data":{"id":"987654"}}).encode()
        n = self.server.received
        self.assertEqual(self.send(body,headers),401)
        self.assertEqual(self.server.received,n)

    def test_signed_event_missing_environment_flag_is_rejected(self):
        body, headers=signed(event_id="453212")
        n=self.server.received
        self.assertEqual(self.send(body,headers),401)
        self.assertEqual(self.server.received,n)


    def test_signed_mp_simulator_without_live_mode_is_accepted_but_not_paid(self):
        body, headers = signed(topic="subscription_preapproval", event_id="123456")
        body = json.dumps(mp_simulator_fixture()).encode()
        from io import StringIO
        from unittest.mock import patch
        output = StringIO()
        with patch("builtins.print", side_effect=lambda *v, **k: output.write(v[0]+"\\n")):
            self.assertEqual(self.send(body, headers), 200)
        self.assertIn(
            "C5_AUTHENTICATED_SIMULATOR_RECEIPT_TOPIC=subscription_preapproval",
            output.getvalue())
        self.assertNotIn("123456", output.getvalue())
        self.assertNotIn("1234509876", output.getvalue())

    def test_mp_simulator_with_wrong_signature_is_rejected(self):
        body, headers = signed(topic="subscription_preapproval",
                               event_id="123456", secret="wrong-fake-secret")
        body = json.dumps(mp_simulator_fixture()).encode()
        previous = self.server.received
        from io import StringIO
        from unittest.mock import patch
        output = StringIO()
        with patch("builtins.print", side_effect=lambda *v, **k: output.write(v[0] + "\\n")):
            self.assertEqual(self.send(body, headers), 401)
        self.assertEqual(self.server.received, previous)
        self.assertIn("C5_TEST_WEBHOOK_REJECT_CAUSE=HMAC_MISMATCH", output.getvalue())
        self.assertNotIn("123456", output.getvalue())
        self.assertNotIn("wrong-fake-secret", output.getvalue())

    def test_expired_simulator_hmac_cannot_bypass_timestamp_window(self):
        body, headers = signed(topic="subscription_preapproval",
                               event_id="123456",
                               ts=int(time.time()*1000)-300_001)
        body = json.dumps(mp_simulator_fixture()).encode()
        previous = self.server.received
        self.assertEqual(self.send(body, headers), 401)
        self.assertEqual(self.server.received, previous)

    def test_public_webhook_handler_emits_safe_entry_marker(self):
        # A proxy-level 502 does not reach this handler; 401 must show ingress.
        from io import StringIO
        from unittest.mock import patch
        fake_secret = "PRIVATE_DO_NOT_LOG"
        out = StringIO()
        with patch("builtins.print", side_effect=lambda *v, **k: out.write(v[0] + "\\n")):
            self.assertEqual(self.send(b'{"example":"PRIVATE_DO_NOT_LOG"}', {}), 401)
        self.assertIn("C5_TEST_WEBHOOK_HANDLER_ENTERED", out.getvalue())
        self.assertNotIn(fake_secret, out.getvalue())

    def test_simulator_missing_hmac_is_rejected(self):
        body = json.dumps(mp_simulator_fixture()).encode()
        previous = self.server.received
        self.assertEqual(self.send(body, {}), 401)
        self.assertEqual(self.server.received, previous)


class LocalHttpContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = Collector(("127.0.0.1", 0), SECRET)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def send(self, path, body=b"", headers=None, method="POST"):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        custom = {k:v for k,v in (headers.items() if headers else ())}
        conn.request(method, path, body=body, headers=custom)
        res = conn.getresponse()
        status, data = res.status, res.read()
        conn.close()
        return status, data

    def test_health_is_accessible(self):
        self.assertEqual(self.send("/health", method="GET")[0], 204)

    def test_wrong_path_fails(self):
        self.assertEqual(self.send("/admin", b"{}", {})[0], 404)

    def test_unsigned_webhook_fails_with_no_leak(self):
        code, body = self.send("/webhooks/v1/mercadopago", b'{"secret":"not-to-log"}')
        self.assertEqual(code, 401)
        self.assertEqual(body, b"")

    def test_valid_signed_webhook_receipt(self):
        body, headers = signed(event_id="998877")
        n = self.server.received
        code, data = self.send("/webhooks/v1/mercadopago", body, headers)
        self.assertEqual(code, 200)
        self.assertEqual(data, b"")
        self.assertEqual(self.server.received, n+1)
        self.assertIn(("subscription_authorized_payment", "998877"), self.server.unique)

    def test_oversized_payload_fails_closed(self):
        self.assertEqual(self.send("/webhooks/v1/mercadopago", b"x"*4097)[0], 413)

if __name__ == "__main__":
    unittest.main()
