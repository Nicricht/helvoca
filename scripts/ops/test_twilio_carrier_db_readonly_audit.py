"""Stage D Twilio GET-only and Railway read-only comparator unit tests, no network."""
from __future__ import annotations

import base64
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from unittest.mock import patch

FILE = Path(__file__).with_name("twilio_carrier_db_readonly_audit.py")
SPEC = importlib.util.spec_from_file_location("twilio_db_d", FILE)
tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tool)

PARENT = "AC" + "a" * 32
CHILD = "AC" + "b" * 32
SID = "PN" + "c" * 32
PHONE = "+14355652512"
TENANT = "044a5d73-2df7-4e77-849f-8639d05ac30b"
BASE = "https://helvoca-api-production.up.railway.app"


def row(**replacements):
    return {"business_id": TENANT, "provider": "TWILIO", "active": True,
            "external_id": SID, "twilio_account_sid": PARENT,
            "twilio_binding_source": "VERIFIED_EXISTING",
            "voice_verified_at": "2026-10-13T00:00:00Z", **replacements}


def db(**replacements):
    return {"read_only": True, "major": 18, "last_flyway_version": "103",
            "provenance_column_count": 3, "rows": [row(**replacements)]}


def twilio_number(**replacement):
    result = {"sid": SID, "account_sid": PARENT, "phone_number": PHONE,
              "status": "in-use", "voice_receive_mode": "voice",
              "capabilities": {"voice": True},
              "voice_url": BASE + "/webhooks/v1/twilio/voice",
              "voice_method": "POST",
              "status_callback": BASE + "/webhooks/v1/twilio/status",
              "status_callback_method": "POST",
              "voice_application_sid": None, "trunk_sid": None}
    return {**result, **replacement}


def environment(**replace):
    return {"TWILIO_ACCOUNT_SID": PARENT, "TWILIO_AUTH_TOKEN": "not-a-real-token",
            "TWILIO_PUBLIC_BASE_URL": BASE,
            "HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED": "false",
            **replace}


def carrier(**changes):
    return {"owner": PARENT, "phone_sid": SID, "owner_kind": "PARENT",
            "reasons": [], **changes}


