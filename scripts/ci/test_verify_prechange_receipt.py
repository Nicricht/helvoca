"""Adversarial tests for the dynamic pre-change receipt PR gate."""
import json
import runpy
import os
import unittest
from io import BytesIO
from unittest.mock import patch
from urllib.error import URLError

import verify_prechange_receipt as subject

HEAD = "a" * 40
PARENT = "b" * 40
BASE = "c" * 40
BRANCH = "release/test"
DATE = "2026-10-16T19:45:00Z"


def comment(**overrides):
    fields = {
        "AUDITED_HEAD": PARENT,
        "BASE_SHA": BASE,
        "BRANCH": BRANCH,
        "PRIOR_EVIDENCE": "https://github.com/Nicricht/helvoca/issues/823#issuecomment-123",
        "NEW_DELTA": "Exact unmet external criterion not previously demonstrated",
        "AFFECTED_FILES": "scripts/ci/verify_prechange_receipt.py",
        "QA_PLAN": "Unit RED-GREEN plus branch coverage and exact HEAD CI",
        "DOC_TARGET": "docs/engineering/COMPLETION_DOCUMENTATION_POLICY.md",
    }
    fields.update(overrides.pop("fields", {}))
    c = {
        "author_association": "OWNER", "created_at": "2026-10-16T19:30:00Z",
        "body": subject.MARKER + "\n" + "\n".join(f"{k}: {v}" for k, v in fields.items()),
    }
    c.update(overrides)
    return c


class ReceiptTests(unittest.TestCase):
    def test_valid_owner_receipt(self):
        self.assertTrue(subject.valid_receipt(comment(), PARENT, BASE, BRANCH, DATE))

    def test_untrusted_author_and_nonreceipt(self):
        self.assertFalse(subject.valid_receipt(comment(author_association="NONE"), PARENT, BASE, BRANCH, DATE))
        self.assertFalse(subject.valid_receipt(comment(body="Generic status update"), PARENT, BASE, BRANCH, DATE))
        self.assertFalse(subject.valid_receipt(comment(body=None), PARENT, BASE, BRANCH, DATE))

    def test_missing_or_duplicate_fields(self):
        c = comment(fields={"DOC_TARGET": ""})
        c["body"] = c["body"].rsplit("\n", 1)[0]
        self.assertFalse(subject.valid_receipt(c, PARENT, BASE, BRANCH, DATE))
        c = comment()
        c["body"] += "\nDOC_TARGET: docs/another.md"
        self.assertFalse(subject.valid_receipt(c, PARENT, BASE, BRANCH, DATE))

    def test_replayed_wrong_audited_head_base_and_branch(self):
        for vals in ({"AUDITED_HEAD": HEAD}, {"BASE_SHA": HEAD}, {"BRANCH": "old-branch"}):
            with self.subTest(vals=vals):
                self.assertFalse(subject.valid_receipt(comment(fields=vals), PARENT, BASE, BRANCH, DATE))

    def test_malformed_sha_short_placeholder_or_missing_citation(self):
        for vals in ({"AUDITED_HEAD": "not-a-sha"}, {"BASE_SHA": "bad"},
                     {"NEW_DELTA": "pending"}, {"PRIOR_EVIDENCE": "Evidence in old chat"}):
            with self.subTest(vals=vals):
                self.assertFalse(subject.valid_receipt(comment(fields=vals), PARENT, BASE, BRANCH, DATE))

    def test_sha_checks_fail_even_if_expected_legacy_fields_match(self):
        self.assertFalse(subject.valid_receipt(comment(fields={"AUDITED_HEAD": "bad"}), "bad", BASE, BRANCH, DATE))
        self.assertFalse(subject.valid_receipt(comment(fields={"BASE_SHA": "bad"}), PARENT, "bad", BRANCH, DATE))

    def test_comment_after_commit_is_invalid(self):
        self.assertFalse(subject.valid_receipt(comment(created_at="2026-10-16T19:50:00Z"), PARENT, BASE, BRANCH, DATE))

    def test_unreadable_comment_timestamp_is_invalid(self):
        for bad in ("wrong", None):
            self.assertFalse(subject.valid_receipt(comment(created_at=bad), PARENT, BASE, BRANCH, DATE))
        c = comment()
        del c["created_at"]
        self.assertFalse(subject.valid_receipt(c, PARENT, BASE, BRANCH, DATE))

    def test_supports_member_and_collaborator(self):
        self.assertTrue(subject.valid_receipt(comment(author_association="MEMBER"), PARENT, BASE, BRANCH, DATE))
        self.assertTrue(subject.valid_receipt(comment(author_association="COLLABORATOR"), PARENT, BASE, BRANCH, DATE))


