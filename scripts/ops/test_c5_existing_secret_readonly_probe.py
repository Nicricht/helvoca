import hashlib
import io
import json
import unittest
from urllib import error

try:
    from c5_existing_secret_readonly_probe import (
        IDENTITY_URL, ProbeBlocked, probe, verify_public_identity)
except ModuleNotFoundError:
    from scripts.ops.c5_existing_secret_readonly_probe import (
        IDENTITY_URL, ProbeBlocked, probe, verify_public_identity)

ACCOUNT = {"id": 123456, "nickname": "TESTUSERFAKE1234", "site_id": "MLC"}
EXPECTED = hashlib.sha256(b"123456|TESTUSERFAKE1234|MLC").hexdigest()

class ReadOnlyExistingSecretTests(unittest.TestCase):
    def test_valid_test_seller(self):
        self.assertTrue(verify_public_identity(ACCOUNT, EXPECTED))

    def test_wrong_test_seller_is_blocked(self):
        with self.assertRaisesRegex(ProbeBlocked, "SELLER_IDENTITY_MISMATCH"):
            verify_public_identity({**ACCOUNT, "id": 654321}, EXPECTED)


    def test_seller_id_matches_but_alias_differs(self):
        seller_id_hash = hashlib.sha256(b"123456").hexdigest()
        with self.assertRaisesRegex(ProbeBlocked, "SELLER_ID_MATCH_BUT_ALIAS_MISMATCH"):
            verify_public_identity({**ACCOUNT, "nickname": "TESTUSEROTHER123"}, EXPECTED,
                                   seller_id_digest=seller_id_hash)

    def test_buyer_token_detected_without_exposing_id(self):
        buyer_id_hash = hashlib.sha256(b"123456").hexdigest()
        with self.assertRaisesRegex(ProbeBlocked, "IS_SCREENSHOT_BUYER_NOT_SELLER"):
            verify_public_identity(ACCOUNT, hashlib.sha256(b"mismatching-seller").hexdigest(),
                                   buyer_id_digest=buyer_id_hash)

    def test_other_test_account_detected_without_exposing_id(self):
        with self.assertRaisesRegex(ProbeBlocked, "IS_DIFFERENT_TEST_ACCOUNT"):
            verify_public_identity(ACCOUNT, hashlib.sha256(b"other-test-account").hexdigest(),
                                   seller_id_digest=hashlib.sha256(b"999").hexdigest(),
                                   buyer_id_digest=hashlib.sha256(b"998").hexdigest())

    def test_live_username_is_blocked(self):
        with self.assertRaisesRegex(ProbeBlocked, "NOT_CONFIRMED_TEST_SELLER"):
            verify_public_identity({**ACCOUNT, "nickname": "LIVEUSER"}, EXPECTED)

    def test_wrong_country_is_blocked(self):
        with self.assertRaisesRegex(ProbeBlocked, "NOT_CONFIRMED_TEST_SELLER"):
            verify_public_identity({**ACCOUNT, "site_id": "MLA"}, EXPECTED)

    def test_explicitly_false_test_user_is_blocked(self):
        with self.assertRaisesRegex(ProbeBlocked, "CONTRADICTED"):
            verify_public_identity({**ACCOUNT, "test_user": False}, EXPECTED)

    def test_real_account_email_is_blocked(self):
        with self.assertRaisesRegex(ProbeBlocked, "TEST_EMAIL_MISMATCH"):
            verify_public_identity({**ACCOUNT, "email": "real@example.com"}, EXPECTED)

    def test_missing_actions_secret(self):
        with self.assertRaisesRegex(ProbeBlocked, "SECRET_UNAVAILABLE"):
            probe("", EXPECTED, opener=lambda req, timeout: self.fail("provider called"))

    def test_invalid_token_format_blocked_before_provider(self):
        with self.assertRaisesRegex(ProbeBlocked, "TOKEN_FORMAT_UNEXPECTED"):
            probe("not-mercadopago", EXPECTED,
                  opener=lambda req, timeout: self.fail("provider called"))

    def test_only_get_and_identity_url_are_used(self):
        calls = []
        def opener(req, timeout):
            calls.append((req.get_method(), req.full_url, timeout))
            return io.BytesIO(json.dumps(ACCOUNT).encode())
        self.assertTrue(probe("APP_USR-dummy", EXPECTED, opener=opener))
        self.assertEqual(calls, [("GET", IDENTITY_URL, 8)])

    def test_invalid_json_is_sanitized(self):
        def opener(req, timeout): return io.BytesIO(b"not-json")
        with self.assertRaisesRegex(ProbeBlocked, "RESPONSE_INVALID"):
            probe("APP_USR-dummy", EXPECTED, opener=opener)

    def test_provider_unauthorized_is_sanitized(self):
        def opener(req, timeout):
            raise error.HTTPError(IDENTITY_URL, 401, "private", {}, None)
        with self.assertRaisesRegex(ProbeBlocked, "REJECTED_BY_PROVIDER"):
            probe("APP_USR-dummy", EXPECTED, opener=opener)

    def test_provider_unreachable_is_sanitized(self):
        def opener(req, timeout): raise RuntimeError("sensitive provider response")
        with self.assertRaisesRegex(ProbeBlocked, "LOOKUP_UNAVAILABLE"):
            probe("APP_USR-dummy", EXPECTED, opener=opener)

    def test_oversized_response_is_blocked(self):
        def opener(req, timeout): return io.BytesIO(b"a" * 16385)
        with self.assertRaisesRegex(ProbeBlocked, "OVERSIZED"):
            probe("APP_USR-dummy", EXPECTED, opener=opener)

if __name__ == "__main__":
    unittest.main()
