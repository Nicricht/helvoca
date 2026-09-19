-- V53: onboarding business capabilities.
-- Nullable booleans preserve the distinction between "not answered yet" and an explicit no.

ALTER TABLE public.business_profile
    ADD COLUMN sells_products BOOLEAN,
    ADD COLUMN sells_services BOOLEAN,
    ADD COLUMN uses_reservations BOOLEAN;

COMMENT ON COLUMN public.business_profile.sells_products IS
    'Whether this business sells physical or digital products. NULL means onboarding has not answered yet.';
COMMENT ON COLUMN public.business_profile.sells_services IS
    'Whether this business sells or provides services. NULL means onboarding has not answered yet.';
COMMENT ON COLUMN public.business_profile.uses_reservations IS
    'Whether this business uses reservations or appointments. NULL means onboarding has not answered yet.';
