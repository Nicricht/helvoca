-- V67: tenant-scoped inventory foundation.
-- This migration is intentionally isolated on feat/inventory-v1 until the module is certified.

CREATE TABLE inventory_stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE CASCADE,
    sku VARCHAR(80),
    tracking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    on_hand INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    reorder_threshold INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inventory_stock_business_item UNIQUE (business_id, catalog_item_id),
    CONSTRAINT ck_inventory_stock_non_negative CHECK (
        on_hand >= 0 AND reserved >= 0 AND reorder_threshold >= 0
    ),
    CONSTRAINT ck_inventory_stock_reserved_not_above_on_hand CHECK (reserved <= on_hand)
);

CREATE UNIQUE INDEX uq_inventory_stock_business_sku
    ON inventory_stock (business_id, lower(sku))
    WHERE sku IS NOT NULL;

CREATE INDEX idx_inventory_stock_business_updated
    ON inventory_stock (business_id, updated_at DESC);

CREATE TABLE inventory_reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reference_type VARCHAR(40),
    reference_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_inventory_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservation_status CHECK (
        status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')
    )
);

CREATE INDEX idx_inventory_reservation_business_status_expiry
    ON inventory_reservation (business_id, status, expires_at);
CREATE INDEX idx_inventory_reservation_reference
    ON inventory_reservation (business_id, reference_type, reference_id);

CREATE TABLE inventory_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE CASCADE,
    movement_type VARCHAR(30) NOT NULL,
    quantity_delta INTEGER NOT NULL DEFAULT 0,
    reserved_delta INTEGER NOT NULL DEFAULT 0,
    on_hand_after INTEGER NOT NULL,
    reserved_after INTEGER NOT NULL,
    reference_type VARCHAR(40),
    reference_id UUID,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_inventory_movement_type CHECK (
        movement_type IN ('CONFIGURE','ADJUSTMENT','RESERVATION','RELEASE','CONSUMPTION')
    ),
    CONSTRAINT ck_inventory_movement_after_non_negative CHECK (
        on_hand_after >= 0 AND reserved_after >= 0
    )
);

CREATE INDEX idx_inventory_movement_business_created
    ON inventory_movement (business_id, created_at DESC);
CREATE INDEX idx_inventory_movement_item_created
    ON inventory_movement (business_id, catalog_item_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_stock
    TO helvoca_runtime, helvoca_system;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_reservation
    TO helvoca_runtime, helvoca_system;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_movement
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.inventory_stock ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_stock FORCE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_reservation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_reservation FORCE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_movement FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_stock;
DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_reservation;
DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_movement;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_stock TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_reservation TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_movement TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
