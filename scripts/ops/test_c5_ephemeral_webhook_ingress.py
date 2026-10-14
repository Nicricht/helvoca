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
    from c5_ephemeral_webhook_ingress import Collector, InvalidWebhook, verify, strict_json, parse_signature
except ModuleNotFoundError:
    from scripts.ops.c5_ephemeral_webhook_ingress import Collector, InvalidWebhook, verify, strict_json, parse_signature

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

    def test_real_email_or_token_not_in_logs(self):
        from scripts.ops import c5_ephemeral_webhook_ingress as mod
        self.assertEqual(mod.Handler.log_message(None, "fake email %s", "hello@example.com"), None)

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
        self.assertEqual(code, 204)
        self.assertEqual(data, b"")
        self.assertEqual(self.server.received, n+1)
        self.assertIn(("subscription_authorized_payment", "998877"), self.server.unique)

    def test_oversized_payload_fails_closed(self):
        self.assertEqual(self.send("/webhooks/v1/mercadopago", b"x"*4097)[0], 413)

if __name__ == "__main__":
    unittest.main()
