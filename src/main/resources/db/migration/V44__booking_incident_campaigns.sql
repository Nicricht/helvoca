-- V44 Booking incident campaigns
-- Safe preparation only. No provider dispatch or durable outbound job is created here.

CREATE TABLE booking_incident_campaign (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    goal VARCHAR(20) NOT NULL,
    strategy VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_booking_incident_campaign_goal
        CHECK (goal IN ('INFORM', 'RESCHEDULE')),
    CONSTRAINT ck_booking_incident_campaign_strategy
        CHECK (strategy IN ('CHEAPEST', 'WHATSAPP', 'CALL')),
    CONSTRAINT ck_booking_incident_campaign_status
        CHECK (status IN ('PREPARED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX ix_booking_incident_campaign_business_created
    ON booking_incident_campaign(business_id, created_at DESC);

CREATE TABLE booking_incident_recipient (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES booking_incident_campaign(id) ON DELETE CASCADE,
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    booking_ids_json JSONB NOT NULL,
    channel_preference VARCHAR(20) NOT NULL,
    content_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_booking_incident_recipient_customer UNIQUE (campaign_id, customer_id),
    CONSTRAINT ck_booking_incident_recipient_channel
        CHECK (channel_preference IN ('CHEAPEST', 'WHATSAPP', 'CALL')),
    CONSTRAINT ck_booking_incident_recipient_status
        CHECK (status IN ('PREPARED', 'CANCELLED'))
);

CREATE INDEX ix_booking_incident_recipient_campaign
    ON booking_incident_recipient(business_id, campaign_id);


-- V40 hardens only tables that exist at its migration time. V44 installs
-- tenant RLS explicitly for these new campaign tables.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.booking_incident_campaign TO helvoca_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.booking_incident_recipient TO helvoca_runtime;

ALTER TABLE public.booking_incident_campaign ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.booking_incident_campaign FORCE ROW LEVEL SECURITY;
ALTER TABLE public.booking_incident_recipient ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.booking_incident_recipient FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.booking_incident_campaign;
DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.booking_incident_recipient;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.booking_incident_campaign TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.booking_incident_recipient TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;
