-- V55: tenant-scoped Meta WhatsApp credential references.
--
-- Secrets are never stored in PostgreSQL. Each tenant row stores only a
-- credential_ref that maps to deployment secrets:
-- HELVOCA_META_WHATSAPP_<CREDENTIAL_REF>_ACCESS_TOKEN
--
-- No existing tenant is configured or enabled by this migration.

CREATE TABLE business_meta_whatsapp_config (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    credential_ref VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_meta_whatsapp_credential_ref
        CHECK (credential_ref ~ '^[A-Z0-9_]{2,80}$')
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business_meta_whatsapp_config
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.business_meta_whatsapp_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_meta_whatsapp_config FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.business_meta_whatsapp_config;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.business_meta_whatsapp_config TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE public.business_meta_whatsapp_config IS
    'Tenant Meta WhatsApp configuration containing only non-secret credential references.';
