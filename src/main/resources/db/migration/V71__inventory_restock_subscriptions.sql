-- V71: opt-in restock subscriptions and a provider-neutral pending notification queue.
-- No outbound provider/job is connected by this migration.

ALTER TABLE public.inventory_product_variant
    ADD CONSTRAINT uq_inventory_product_variant_id_business UNIQUE (id, business_id);

CREATE TABLE public.inventory_restock_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    customer_id UUID,
    catalog_item_id UUID NOT NULL,
    variant_id UUID,
    preferred_channel VARCHAR(20) NOT NULL,
    contact VARCHAR(180) NOT NULL,
    normalized_contact VARCHAR(180) NOT NULL,
    consent_granted BOOLEAN NOT NULL,
    consent_granted_at TIMESTAMPTZ NOT NULL,
    consent_source VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notified_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inventory_restock_subscription_id_business UNIQUE (id, business_id),
    CONSTRAINT fk_inventory_restock_subscription_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_restock_subscription_item_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_restock_subscription_variant_tenant
        FOREIGN KEY (variant_id, business_id)
        REFERENCES public.inventory_product_variant(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_restock_subscription_channel CHECK (
        preferred_channel IN ('WHATSAPP','SMS','EMAIL')
    ),
    CONSTRAINT ck_inventory_restock_subscription_consent CHECK (consent_granted = TRUE),
    CONSTRAINT ck_inventory_restock_subscription_status CHECK (
        status IN ('ACTIVE','NOTIFIED','CANCELLED')
    ),
    CONSTRAINT ck_inventory_restock_subscription_state CHECK (
        (status = 'ACTIVE' AND notified_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'NOTIFIED' AND notified_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_restock_subscription_contact CHECK (
        length(btrim(contact)) > 0 AND length(btrim(normalized_contact)) > 0
    )
);

CREATE UNIQUE INDEX uq_inventory_restock_subscription_active
    ON public.inventory_restock_subscription (
        business_id,
        catalog_item_id,
        COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid),
        preferred_channel,
        normalized_contact
    )
    WHERE status = 'ACTIVE';

CREATE INDEX idx_inventory_restock_subscription_business_status_created
    ON public.inventory_restock_subscription (business_id, status, created_at DESC);

CREATE INDEX idx_inventory_restock_subscription_subject
    ON public.inventory_restock_subscription (
        business_id, catalog_item_id, variant_id, status, created_at
    );

CREATE TABLE public.inventory_restock_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    subscription_id UUID NOT NULL,
    customer_id UUID,
    catalog_item_id UUID NOT NULL,
    variant_id UUID,
    preferred_channel VARCHAR(20) NOT NULL,
    contact VARCHAR(180) NOT NULL,
    subject_name VARCHAR(220) NOT NULL,
    sku VARCHAR(80),
    available INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(220) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT fk_inventory_restock_notification_subscription_tenant
        FOREIGN KEY (subscription_id, business_id)
        REFERENCES public.inventory_restock_subscription(id, business_id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_restock_notification_customer_tenant
        FOREIGN KEY (customer_id, business_id)
        REFERENCES public.customer(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_restock_notification_item_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_restock_notification_variant_tenant
        FOREIGN KEY (variant_id, business_id)
        REFERENCES public.inventory_product_variant(id, business_id) ON DELETE RESTRICT,
    CONSTRAINT uq_inventory_restock_notification_subscription UNIQUE (business_id, subscription_id),
    CONSTRAINT uq_inventory_restock_notification_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT ck_inventory_restock_notification_channel CHECK (
        preferred_channel IN ('WHATSAPP','SMS','EMAIL')
    ),
    CONSTRAINT ck_inventory_restock_notification_status CHECK (
        status IN ('PENDING','CANCELLED')
    ),
    CONSTRAINT ck_inventory_restock_notification_available CHECK (available > 0),
    CONSTRAINT ck_inventory_restock_notification_state CHECK (
        (status = 'PENDING' AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX idx_inventory_restock_notification_business_status_created
    ON public.inventory_restock_notification (business_id, status, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_restock_subscription
    TO helvoca_runtime, helvoca_system;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.inventory_restock_notification
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.inventory_restock_subscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_restock_subscription FORCE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_restock_notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_restock_notification FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_restock_subscription;
DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.inventory_restock_notification;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_restock_subscription TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.inventory_restock_notification TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner, migration_owner
    );
END
$$;
