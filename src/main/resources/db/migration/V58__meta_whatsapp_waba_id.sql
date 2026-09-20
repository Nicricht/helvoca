-- V58: tenant-scoped Meta WhatsApp Business Account identifier.
--
-- WABA IDs are external Meta identifiers, not secrets. The column is nullable so
-- existing manually configured tenants continue to work unchanged until they are
-- onboarded through Embedded Signup.

ALTER TABLE public.business_meta_whatsapp_config
    ADD COLUMN waba_id VARCHAR(80);

COMMENT ON COLUMN public.business_meta_whatsapp_config.waba_id IS
    'Meta WhatsApp Business Account identifier authorized for this Helvoca tenant.';
