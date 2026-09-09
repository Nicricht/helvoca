INSERT INTO role(code, name) VALUES
('PLATFORM_ADMIN', 'Platform Administrator'),
('BUSINESS_ADMIN', 'Business Administrator'),
('OPERATOR', 'Human Operator')
ON CONFLICT (code) DO NOTHING;
