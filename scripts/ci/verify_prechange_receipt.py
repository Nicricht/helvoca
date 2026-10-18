#!/usr/bin/env python3
"""Fail closed unless a trusted PR comment audits the parent of the current HEAD."""

import json
import os
import re
import sys
from datetime import datetime, timezone
from urllib.request import Request, urlopen

MARKER = "FRESH_CONTEXT_EVIDENCE_RECEIPT_V2"
FIELDS = (
    "AUDITED_HEAD", "BASE_SHA", "BRANCH", "PRIOR_EVIDENCE", "NEW_DELTA",
    "AFFECTED_FILES", "QA_PLAN", "DOC_TARGET",
)
TRUSTED = frozenset({"OWNER", "MEMBER", "COLLABORATOR"})
SHA = re.compile(r"[0-9a-f]{40}\Z")
REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")


class ReceiptFailure(Exception):
    """A missing or unverified prerequisite must block this PR check."""


def _time(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


def valid_receipt(comment, parent, base, branch, head_created):
    """Check traceability; this cannot judge whether the author's claim is true."""
    if comment.get("author_association") not in TRUSTED:
        return False
    body = comment.get("body") or ""
    if not body.startswith(MARKER + "\n"):
        return False
    entries = re.findall(r"^([A-Z_]+):[ \t]*(.*)$", body, flags=re.MULTILINE)
    if len(entries) != len(FIELDS) or {key for key, _ in entries} != set(FIELDS):
        return False
    values = dict(entries)
    if (values["AUDITED_HEAD"] != parent or values["BASE_SHA"] != base
            or values["BRANCH"] != branch):
        return False
    if (not SHA.fullmatch(values["AUDITED_HEAD"])
            or not SHA.fullmatch(values["BASE_SHA"])):
        return False
    if any(len(values[key].strip()) < 16 for key in FIELDS[3:]):
        return False
    if "https://github.com/" not in values["PRIOR_EVIDENCE"]:
        return False
    try:
        created = _time(comment["created_at"])
    except (ValueError, KeyError, TypeError, AttributeError):
        return False
    return created <= _time(head_created)


def github_json(path, token):
    request = Request(
        "https://api.github.com" + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + token,
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Helvoca-Fresh-Context-Gate",
        },
    )
    with urlopen(request, timeout=15) as response:
        return json.load(response)


def verify(repo, number, head, token, fetch=github_json):
    """Check actual PR HEAD, parent, receipt timestamp and prior evidence."""
    if not REPOSITORY.fullmatch(repo) or ".." in repo or not SHA.fullmatch(head) or not token:
        raise ReceiptFailure("Missing or invalid authenticated PR verification context")
    pr = fetch(f"/repos/{repo}/pulls/{number}", token)
    if pr["head"]["sha"] != head:
        raise ReceiptFailure("PR HEAD moved; refresh the evidence audit")
    base = pr["base"]["sha"]
    branch = pr["head"]["ref"]
    commit = fetch(f"/repos/{repo}/git/commits/{head}", token)
    parents = commit["parents"]
    if len(parents) != 1:
        raise ReceiptFailure("Unsupported merge/root commit; verify the parent explicitly")
    parent = parents[0]["sha"]
    head_created = commit["committer"]["date"]
    comments = []
    for page in range(1, 21):
        batch = fetch(f"/repos/{repo}/issues/{number}/comments?per_page=100&page={page}", token)
        comments.extend(batch)
        if len(batch) < 100:
            break
    else:
        raise ReceiptFailure("Comment pagination exceeded safety bound")
    if not any(valid_receipt(c, parent, base, branch, head_created) for c in comments):
        raise ReceiptFailure("No trusted pre-change receipt for this exact parent HEAD")


def main():
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        return 0
    try:
        verify(
            os.environ["GITHUB_REPOSITORY"],
            int(os.environ["HELVOCA_PR_NUMBER"]),
            os.environ["HELVOCA_PR_HEAD_SHA"],
            os.environ["GITHUB_TOKEN"],
        )
    except (ReceiptFailure, KeyError, ValueError, OSError, AttributeError, TypeError) as error:
        print(f"FRESH_CONTEXT_RECEIPT_FAIL_CLOSED: {type(error).__name__}", file=sys.stderr)
        return 1
    print("FRESH_CONTEXT_RECEIPT_VERIFIED_FOR_EXACT_PARENT")
    return 0


if __name__ == "__main__":
    sys.exit(main())
