-- V52: public/business configuration extension for the tenant root.
-- Business remains the tenant identity. This 1:1 table contains receptionist-facing
-- profile data and is protected by the same fail-closed tenant RLS contract.

CREATE TABLE business_profile (
    business_id UUID PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    preset_key VARCHAR(60),
    public_description TEXT,
    public_phone VARCHAR(32),
    public_email VARCHAR(180),
    website_url VARCHAR(500),
    address_line VARCHAR(250),
    commune VARCHAR(120),
    city VARCHAR(120),
    region VARCHAR(120),
    country_code VARCHAR(2),
    default_currency VARCHAR(3) NOT NULL DEFAULT 'CLP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_business_profile_preset
        CHECK (preset_key IS NULL OR preset_key ~ '^[a-z0-9_-]{1,60}$'),
    CONSTRAINT ck_business_profile_phone
        CHECK (public_phone IS NULL OR public_phone ~ '^\\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_business_profile_country
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_business_profile_currency
        CHECK (default_currency ~ '^[A-Z]{3}$')
);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business_profile
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.business_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_profile FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.business_profile;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.business_profile TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

COMMENT ON TABLE public.business_profile IS
    'Tenant-scoped public business profile used by owner-facing configuration and conversational business context.';
