ALTER TABLE public.twilio_certification_demo_call_quota
    DROP CONSTRAINT IF EXISTS twilio_certification_demo_call_quota_reserved_calls_check;

ALTER TABLE public.twilio_certification_demo_call_quota
    ALTER COLUMN reserved_calls TYPE INTEGER;

ALTER TABLE public.twilio_certification_demo_call_quota
    ADD CONSTRAINT twilio_certification_demo_call_quota_reserved_calls_nonnegative
    CHECK (reserved_calls >= 0);
