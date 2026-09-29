-- V78: tenant-selectable curated visual accent theme.
-- The business table is the tenant root and already has forced RLS.

ALTER TABLE public.business
    ADD COLUMN appearance_theme VARCHAR(16) NOT NULL DEFAULT 'cyan';

ALTER TABLE public.business
    ADD CONSTRAINT ck_business_appearance_theme
    CHECK (appearance_theme IN ('cyan', 'blue', 'emerald', 'violet', 'amber'));

COMMENT ON COLUMN public.business.appearance_theme IS
    'Curated RecepVoz visual accent preset. Semantic status colors are not tenant-customizable.';
