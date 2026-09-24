-- V62: first-class catalog media and durable WhatsApp multimedia delivery.

ALTER TABLE public.catalog_item
    ADD CONSTRAINT uq_catalog_item_id_business UNIQUE (id, business_id);

CREATE TABLE public.catalog_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_url VARCHAR(1200) NOT NULL,
    mime_type VARCHAR(120),
    caption VARCHAR(1024),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_catalog_media_item_tenant
        FOREIGN KEY (catalog_item_id, business_id)
        REFERENCES public.catalog_item(id, business_id) ON DELETE CASCADE,
    CONSTRAINT uq_catalog_media_item_url UNIQUE (business_id, catalog_item_id, media_url),
    CONSTRAINT ck_catalog_media_type CHECK (media_type IN ('IMAGE','VIDEO','DOCUMENT')),
    CONSTRAINT ck_catalog_media_url CHECK (media_url ~ '^https://'),
    CONSTRAINT ck_catalog_media_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_catalog_media_item_active
    ON public.catalog_media(business_id, catalog_item_id, active, sort_order, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.catalog_media
    TO helvoca_runtime, helvoca_system;

ALTER TABLE public.catalog_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_media FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS helvoca_tenant_isolation ON public.catalog_media;

DO $$
DECLARE
    migration_owner text := current_user;
BEGIN
    EXECUTE format(
        'CREATE POLICY helvoca_tenant_isolation ON public.catalog_media TO PUBLIC '
        || 'USING (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id()) '
        || 'WITH CHECK (current_user = %L OR current_user = ''helvoca_system'' OR business_id = public.helvoca_rls_business_id())',
        migration_owner,
        migration_owner
    );
END
$$;

ALTER TABLE public.outbound_message
    ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN media_url VARCHAR(1200),
    ADD COLUMN media_mime_type VARCHAR(120),
    ADD COLUMN media_caption VARCHAR(1024),
    ADD COLUMN catalog_item_id UUID REFERENCES public.catalog_item(id) ON DELETE SET NULL;

ALTER TABLE public.outbound_message
    DROP CONSTRAINT IF EXISTS ck_outbound_message_purpose;
ALTER TABLE public.outbound_message
    ADD CONSTRAINT ck_outbound_message_purpose CHECK (purpose IN (
        'PAYMENT_LINK','BOOKING_CONFIRMATION','MEETING_LINK','ORDER_STATUS',
        'QUOTE','REMINDER','DELIVERY_STATUS','INCIDENT_NOTICE','PRODUCT_SHOWCASE'
    ));

ALTER TABLE public.outbound_message
    ADD CONSTRAINT ck_outbound_message_content_type
        CHECK (content_type IN ('TEXT','IMAGE','VIDEO','DOCUMENT')),
    ADD CONSTRAINT ck_outbound_message_media_shape
        CHECK (
            (content_type = 'TEXT' AND media_url IS NULL)
            OR
            (content_type IN ('IMAGE','VIDEO','DOCUMENT')
                AND media_url IS NOT NULL
                AND media_url ~ '^https://')
        );

CREATE INDEX idx_outbound_message_catalog_item
    ON public.outbound_message(business_id, catalog_item_id, created_at DESC)
    WHERE catalog_item_id IS NOT NULL;

COMMENT ON TABLE public.catalog_media IS
    'Tenant-scoped public media references for catalog products/services. URLs are backend-owned and may be used for durable WhatsApp delivery.';
