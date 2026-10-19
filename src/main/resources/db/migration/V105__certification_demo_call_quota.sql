CREATE TABLE public.twilio_certification_demo_call_quota (
    business_id UUID PRIMARY KEY REFERENCES public.business(id),
    reserved_calls SMALLINT NOT NULL DEFAULT 0
        CHECK (reserved_calls BETWEEN 0 AND 2)
);

REVOKE ALL ON TABLE public.twilio_certification_demo_call_quota FROM PUBLIC, helvoca_runtime;
GRANT SELECT, INSERT, UPDATE ON TABLE public.twilio_certification_demo_call_quota TO helvoca_system;
ALTER TABLE public.twilio_certification_demo_call_quota ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.twilio_certification_demo_call_quota FORCE ROW LEVEL SECURITY;
CREATE POLICY helvoca_system_certification_demo_quota
    ON public.twilio_certification_demo_call_quota
    FOR ALL TO helvoca_system USING (true) WITH CHECK (true);
