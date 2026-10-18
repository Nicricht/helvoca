"""Strict offline tests. Never connect to production or DR."""
from pathlib import Path
from unittest.mock import patch, Mock
import subprocess
import unittest

import c1_restore_global_roles_acls as d

TEST_DR_SERVICE = "11111111-2222-4333-8444-555555555555"

def fixture():
    return (b'-- PostgreSQL database dump\n' +
            b''.join(f'GRANT SELECT ON TABLE public.t{i:02d} TO helvoca_runtime;\n'.encode() for i in range(8)) +
            b'GRANT SELECT ON TABLE public.business TO helvoca_system;\n' +
            b'GRANT SELECT ON TABLE public.business TO PUBLIC;\n' +
            b'CREATE TABLE public.sentinel();\n')

class DRRoleACLTests(unittest.TestCase):
    def test_cli_found_and_fail_closed(self):
        with patch.object(d.shutil,'which',return_value='C:\\Railway\\railway.cmd'):
            self.assertEqual(d.cli(),'C:\\Railway\\railway.cmd')
        with patch.object(d.shutil,'which',return_value=None):
            with self.assertRaisesRegex(d.Blocked,'C1_RAILWAY_CLI_MISSING'):d.cli()

    def test_guard_fixed_identity_credentials_never_print(self):
        s=d.guard(d.DR_PROJECT,d.DR_ENV,TEST_DR_SERVICE)
        self.assertIn(d.DR_PROJECT,s)
        self.assertIn(TEST_DR_SERVICE,s)
        self.assertIn('test "${RAILWAY_ENVIRONMENT_ID:-}"',s)
        self.assertIn('export PGPASSWORD=',s)
        self.assertNotIn('actual-password',s)
        self.assertIn('test "${POSTGRES_DB:-}" = "railway"',s)

    def test_argv_isolated_target(self):
        with patch.object(d,'cli',return_value='C:\\Railway\\railway.cmd'):
            x=d.argv(d.DR_PROJECT,d.DR_ENV,TEST_DR_SERVICE,'SELECT')
        self.assertEqual(x[0],'C:\\Railway\\railway.cmd')
        self.assertEqual(x[2:4],['--project',d.DR_PROJECT])
        self.assertEqual(x[-1],'SELECT')

    def test_transfer_good_bytes_and_input(self):
        fake=Mock(returncode=0,stdout=b'C1_OK')
        with patch.object(d.subprocess,'run',return_value=fake) as run:
            self.assertEqual(d.transfer(['test'],input_bytes=b'REDACTED'),b'C1_OK')
        self.assertEqual(run.call_args.kwargs['input'],b'REDACTED')
        self.assertEqual(run.call_args.kwargs['stderr'],subprocess.DEVNULL)

    def test_transfer_command_failed(self):
        with patch.object(d.subprocess,'run',return_value=Mock(returncode=1,stdout=b'')):
            with self.assertRaisesRegex(d.Blocked,'C1_REMOTE_COMMAND_FAILED'):d.transfer(['test'])

    def test_transfer_too_much_stdout(self):
        with patch.object(d.subprocess,'run',return_value=Mock(returncode=0,stdout=b'abc')):
            with self.assertRaisesRegex(d.Blocked,'C1_REMOTE_OUTPUT_TOO_LARGE'):d.transfer(['test'],cap=2)

    def test_transfer_unlaunchable_or_timeout(self):
        for error in [OSError('private'),subprocess.TimeoutExpired('cmd',3)]:
            with self.subTest(error=type(error).__name__),patch.object(d.subprocess,'run',side_effect=error):
                with self.assertRaisesRegex(d.Blocked,'C1_RAILWAY_SSH_FAILED_OR_TIMEOUT'):d.transfer(['test'])

    def test_acl_allowlist_selects_only_helvoca_roles(self):
        text=fixture()+b'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT ON TABLES TO helvoca_runtime;\n'
        lines=d.extract_acl(text)
        self.assertEqual(len(lines),10)
        self.assertNotIn('CREATE TABLE','\n'.join(lines))
        self.assertFalse(any('TO PUBLIC' in s for s in lines))
        self.assertTrue(any(s.startswith('ALTER DEFAULT PRIVILEGES') for s in lines))

    def test_role_text_inside_comment_is_not_executable_acl(self):
        text=fixture()+b"COMMENT ON ROLE helvoca_runtime IS 'INERT';\n"
        filtered=d.extract_acl(text)
        self.assertFalse(any(x.startswith('COMMENT') for x in filtered))

    def test_script_main_guard_with_runpy_no_provider_calls(self):
        import runpy, sys
        with patch.object(sys,'argv',['c1_restore_global_roles_acls.py']),patch.object(d,'run'):
            with self.assertRaises(SystemExit) as context:
                runpy.run_path(str(Path(__file__).with_name('c1_restore_global_roles_acls.py')), run_name='__main__')
        self.assertEqual(context.exception.code,2)

    def test_acl_too_small_and_oversize(self):
        for s in [b'',b'-- PostgreSQL database dump']:
            with self.subTest(s=s),self.assertRaisesRegex(d.Blocked,'C1_SCHEMA_METADATA_UNEXPECTED_SIZE'):d.extract_acl(s)
        with patch.object(d,'require',wraps=d.require):
            with self.assertRaisesRegex(d.Blocked,'C1_SCHEMA_METADATA_UNEXPECTED_SIZE'):d.extract_acl(b'0'*16000001)

    def test_acl_not_utf8(self):
        s=fixture()+b'\xff'
        with self.assertRaisesRegex(d.Blocked,'C1_SCHEMA_METADATA_NOT_UTF8'):d.extract_acl(s)

    def test_acl_wrong_header(self):
        s=fixture().replace(b'PostgreSQL database dump',b'TOTALLY WRONG SQL DUMP')
        with self.assertRaisesRegex(d.Blocked,'C1_SCHEMA_DUMP_HEADER_MISSING'):d.extract_acl(s)

    def test_acl_incomplete(self):
        f=b'-- PostgreSQL database dump\n'+b'-- filler\n'*65+b'GRANT SELECT TO helvoca_runtime;\n'*5
        with self.assertRaisesRegex(d.Blocked,'C1_SOURCE_ACLS_MISSING_OR_INCOMPLETE'):d.extract_acl(f)

    def test_acl_requires_both_roles(self):
        f=b'-- PostgreSQL database dump\n'+b'GRANT SELECT ON TABLE public.x TO helvoca_runtime;\n'*12
        with self.assertRaisesRegex(d.Blocked,'C1_BOTH_ROLE_GRANTS_REQUIRED'):d.extract_acl(f)

    def test_acl_rejects_multiline_semicolon_and_nul(self):
        for addition in [b'GRANT SELECT ON TABLE public.z TO helvoca_system',b'GRANT SELECT ON TABLE public.z TO helvoca_system;\x00']:
            with self.subTest(addition=addition),self.assertRaisesRegex(d.Blocked,'C1_UNSAFE_OR_MULTILINE_ACL'):
                d.extract_acl(fixture()+addition+b'\n')

    def test_acl_rejects_stacked_sql_in_grant_line(self):
        injected = (fixture() +
            b"GRANT SELECT ON TABLE public.x TO helvoca_runtime; DROP TABLE public.business;\n")
        with self.assertRaisesRegex(d.Blocked, 'C1_UNSAFE_OR_MULTILINE_ACL'):
            d.extract_acl(injected)

    def test_dr_target_must_be_real_uuid_not_production_or_missing(self):
        for target in ('', 'not-a-uuid', d.PROD_SERVICE, '00000000-0000-0000-0000-000000000000'):
            with self.subTest(target=target), patch.object(d, 'run') as remote:
                self.assertEqual(d.main(['--execute', '--dr-service', target]), 2)
                remote.assert_not_called()

    def test_acl_blocks_history_exposure(self):
        with self.assertRaisesRegex(d.Blocked,'C1_FLYWAY_HISTORY_EXPOSURE_BLOCKED'):
            d.extract_acl(fixture()+b'GRANT SELECT ON TABLE public.flyway_schema_history TO helvoca_runtime;\n')

    def test_compose_sql_atomic_role_no_login_and_no_row_data(self):
        acl=d.extract_acl(fixture())
        src=d.compose_acl_sql(acl).decode()
        self.assertTrue(src.startswith('BEGIN;'))
        self.assertTrue(src.endswith('COMMIT;\n'))
        self.assertIn('NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',src)
        self.assertIn('BUSINESS_RLS_NOT_FORCED',src)
        self.assertIn('GRANT helvoca_runtime, helvoca_system TO CURRENT_USER;',src)
        self.assertNotIn('INSERT INTO public.business',src)

    def test_run_happy_path_only_source_schema_is_read(self):
        with patch.object(d,'transfer',side_effect=[b'C1_SOURCE_ONLY_READ_VERIFIED\n',b'C1_DR_TARGET_VERIFIED\n',fixture(),b'C1_DR_TARGET_VERIFIED\n',b'',b'0\n']) as f, patch.object(d,'argv',side_effect=lambda p,e,s,c:[p,e,s,c]):
            self.assertEqual(d.run(TEST_DR_SERVICE),0)
        args=f.call_args_list
        self.assertEqual(len(args),6)
        self.assertEqual(args[0].args[0][0],d.PROD_PROJECT)
        self.assertIn('pg_dump --schema-only --no-owner',args[2].args[0][-1])
        self.assertEqual(args[2].args[0][0],d.PROD_PROJECT)
        self.assertIn('exec psql',args[4].args[0][-1])
        self.assertEqual(args[4].args[0][0],d.DR_PROJECT)
        self.assertNotIn('CREATE TABLE',args[4].kwargs['input_bytes'].decode())

    def test_run_source_guard_failure_before_any_schema_access(self):
        with patch.object(d,'transfer',return_value=b'WRONG') as f,patch.object(d,'argv',side_effect=lambda p,e,s,c:[p,e,s,c]):
            with self.assertRaisesRegex(d.Blocked,'C1_SOURCE_IDENTITY_MISMATCH'):d.run(TEST_DR_SERVICE)
        self.assertEqual(f.call_count,1)

    def test_run_dr_guard_failure_before_schema_access(self):
        with patch.object(d,'transfer',side_effect=[b'C1_SOURCE_ONLY_READ_VERIFIED',b'WRONG']) as f,patch.object(d,'argv',side_effect=lambda p,e,s,c:[p,e,s,c]):
            with self.assertRaisesRegex(d.Blocked,'C1_DR_IDENTITY_MISMATCH'):d.run(TEST_DR_SERVICE)
        self.assertEqual(f.call_count,2)

    def test_run_target_recheck_failure_prevents_write(self):
        with patch.object(d,'transfer',side_effect=[b'C1_SOURCE_ONLY_READ_VERIFIED',b'C1_DR_TARGET_VERIFIED',fixture(),b'WRONG']) as f,patch.object(d,'argv',side_effect=lambda p,e,s,c:[p,e,s,c]):
            with self.assertRaisesRegex(d.Blocked,'C1_DR_CHANGED_BEFORE_ACL_RESTORE'):d.run(TEST_DR_SERVICE)
        self.assertEqual(f.call_count,4)

    def test_run_tenant_no_scope_must_be_zero(self):
        with patch.object(d,'transfer',side_effect=[b'C1_SOURCE_ONLY_READ_VERIFIED',b'C1_DR_TARGET_VERIFIED',fixture(),b'C1_DR_TARGET_VERIFIED',b'',b'1\n']),patch.object(d,'argv',side_effect=lambda p,e,s,c:[p,e,s,c]):
            with self.assertRaisesRegex(d.Blocked,'C1_DR_TENANT_NO_SCOPE_ACCESS_BLOCKED'):d.run(TEST_DR_SERVICE)

    def test_explicit_flag_required(self):
        with patch.object(d,'run') as run:
            self.assertEqual(d.main([]),2)
        run.assert_not_called()

    def test_main_returns_success_only_on_work(self):
        with patch.object(d,'run',return_value=0) as run:
            self.assertEqual(d.main(['--execute','--dr-service',TEST_DR_SERVICE]),0)
        run.assert_called_once_with(TEST_DR_SERVICE)

    def test_main_failure_redacts_private_error(self):
        with patch.object(d,'run',side_effect=OSError('SUPER_SECRET')):
            self.assertEqual(d.main(['--execute','--dr-service',TEST_DR_SERVICE]),2)
        with patch.object(d,'run',side_effect=d.Blocked('SAFE_CODE')):
            self.assertEqual(d.main(['--execute','--dr-service',TEST_DR_SERVICE]),2)
        with patch.object(d,'run',side_effect=ValueError('PRIVATE')):
            self.assertEqual(d.main(['--execute','--dr-service',TEST_DR_SERVICE]),2)

if __name__=='__main__':unittest.main()
