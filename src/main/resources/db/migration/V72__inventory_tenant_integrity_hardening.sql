-- V72: database-level tenant integrity hardening for Inventory V1.
-- RLS remains the runtime isolation layer; these composite foreign keys also
-- prevent cross-tenant product/variant references even for privileged writers.

ALTER TABLE public.inventory_product_variant
    ADD CONSTRAINT uq_inventory_variant_id_item_business
        UNIQUE (id, catalog_item_id, business_id),
    ADD CONSTRAINT uq_inventory_variant_id_item
        UNIQUE (id, catalog_item_id);

ALTER TABLE public.inventory_stock
    ADD CONSTRAINT fk_inventory_stock_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE CASCADE;

ALTER TABLE public.inventory_reservation
    ADD CONSTRAINT fk_inventory_reservation_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_inventory_reservation_variant_identity
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.inventory_movement
    ADD CONSTRAINT fk_inventory_movement_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_inventory_movement_variant_identity
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.inventory_product_variant
    ADD CONSTRAINT fk_inventory_variant_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE CASCADE;

ALTER TABLE public.inventory_alert
    ADD CONSTRAINT fk_inventory_alert_catalog_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_inventory_alert_variant_identity
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.inventory_restock_subscription
    ADD CONSTRAINT fk_inventory_restock_subscription_variant_identity
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.inventory_restock_notification
    ADD CONSTRAINT fk_inventory_restock_notification_variant_identity
        FOREIGN KEY (variant_id, catalog_item_id, business_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id, business_id)
        ON DELETE RESTRICT;

ALTER TABLE public.business_operation_item
    ADD CONSTRAINT fk_business_operation_item_variant_product
        FOREIGN KEY (variant_id, catalog_item_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id)
        ON DELETE RESTRICT;

ALTER TABLE public.business_order_line
    ADD CONSTRAINT fk_business_order_line_variant_product
        FOREIGN KEY (variant_id, catalog_item_id)
        REFERENCES public.inventory_product_variant(id, catalog_item_id)
        ON DELETE RESTRICT;
