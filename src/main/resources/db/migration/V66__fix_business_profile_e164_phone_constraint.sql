-- V66: align the database phone constraint with the API's E.164 validation.
-- The original PostgreSQL regex used a Java-style escaped plus sign and rejected
-- valid values such as +56975856664. Use a character class so PostgreSQL treats
-- the plus sign literally and preserves the intended E.164 contract.

ALTER TABLE public.business_profile
    DROP CONSTRAINT IF EXISTS ck_business_profile_phone;

ALTER TABLE public.business_profile
    ADD CONSTRAINT ck_business_profile_phone
    CHECK (public_phone IS NULL OR public_phone ~ '^[+][1-9][0-9]{7,14}$');
