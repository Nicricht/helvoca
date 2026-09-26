CREATE TABLE pilot_activation_confirmation (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    prices_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    faq_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    policies_approved BOOLEAN NOT NULL DEFAULT FALSE,
    agent_instructions_approved BOOLEAN NOT NULL DEFAULT FALSE,
    pilot_scope_approved BOOLEAN NOT NULL DEFAULT FALSE,
    conversation_test_completed BOOLEAN NOT NULL DEFAULT FALSE,
    mutation_tests_completed BOOLEAN NOT NULL DEFAULT FALSE,
    human_handoff_tested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.pilot_activation_confirmation
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.pilot_activation_confirmation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pilot_activation_confirmation FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.pilot_activation_confirmation;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.pilot_activation_confirmation TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;
