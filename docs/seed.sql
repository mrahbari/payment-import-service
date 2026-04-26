-- Run after Flyway migrations. Idempotent: safe to run when demo rows already exist.
-- ./scripts/run-all.sh applies this when GET .../contracts/by-number/CNT-1001 is not 200
-- (with retries; avoid duplicate-key errors on races or manual re-runs).

INSERT INTO clients (id, name) VALUES (1, 'Demo Corp') ON CONFLICT (id) DO NOTHING;

INSERT INTO contracts (client_id, contract_number)
VALUES (1, 'CNT-1001') ON CONFLICT (contract_number) DO NOTHING;

-- Keep SERIALs past MAX(id) so the next JPA insert does not clash with explicit id=1.
SELECT setval(pg_get_serial_sequence('clients', 'id'), (SELECT COALESCE(MAX(id), 1) FROM clients));
SELECT setval(pg_get_serial_sequence('contracts', 'id'), (SELECT COALESCE(MAX(id), 1) FROM contracts));
