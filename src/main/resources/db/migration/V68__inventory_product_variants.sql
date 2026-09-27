-- V68: product variants with tenant-scoped stock.
-- Variants are optional. Existing product-level inventory remains valid.

CREATE TABLE inventory_product_variant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    option_values_json TEXT NOT NULL DEFAULT '{}',
    sku VARCHAR(80) NOT NULL,
    tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    on_hand INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    reorder_threshold INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inventory_variant_business_item_name
        UNIQUE (business_id, catalog_item_id, name),
    CONSTRAINT ck_inventory_variant_non_negative CHECK (
        on_hand >= 0 AND reserved >= 0 AND reorder_threshold >= 0
    ),
    CONSTRAINT ck_inventory_variant_reserved_not_above_on_hand CHECK (reserved <= on_hand)
);

CREATE UNIQUE INDEX uq_inventory_variant_business_sku
    ON inventory_product_variant (business_id, lower(sku));

CREATE INDEX idx_inventory_variant_business_item
    ON inventory_product_variant (business_id, catalog_item_id, active, name);

ALTER TABLE inventory_movement
    ADD COLUMN variant_id UUID REFERENCES inventory_product_variant(id) ON DELETE SET NULL;

CREATE INDEX idx_inventory_movement_variant_created
    ON inventory_movement (business_id, variant_id, created_at DESC)
    WHERE variant_id IS NOT NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_product_variant
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.inventory_product_variant ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_product_variant FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_product_variant;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_product_variant TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
