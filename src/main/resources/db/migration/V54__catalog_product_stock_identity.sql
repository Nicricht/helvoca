-- V54: product stock identity foundation.
-- SKU is optional and unique per tenant when present. Inventory tracking is opt-in.
ALTER TABLE public.catalog_item
    ADD COLUMN sku VARCHAR(80),
    ADD COLUMN inventory_tracked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.catalog_item
    ADD CONSTRAINT ck_catalog_item_sku
        CHECK (sku IS NULL OR sku ~ '^[A-Z0-9][A-Z0-9._-]{0,79}$');

CREATE UNIQUE INDEX uq_catalog_item_business_sku
    ON public.catalog_item (business_id, UPPER(sku))
    WHERE sku IS NOT NULL;

COMMENT ON COLUMN public.catalog_item.sku IS
    'Optional tenant-scoped product SKU. Normalized to uppercase by the application.';
COMMENT ON COLUMN public.catalog_item.inventory_tracked IS
    'Whether inventory quantities are authoritative for this catalog item.';