class StageDTwilioCarrierDBAuditTest(unittest.TestCase):
    def test_sql_is_read_only_and_does_not_depend_on_v100_schema(self):
        sql = tool.phone_sql(PHONE)
        self.assertTrue(sql.startswith("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;"))
        self.assertTrue(sql.endswith("COMMIT;"))
        self.assertIn("current_setting('transaction_read_only')", sql)
        self.assertIn("to_jsonb(p)->>'twilio_account_sid'", sql)
        for forbidden in ("INSERT ", "UPDATE ", "DELETE ", "TRUNCATE ",
                          "CREATE ", "DROP ", "ALTER ", "COPY "):
            self.assertNotIn(forbidden, sql.upper())

    def test_deny_sql_injection_e164(self):
        for invalid in ("+123';DROP TABLE phone_number;--", "14355652512",
                        "++14355652512", "+14355652512\n", "+120"):
            with self.subTest(phone=invalid):
                with self.assertRaises(tool.AuditError):
                    tool.phone_sql(invalid)

    def test_db_ssh_invocation_only_target_pg18_and_no_stdout_log(self):
        wrapped = {"read_only": True, "major": 18,
                   "last_flyway_version": "103", "provenance_column_count": 3,
                   "rows": [row()]}
        raw = ("\n" + tool.PG_MARKER + json.dumps(wrapped) + "\n").encode()
        fake = subprocess.CompletedProcess([], 0, raw, b"SENSITIVE")
        with patch.object(tool.subprocess, "run", return_value=fake) as run:
            result = tool.db_query("railway", PHONE)
        self.assertEqual(result, wrapped)
        cmd = run.call_args.args[0]
        self.assertEqual(["railway", "ssh", "--project", tool.PROJECT], cmd[:4])
        self.assertEqual(tool.PG_SERVICE, cmd[cmd.index("--service")+1])
        self.assertIn("base64 -d", cmd[-1])
        encoded = cmd[-1].split("printf %s ",1)[1].split(" | base64 -d",1)[0]
        self.assertEqual(tool.phone_sql(PHONE), base64.b64decode(encoded).decode())
        self.assertIs(run.call_args.kwargs["stderr"], subprocess.DEVNULL)
        self.assertEqual(b"", run.call_args.kwargs["input"])

    def test_db_invalid_or_untrusted_reply_rejected(self):
        for raw in (b"", b"junk\n", b"{}\n",
                    (tool.PG_MARKER + '{"read_only":false,"major":18,"rows":[]}' ).encode(),
                    (tool.PG_MARKER + '{"read_only":true,"major":16,"rows":[]}' ).encode(),
                    b"X" * 25000):
            with self.subTest(raw=raw[:10]):
                with patch.object(tool.subprocess, "run",return_value=
                        subprocess.CompletedProcess([],0,raw,b"")):
                    with self.assertRaises(tool.AuditError):
                        tool.db_query("railway", PHONE)

    def test_credentials_not_in_get_url_and_get_only_method(self):
        class FakeResponse:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def geturl(self): return tool.ROOT + "/2010-04-01/Accounts/" + PARENT + ".json"
            def read(self, n): return b'{"status":"active"}'
        class FakeOpener:
            def open(self, request, timeout):
                self.request = request
                return FakeResponse()
        opener=FakeOpener()
        with patch.object(tool, "build_opener", return_value=opener):
            value=tool.twilio_get("/2010-04-01/Accounts/"+PARENT+".json",PARENT,"fake-token")
        self.assertEqual("active",value["status"])
        request=opener.request
        self.assertEqual("GET",request.get_method())
        self.assertTrue(request.full_url.startswith("https://api.twilio.com/"))
        self.assertNotIn("fake-token",request.full_url)

    def test_get_only_restricts_host_and_resource_path(self):
        for p in ("https://attacker.test", "//attacker.test",
                  "/2010-04-01/Calls.json", "/2010-04-01/Accounts/../../evil"):
            with self.subTest(path=p):
                with self.assertRaises(tool.AuditError):
                    tool.twilio_get(p,PARENT,"fake-token")

    def test_carrier_parent_ownership_list_account_and_detail(self):
        calls=[]
        def fake(path, parent, token):
            calls.append(path)
            if path.endswith("/IncomingPhoneNumbers.json?PhoneNumber=%2B14355652512&PageSize=20"):
                return {"incoming_phone_numbers":[twilio_number()],
                        "next_page_uri":None}
            if path.endswith("/Accounts/"+PARENT+".json"):
                return {"sid":PARENT,"status":"active"}
            if path.endswith("/IncomingPhoneNumbers/"+SID+".json"):
                return twilio_number()
            raise AssertionError("Unexpected GET")
        result=tool.carrier_observe(PHONE,environment(),fake)
        self.assertEqual("PARENT",result["owner_kind"])
        self.assertEqual([],result["reasons"])
        self.assertEqual(3,len(calls))

    def test_carrier_child_validated_under_parent_auth(self):
        calls=[]
        def fake(path,parent,token):
            calls.append((path,parent))
            if path.endswith("IncomingPhoneNumbers.json?PhoneNumber=%2B14355652512&PageSize=20"):
                return {"incoming_phone_numbers": [] if "/"+PARENT+"/" in path
                        else [{**twilio_number(), "account_sid":CHILD}],
                        "next_page_uri":None}
            if path.endswith("/Accounts/"+CHILD+".json"):
                return {"sid":CHILD,"status":"active","owner_account_sid":PARENT}
            if path.endswith("/IncomingPhoneNumbers/"+SID+".json"):
                return {**twilio_number(),"account_sid":CHILD}
            raise AssertionError(path)
        result=tool.carrier_observe(PHONE,environment(
            HELVOCA_TWILIO_SUBACCOUNT_SID=CHILD),fake)
        self.assertEqual("SUBACCOUNT",result["owner_kind"])
        self.assertEqual(CHILD,result["owner"])
        self.assertTrue(all(parent==PARENT for _,parent in calls))

    def test_carrier_two_owners_or_no_owners_are_not_verified(self):
        def pages(n):
            def fake(path,parent,token):
                return {"incoming_phone_numbers":[twilio_number()] if n else [],
                        "next_page_uri":None}
            return fake
        with self.assertRaisesRegex(tool.AuditError,"TWILIO_NOT_EXACTLY_ONE_OWNED_NUMBER"):
            tool.carrier_observe(PHONE,environment(HELVOCA_TWILIO_SUBACCOUNT_SID=CHILD),
                                  pages(True))
        with self.assertRaisesRegex(tool.AuditError,"TWILIO_NOT_EXACTLY_ONE_OWNED_NUMBER"):
            tool.carrier_observe(PHONE,environment(),pages(False))

    def test_missing_twilio_pagination_marker_fails_closed(self):
        def incomplete(path, parent, token):
            return {"incoming_phone_numbers": [twilio_number()]}
        with self.assertRaisesRegex(tool.AuditError,
                                    "TWILIO_LIST_AMBIGUOUS_OR_INCOMPLETE"):
            tool.carrier_observe(PHONE, environment(), incomplete)

    def test_wrong_webhook_and_voice_capability_are_blockers(self):
        def fake(path,parent,token):
            if "IncomingPhoneNumbers.json" in path:
                return {"incoming_phone_numbers":[twilio_number(
                    voice_url="https://foreign.example/voice",
                    capabilities={"voice":False})],"next_page_uri":None}
            if path.endswith("/Accounts/"+PARENT+".json"):
                return {"sid":PARENT,"status":"active"}
            return twilio_number(voice_url="https://foreign.example/voice",
                                 capabilities={"voice":False})
        reasons=tool.carrier_observe(PHONE,environment(),fake)["reasons"]
        self.assertIn("TWILIO_WEBHOOK_MISMATCH",reasons)
        self.assertIn("TWILIO_VOICE_UNAVAILABLE",reasons)

    def test_compare_perfect_proof_only_checks_metadata_not_live_call(self):
        self.assertEqual([],tool.compare(PHONE,TENANT,db(),carrier(),environment()))

    def test_missing_v100_columns_are_not_auto_fixed(self):
        reasons=tool.compare(PHONE,TENANT,db(
            twilio_account_sid=None,twilio_binding_source=None,
            voice_verified_at=None),carrier(),environment())
        self.assertIn("DB_ACCOUNT_SID_MISSING_OR_MISMATCH",reasons)
        self.assertIn("DB_PROVENANCE_UNVERIFIED",reasons)
        self.assertIn("DB_VOICE_TIMESTAMP_MISSING",reasons)

    def test_mismatch_sid_tenant_and_extra_rows_block(self):
        reasons=tool.compare(PHONE,TENANT,db(external_id="PN"+"d"*32,
                                              business_id=str("0"*8+"-0000-4000-8000-000000000000")),
                             carrier(),environment())
        self.assertIn("DB_PHONE_SID_MISMATCH",reasons)
        self.assertIn("DB_TENANT_MISMATCH",reasons)
        extra=db();extra["rows"].append(row())
        self.assertIn("DB_NUMBER_NOT_UNIQUE_OR_MISSING",
                      tool.compare(PHONE,TENANT,extra,carrier(),environment()))

    def test_kill_switch_true_blocks_even_if_numbers_match(self):
        reasons=tool.compare(PHONE,TENANT,db(),carrier(),environment(
            HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED="true"))
        self.assertIn("LIVE_EFFECTS_SWITCH_ENABLED",reasons)

    def test_reporting_never_serializes_sids_or_credentials(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.dict(os.environ,{"LOCALAPPDATA":root}):
                result=tool.private_report({"status":"BLOCKED","blockers":["SID_MISMATCH"]})
                self.assertEqual("private-evidence",result.parent.name)
                text=result.read_text()
                self.assertNotIn(PARENT,text)
                self.assertNotIn(SID,text)
                self.assertNotIn("fake-token",text)

    def test_main_missing_railway_run_context_aborts_without_get(self):
        with patch.dict(os.environ,{"TWILIO_ACCOUNT_SID":"","TWILIO_AUTH_TOKEN":""}), \
                patch("builtins.print") as print_mock:
            result=tool.main(["--phone",PHONE,"--tenant",TENANT])
            self.assertEqual(2,result)
            self.assertIn("RAILWAY_RUN_BACKEND_VARIABLES_NOT_INJECTED",
                          str(print_mock.call_args_list))

    def test_main_simulation_uses_get_read_only_and_no_sid_output(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.dict(os.environ,{**environment(),"LOCALAPPDATA":root}), \
                    patch.object(tool.shutil,"which",return_value="railway"), \
                    patch.object(tool,"db_query",return_value=db()), \
                    patch.object(tool,"carrier_observe",return_value=carrier()), \
                    patch("builtins.print") as printer:
                self.assertEqual(0,tool.main(["--phone",PHONE,"--tenant",TENANT]))
                prints="\n".join(str(x) for entry in printer.call_args_list for x in entry.args)
                self.assertIn("STAGE_D_TWILIO_DB_READONLY_MATCH",prints)
                self.assertNotIn(PARENT,prints)
                self.assertNotIn(SID,prints)
                self.assertNotIn(PHONE,prints)
                report=list(Path(root).rglob("*.private.json"))[0].read_text()
                self.assertNotIn(PARENT,report)
                self.assertNotIn(SID,report)
                self.assertIn('"commercial_go": false',report.lower())


    def test_read_only_sql_labels_database_version_and_column_count(self):
        sql = tool.phone_sql(PHONE)
        self.assertIn("'last_flyway_version'", sql)
        self.assertIn("'provenance_column_count'", sql)
        self.assertIn("FROM public.flyway_schema_history", sql)
        self.assertIn("information_schema.columns", sql)
        self.assertIn("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY", sql)
        self.assertIn("current_setting('transaction_read_only')", sql)
        for forbidden in ("INSERT INTO ", "UPDATE phone_number", "ALTER TABLE ",
                          "DROP TABLE ", "CREATE TABLE ", "COPY "):
            self.assertNotIn(forbidden, sql.upper())

    def test_v99_columns_absent_has_one_truthful_schema_blocker(self):
        data = db(twilio_account_sid=None, twilio_binding_source=None,
                  voice_verified_at=None)
        data["last_flyway_version"] = "99"
        data["provenance_column_count"] = 0
        reasons = tool.compare(PHONE, TENANT, data, carrier(), environment())
        self.assertEqual(["DB_SCHEMA_V100_V103_NOT_DEPLOYED"], reasons)
        self.assertNotIn("DB_ACCOUNT_SID_MISSING_OR_MISMATCH", reasons)
        self.assertNotIn("DB_PROVENANCE_UNVERIFIED", reasons)
        self.assertNotIn("DB_VOICE_TIMESTAMP_MISSING", reasons)

    def test_v99_migration_not_present_cannot_make_go_even_when_row_sid_matches(self):
        data = db()
        data["last_flyway_version"] = "99"
        data["provenance_column_count"] = 0
        reasons = tool.compare(PHONE, TENANT, data, carrier(), environment())
        self.assertIn("DB_SCHEMA_V100_V103_NOT_DEPLOYED", reasons)

    def test_v99_does_not_hide_wrong_tenant_or_phone_sid(self):
        data = db(external_id="PN" + "d" * 32, business_id="00000000-0000-4000-8000-000000000000")
        data["last_flyway_version"] = "99"
        data["provenance_column_count"] = 0
        reasons = tool.compare(PHONE, TENANT, data, carrier(), environment())
        self.assertIn("DB_PHONE_SID_MISMATCH", reasons)
        self.assertIn("DB_TENANT_MISMATCH", reasons)
        self.assertIn("DB_SCHEMA_V100_V103_NOT_DEPLOYED", reasons)

    def test_partial_schema_and_legacy_version_with_new_columns_stop(self):
        for version, count in (("99", 1), ("99", 3), ("100", 1),
                               ("101", 0), ("103", 2), ("098", 3),
                               ("bad", 3), (None, 3), ("103", "3")):
            with self.subTest(version=version, count=count):
                data = db()
                data["last_flyway_version"] = version
                data["provenance_column_count"] = count
                reasons = tool.compare(PHONE, TENANT, data, carrier(), environment())
                self.assertIn("DB_PROVENANCE_SCHEMA_INCONSISTENT", reasons)

    def test_v100_plus_with_null_real_data_still_reports_three_distinct_blockers(self):
        for version in ("100", "101", "102", "103", "104"):
            with self.subTest(version=version):
                data = db(twilio_account_sid=None, twilio_binding_source=None,
                          voice_verified_at=None)
                data["last_flyway_version"] = version
                reasons = tool.compare(PHONE, TENANT, data, carrier(), environment())
                self.assertIn("DB_ACCOUNT_SID_MISSING_OR_MISMATCH", reasons)
                self.assertIn("DB_PROVENANCE_UNVERIFIED", reasons)
                self.assertIn("DB_VOICE_TIMESTAMP_MISSING", reasons)

    def test_schema_absence_never_overrides_upstream_carrier_errors(self):
        data = db()
        data["last_flyway_version"] = "99"
        data["provenance_column_count"] = 0
        reasons = tool.compare(
            PHONE, TENANT, data,
            carrier(reasons=["TWILIO_WEBHOOK_MISMATCH"]), environment())
        self.assertIn("TWILIO_WEBHOOK_MISMATCH", reasons)
        self.assertIn("DB_SCHEMA_V100_V103_NOT_DEPLOYED", reasons)

    def test_db_response_missing_schema_metadata_is_untrusted(self):
        valid = db()
        for key, value in (("last_flyway_version", None),
                           ("last_flyway_version", "99;DROP"),
                           ("last_flyway_version", 99),
                           ("provenance_column_count", True),
                           ("provenance_column_count", 4),
                           ("provenance_column_count", "3")):
            with self.subTest(key=key, value=value):
                data = dict(valid)
                data[key] = value
                raw = (tool.PG_MARKER + json.dumps(data) + "\n").encode()
                with patch.object(tool.subprocess, "run", return_value=
                                  subprocess.CompletedProcess([], 0, raw, b"")):
                    with self.assertRaises(tool.AuditError):
                        tool.db_query("railway", PHONE)



if __name__=="__main__":
    unittest.main()
