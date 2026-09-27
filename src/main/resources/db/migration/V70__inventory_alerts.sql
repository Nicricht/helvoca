-- V70: tenant-scoped inventory alert state machine.
-- Alerts are in-app notifications only. No external provider dispatch is enabled here.

CREATE TABLE inventory_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES inventory_product_variant(id) ON DELETE SET NULL,
    alert_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    subject_name VARCHAR(220) NOT NULL,
    sku VARCHAR(80),
    available INTEGER NOT NULL,
    reorder_threshold INTEGER NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_inventory_alert_type CHECK (
        alert_type IN ('LOW_STOCK','OUT_OF_STOCK','RESTOCKED')
    ),
    CONSTRAINT ck_inventory_alert_status CHECK (
        status IN ('OPEN','RESOLVED')
    ),
    CONSTRAINT ck_inventory_alert_values CHECK (
        available >= 0 AND reorder_threshold >= 0
    )
);

CREATE INDEX idx_inventory_alert_business_status_created
    ON inventory_alert (business_id, status, created_at DESC);

CREATE INDEX idx_inventory_alert_item_created
    ON inventory_alert (business_id, catalog_item_id, created_at DESC);

CREATE UNIQUE INDEX uq_inventory_alert_one_open_subject
    ON inventory_alert (
        business_id,
        catalog_item_id,
        COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE status = 'OPEN';

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_alert
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.inventory_alert ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_alert FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_alert;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_alert TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