class VerifyTests(unittest.TestCase):
    def fetcher(self, comments=None, pr_head=HEAD, parents=None, large=False):
        calls = []
        if comments is None:
            comments = [comment()]
        if parents is None:
            parents = [{"sha": PARENT}]

        def fetch(path, token):
            calls.append(path)
            self.assertEqual(token, "fake-token")
            if path.endswith("/pulls/846"):
                return {"head": {"sha": pr_head, "ref": BRANCH}, "base": {"sha": BASE}}
            if path.endswith("/git/commits/" + HEAD):
                return {"parents": parents, "committer": {"date": DATE}}
            if "/issues/846/comments?" in path:
                page = int(path.rsplit("page=", 1)[1])
                if large:
                    return [comment()] * 100
                return comments[(page - 1) * 100:page * 100]
            raise AssertionError(path)
        return fetch, calls

    def test_real_lifecycle_accepts_matching_receipt(self):
        fetch, calls = self.fetcher()
        subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=fetch)
        self.assertEqual(len(calls), 3)

    def test_paginates_when_over_hundred_comments(self):
        fetch, calls = self.fetcher(comments=[comment(body="x")] * 100 + [comment()])
        subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=fetch)
        self.assertEqual(len(calls), 4)

    def test_invalid_context_fails_closed(self):
        fetch, _ = self.fetcher()
        for repo, head, token in (("../bad", HEAD, "fake-token"),
                                  ("Nicricht/helvoca", "bad", "fake-token"),
                                  ("Nicricht/helvoca", HEAD, "")):
            with self.assertRaises(subject.ReceiptFailure):
                subject.verify(repo, 846, head, token, fetch=fetch)

    def test_changed_head_fails_closed(self):
        fetch, _ = self.fetcher(pr_head=PARENT)
        with self.assertRaisesRegex(subject.ReceiptFailure, "moved"):
            subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=fetch)

    def test_merge_commit_fails_closed(self):
        fetch, _ = self.fetcher(parents=[{"sha": PARENT}, {"sha": BASE}])
        with self.assertRaisesRegex(subject.ReceiptFailure, "merge/root"):
            subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=fetch)

    def test_no_applicable_receipt_fails_closed(self):
        fetch, _ = self.fetcher(comments=[comment(fields={"AUDITED_HEAD": HEAD})])
        with self.assertRaisesRegex(subject.ReceiptFailure, "No trusted"):
            subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=fetch)

    def test_overlong_pagination_fails_closed(self):
        fetch, calls = self.fetcher(large=True)
        with self.assertRaisesRegex(subject.ReceiptFailure, "pagination"):
            subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=fetch)
        self.assertEqual(len(calls), 22)

    def test_provider_failure_fails_closed(self):
        def bad_fetch(path, token):
            raise URLError("offline")
        with self.assertRaises(URLError):
            subject.verify("Nicricht/helvoca", 846, HEAD, "fake-token", fetch=bad_fetch)


class EntrypointTests(unittest.TestCase):
    def test_non_pr_event_is_not_gated(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "push"}):
            self.assertEqual(subject.main(), 0)

    def test_valid_pr_event(self):
        env = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REPOSITORY": "Nicricht/helvoca",
               "HELVOCA_PR_NUMBER": "846", "HELVOCA_PR_HEAD_SHA": HEAD, "GITHUB_TOKEN": "fake-token"}
        with patch.dict(os.environ, env), patch.object(subject, "verify") as check:
            self.assertEqual(subject.main(), 0)
            check.assert_called_once()

    def test_invalid_env_fails_closed_without_leaking_secrets(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_TOKEN": "SECRET"}, clear=True):
            self.assertEqual(subject.main(), 1)
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REPOSITORY": "Nicricht/helvoca",
                                          "HELVOCA_PR_NUMBER": "not-number", "HELVOCA_PR_HEAD_SHA": HEAD, "GITHUB_TOKEN": "SECRET"}, clear=True):
            self.assertEqual(subject.main(), 1)

    def test_github_transport_uses_readonly_token_and_decodes_json(self):
        def open_mock(request, timeout):
            self.assertEqual(timeout, 15)
            self.assertEqual(request.get_header("Authorization"), "Bearer TEST_SECRET")
            self.assertTrue(request.full_url.startswith("https://api.github.com/repos/"))
            return BytesIO(b'{"state":"good"}')
        with patch.object(subject, "urlopen", side_effect=open_mock):
            self.assertEqual(subject.github_json("/repos/Nicricht/helvoca/pulls/846", "TEST_SECRET"), {"state": "good"})

    def test_script_main_guard(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "push"}):
            with self.assertRaises(SystemExit) as result:
                runpy.run_path(subject.__file__, run_name="__main__")
        self.assertEqual(result.exception.code, 0)

    def test_runtime_failure_fails_closed(self):
        env = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REPOSITORY": "Nicricht/helvoca",
               "HELVOCA_PR_NUMBER": "846", "HELVOCA_PR_HEAD_SHA": HEAD, "GITHUB_TOKEN": "SECRET"}
        with patch.dict(os.environ, env), patch.object(subject, "verify", side_effect=subject.ReceiptFailure("SECRET")):
            self.assertEqual(subject.main(), 1)


if __name__ == "__main__":
    unittest.main()
