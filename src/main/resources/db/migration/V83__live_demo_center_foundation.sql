-- V83 Live Demo Center foundation
-- Separates commercial lifecycle from operational active/suspended status and
-- adds platform-owned reusable demo profiles. No provider credentials belong here.

ALTER TABLE public.business
    ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE public.business
    ADD CONSTRAINT ck_business_mode
        CHECK (mode IN ('DEMO', 'PILOT', 'CUSTOMER'));

CREATE TABLE public.demo_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(150) NOT NULL,
    business_name VARCHAR(150) NOT NULL,
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Santiago',
    language VARCHAR(10) NOT NULL DEFAULT 'es',
    catalog_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    hours_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    knowledge_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    greeting TEXT NOT NULL,
    instructions TEXT,
    capabilities_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    presenter_notes TEXT,
    source_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_demo_profile_updated
    ON public.demo_profile(updated_at DESC, id);

-- demo_profile is platform-owned global configuration. Tenant runtime users
-- must not be able to enumerate or mutate it.
REVOKE ALL ON TABLE public.demo_profile FROM PUBLIC, helvoca_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demo_profile TO helvoca_system;

COMMENT ON COLUMN public.business.mode IS
    'Commercial lifecycle: DEMO, PILOT or CUSTOMER. Independent of ACTIVE/SUSPENDED status.';
COMMENT ON TABLE public.demo_profile IS
    'Platform-owned approved presentation configuration. Never stores provider credentials or live customer operations.';
