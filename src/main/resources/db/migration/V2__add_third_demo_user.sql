-- Three users let the Swagger demo fill a two-seat class and reject a third
-- booking without requiring direct database edits or a registration feature.
INSERT INTO users (email) VALUES ('carol@example.com')
ON CONFLICT (email) DO NOTHING;
