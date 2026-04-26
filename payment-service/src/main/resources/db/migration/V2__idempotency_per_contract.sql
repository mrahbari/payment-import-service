-- Idempotency keys are scoped per contract: the same key may be reused on different contracts.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_idempotency_key_key;

ALTER TABLE payments
    ADD CONSTRAINT uq_payments_contract_idempotency UNIQUE (contract_id, idempotency_key);
