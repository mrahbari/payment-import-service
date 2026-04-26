CREATE TABLE clients (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE contracts (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients (id),
    contract_number VARCHAR(64) NOT NULL UNIQUE
);

CREATE INDEX idx_contracts_client ON contracts (client_id);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES contracts (id),
    amount NUMERIC(19, 4) NOT NULL,
    type VARCHAR(16) NOT NULL,
    payment_date DATE NOT NULL,
    idempotency_key VARCHAR(190) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payments_type CHECK (type IN ('INCOMING', 'OUTGOING'))
);

CREATE INDEX idx_payments_contract ON payments (contract_id);

CREATE TABLE import_batch (
    id BIGSERIAL PRIMARY KEY,
    file_sha256 VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    rows_accepted INT NOT NULL DEFAULT 0,
    rows_rejected INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
