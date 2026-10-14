import io
import unittest

try:
    from c5_mercadopago_sandbox_preflight import (
        CONSENT, IDENTITY_URL, SandboxBlocked, check_provider_identity,
        validate_settings, verify_identity,
    )
except ModuleNotFoundError:
    from scripts.ops.c5_mercadopago_sandbox_preflight import (
        CONSENT, IDENTITY_URL, SandboxBlocked, check_provider_identity,
        validate_settings, verify_identity,
    )


def good_env():
    return {
        "CONFIRM_SANDBOX": "true",
        "CONFIRM_TEST_CREDENTIAL_SOURCE": CONSENT,
        "MERCADOPAGO_TEST_ACCESS_TOKEN": "APP_USR-" + ("x" * 20),
        "MERCADOPAGO_TEST_PAYER_EMAIL": "testuser123456789@testuser.com",
        "MERCADOPAGO_EXPECTED_TEST_SELLER_ID": "123456789",
    }


class TestC5SandboxSafety(unittest.TestCase):
    def expect_block(self, field, value, code):
        env = good_env()
        env[field] = value
        with self.assertRaises(SandboxBlocked) as error:
            validate_settings(env)
        self.assertEqual(str(error.exception), code)

    def test_valid_settings(self):
        self.assertEqual(validate_settings(good_env())[2], "123456789")

    def test_confirmation_required(self):
        self.expect_block("CONFIRM_SANDBOX", "", "C5_SANDBOX_EXPLICIT_CONFIRMATION_REQUIRED")

    def test_source_confirmation_required(self):
        self.expect_block("CONFIRM_TEST_CREDENTIAL_SOURCE", "", "C5_TEST_CREDENTIAL_SOURCE_UNCONFIRMED")

    def test_production_environment_blocked(self):
        self.expect_block("RAILWAY_ENVIRONMENT", "production", "C5_PRODUCTION_ENVIRONMENT_FORBIDDEN")

    def test_format_check(self):
        self.expect_block("MERCADOPAGO_TEST_ACCESS_TOKEN", "not-a-token", "C5_ACCESS_TOKEN_FORMAT_INVALID")

    def test_dummy_buyer_blocked(self):
        self.expect_block("MERCADOPAGO_TEST_PAYER_EMAIL", "test@testuser.com", "C5_REGISTERED_TEST_BUYER_REQUIRED")

    def test_real_buyer_blocked(self):
        self.expect_block("MERCADOPAGO_TEST_PAYER_EMAIL", "person@example.com", "C5_REGISTERED_TEST_BUYER_REQUIRED")

    def test_seller_id_required(self):
        self.expect_block("MERCADOPAGO_EXPECTED_TEST_SELLER_ID", "", "C5_EXPECTED_TEST_SELLER_ID_REQUIRED")

    def test_verified_identity(self):
        self.assertTrue(verify_identity({"id": 123456789, "site_id": "MLC", "test_user": True}, "123456789"))

    def test_false_test_user_blocked(self):
        with self.assertRaisesRegex(SandboxBlocked, "TEST_ACCOUNT_UNVERIFIED"):
            verify_identity({"id": 123456789, "site_id": "MLC", "test_user": False}, "123456789")

    def test_missing_test_user_field_blocked(self):
        with self.assertRaisesRegex(SandboxBlocked, "TEST_ACCOUNT_UNVERIFIED"):
            verify_identity({"id": 123456789, "site_id": "MLC"}, "123456789")

    def test_wrong_seller_blocked(self):
        with self.assertRaisesRegex(SandboxBlocked, "SELLER_ID_MISMATCH"):
            verify_identity({"id": 123456789, "site_id": "MLC", "test_user": True}, "789789789")

    def test_wrong_country_blocked(self):
        with self.assertRaisesRegex(SandboxBlocked, "SELLER_NOT_CHILE"):
            verify_identity({"id": 123456789, "site_id": "MLA", "test_user": True}, "123456789")

    def test_unstructured_identity_blocked(self):
        with self.assertRaisesRegex(SandboxBlocked, "IDENTITY_UNVERIFIED"):
            verify_identity([], "123456789")

    def test_authenticated_preflight_only_uses_read_only_get(self):
        calls = []
        def opener(req, timeout):
            calls.append((req.get_method(), req.full_url, timeout))
            return io.BytesIO(b'{"id":123456789,"site_id":"MLC","test_user":true}')
        self.assertTrue(check_provider_identity(good_env()["MERCADOPAGO_TEST_ACCESS_TOKEN"], "123456789", opener))
        self.assertEqual(calls, [("GET", IDENTITY_URL, 8)])

    def test_api_failure_does_not_expose_details(self):
        def opener(req, timeout):
            raise RuntimeError("provider-internal-detail")
        with self.assertRaises(SandboxBlocked) as error:
            check_provider_identity(good_env()["MERCADOPAGO_TEST_ACCESS_TOKEN"], "123456789", opener)
        self.assertEqual(str(error.exception), "C5_MP_IDENTITY_LOOKUP_FAILED")

    def test_overlarge_response_fails(self):
        def opener(req, timeout):
            return io.BytesIO(b"a" * 8193)
        with self.assertRaisesRegex(SandboxBlocked, "IDENTITY_UNVERIFIED"):
            check_provider_identity(good_env()["MERCADOPAGO_TEST_ACCESS_TOKEN"], "123456789", opener)


if __name__ == "__main__":
    unittest.main()
