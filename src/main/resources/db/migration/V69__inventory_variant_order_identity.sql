-- V69: persist variant identity through orders and inventory reservations.

ALTER TABLE business_operation_item
    ADD COLUMN variant_id UUID REFERENCES inventory_product_variant(id) ON DELETE RESTRICT;

ALTER TABLE business_order_line
    ADD COLUMN variant_id UUID REFERENCES inventory_product_variant(id) ON DELETE RESTRICT;

ALTER TABLE inventory_reservation
    ADD COLUMN variant_id UUID REFERENCES inventory_product_variant(id) ON DELETE RESTRICT;

CREATE INDEX idx_business_operation_item_variant
    ON business_operation_item (operation_id, variant_id)
    WHERE variant_id IS NOT NULL;

CREATE INDEX idx_business_order_line_variant
    ON business_order_line (order_id, variant_id)
    WHERE variant_id IS NOT NULL;

CREATE INDEX idx_inventory_reservation_variant
    ON inventory_reservation (business_id, variant_id, status)
    WHERE variant_id IS NOT NULL;
