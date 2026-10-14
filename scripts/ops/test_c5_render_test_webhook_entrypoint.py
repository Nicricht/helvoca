"""Render deployment entrypoint: never start without TEST-only gates."""
import os
import unittest
from unittest.mock import patch

try:
    from c5_render_test_webhook_entrypoint import settings, main
except ModuleNotFoundError:
    from scripts.ops.c5_render_test_webhook_entrypoint import settings, main

SECRET = "a" * 64

def good():
    return {"C5_RENDER_TEST_ONLY": "isolated-no-payment",
            "C5_EPHEMERAL_MODE": "test-only",
            "C5_EPHEMERAL_HMAC_SECRET": SECRET, "PORT": "10000"}

class TestRenderFreeEntrypoint(unittest.TestCase):
    def test_explicit_test_settings(self):
        self.assertEqual(settings(good()), (SECRET, 10000))

    def test_mode_missing_fails_closed(self):
        env = good()
        env.pop("C5_RENDER_TEST_ONLY")
        with self.assertRaisesRegex(ValueError, "PRODUCTION_MODE_FORBIDDEN"):
            settings(env)

    def test_wrong_mode_fails_closed(self):
        env = good()
        env["C5_RENDER_TEST_ONLY"] = "production"
        with self.assertRaisesRegex(ValueError, "PRODUCTION_MODE_FORBIDDEN"):
            settings(env)

    def test_ingress_test_gate_missing_fails_closed(self):
        env = good()
        env["C5_EPHEMERAL_MODE"] = "production"
        with self.assertRaisesRegex(ValueError, "TEST_INGRESS_DISABLED"):
            settings(env)

    def test_weak_secret_fails_closed(self):
        env = good()
        env["C5_EPHEMERAL_HMAC_SECRET"] = "not-long-enough"
        with self.assertRaisesRegex(ValueError, "SIGNING_SECRET_UNAVAILABLE"):
            settings(env)

    def test_mp_access_token_never_allowed_as_hmac_key(self):
        env = good()
        env["C5_EPHEMERAL_HMAC_SECRET"] = "APP_USR-" + "a" * 64
        with self.assertRaisesRegex(ValueError, "SIGNING_SECRET_UNAVAILABLE"):
            settings(env)

    def test_missing_port_fails_closed(self):
        env = good()
        env["PORT"] = ""
        with self.assertRaisesRegex(ValueError, "PORT_MISSING_OR_INVALID"):
            settings(env)

    def test_invalid_port_fails_closed(self):
        env = good()
        env["PORT"] = "999"
        with self.assertRaisesRegex(ValueError, "PORT_OUT_OF_RANGE"):
            settings(env)

    def test_non_numeric_port_fails_closed(self):
        env = good()
        env["PORT"] = "bad"
        with self.assertRaisesRegex(ValueError, "PORT_MISSING_OR_INVALID"):
            settings(env)

    def test_real_socket_binds_render_public_interface_only_in_test_mode(self):
        from unittest.mock import MagicMock
        fake = MagicMock()
        with patch.dict(os.environ, good(), clear=True):
            with patch("scripts.ops.c5_render_test_webhook_entrypoint.Collector", return_value=fake) as factory:
                self.assertEqual(main(), 0)
                factory.assert_called_once_with(("0.0.0.0", 10000), SECRET)
                fake.serve_forever.assert_called_once()
                fake.server_close.assert_called_once()

    def test_main_without_test_gates_never_binds_any_listener(self):
        with patch.dict(os.environ, {"PORT": "10000"}, clear=True):
            with patch("scripts.ops.c5_render_test_webhook_entrypoint.Collector") as factory:
                self.assertEqual(main(), 2)
                factory.assert_not_called()

if __name__ == "__main__":
    unittest.main()
