# Architecture and Production Readiness Review — v2

**Scope:** Deep dive into JPA transactions, polyglot consistency, and ingestion performance.  
**Reviewer:** Senior Backend Reviewer (Staff-level assessment).

---

## 1. Overall summary

While the initial review (v1) highlighted the strong structural foundation and idempotency patterns, this v2 pass focuses on **internal transaction integrity** and **cross-service efficiency**. The most significant finding is a potential **transaction poisoning** risk in the Java services' idempotency handling. Additionally, the Node.js ingestion path, while correctly streaming, suffers from an **N+1 network request pattern** that will limit throughput at scale.

---

## 2. Detailed issue list

### A. Transaction Management: Rollback-Only Risk (High)
- **Problem:** Catching `DataIntegrityViolationException` within a `@Transactional` block to perform a "re-fetch" recovery.
- **File:** `PaymentServiceImpl.java`, `ImportBatchServiceImpl.java`.
- **Why it matters:** In Spring/JPA (Hibernate), a constraint violation often marks the current transaction as **rollback-only**. Attempting to perform a subsequent `findBy...` or any DB operation in the same transaction will likely throw a `TransactionSystemException: Could not commit JPA transaction; transaction marked as rollback-only`.
- **Example:** In `PaymentServiceImpl.doCreate`, if `paymentRepository.save(payment)` fails due to a unique constraint, the catch block attempts to call `paymentRepository.findByContractIdAndIdempotencyKey`. This second call may fail or the commit at the end of the method will definitely fail.
- **Fix:** Use a separate transaction for the idempotency check or use a native query/different isolation strategy that doesn't poison the session. Alternatively, check existence *before* save (though race conditions still exist, they are less frequent).

### B. Ingestion Performance: N+1 Network Requests (Medium)
- **Problem:** Resolving contract numbers to IDs for every row.
- **File:** `import-service/src/server.ts` (inside `p-limit` loop).
- **Why it matters:** For a file with 5,000 rows for the same contract, the service makes 5,000 `GET /api/v1/contracts/by-number/...` calls. This significantly increases latency and puts unnecessary load on the Java service.
- **Fix:** Implement a simple in-memory cache (e.g., `Map<string, number>`) in the Node service scoped to the duration of a single file import to cache contract number -> ID lookups.

### C. Polyglot Consistency: Idempotency Key Format (Low)
- **Problem:** Differing conventions for idempotency key generation.
- **Why it matters:** The Node service generates keys as `import:${fileSha256}:row:${rowIndex}`. While functional, it couples the Java service's deduplication logic to the Node service's specific parsing implementation.
- **Fix:** Standardize on a UUID or a hash of the row content itself to make the idempotency key truly independent of the row index (which might change if parsing logic changes).

### D. Observability: Missing Health Indicators for Downstream (Low)
- **Problem:** `payment-service` health check doesn't verify the DB connection explicitly (relying on default Actuator), and `import-service` has no health check.
- **Why it matters:** Kubernetes or other orchestrators can't accurately determine if the service is ready to receive traffic.
- **Fix:** Add a custom health indicator in Java for Postgres readiness and a `/health` endpoint in Node.js.

---

## 3. Top 5 most important problems (Updated)

1. **Transaction Poisoning:** Risk of failed commits during idempotent replays in Java.
2. **N+1 HTTP Calls:** Lack of caching for contract resolution in the import service.
3. **Authentication:** (Carried from v1) Wide-open API boundaries.
4. **Distributed Rate Limiting:** (Carried from v1) In-memory limits won't scale.
5. **Docker/Env Parity:** The recent fix for Docker versioning highlights that the environment configuration is sensitive to local setup variations.

---

## 4. Prioritized roadmap

1. **Fix Transaction Logic:** Refactor idempotency handling in Java to avoid "rollback-only" states (e.g., use a "check-then-insert" with a small retry window or a separate transaction for the check).
2. **Add Node.js Caching:** Implement a local cache for contract lookups to boost import speed.
3. **API Security:** Implement a shared secret or JWT between services.
4. **Centralized Logging:** Ensure both services ship logs to a single sink (e.g., ELK/EFK stack).

---

## 5. Production-readiness note

The system is highly **correct** and **observable**, but **operationally fragile** under high concurrency due to the transaction rollback risk. Once the transaction handling is hardened, it will be significantly more resilient. The performance is currently "linear" with the number of rows; caching will make it "sub-linear" for common cases (many rows per contract).

---

## Final conclusion

V2 confirms that the codebase is written with high standards of readability and intent. The issues identified (Transaction poisoning and N+1 requests) are classic "Level 2" problems that appear when moving from a single-user demo to a high-concurrency production system. Addressing these will move the project from "Senior Demo" to "Staff-Level Production Ready."

---

## 6. Implemented follow-ups (repository)

| Item | Change |
|------|--------|
| **A — Rollback-only / transaction poisoning** | `PaymentIdempotencyReplayService` / `ImportBatchRaceLookupService` interfaces, implemented by `*Impl` beans, run post–constraint-violation reads in `@Transactional(REQUIRES_NEW, readOnly = true)` so work is not done in a rollback-only session. |
| **B — N+1 contract HTTP** | `import-service` uses `createContractLookup()` in `paymentClient.ts`: per–file-import cache + in-flight deduplication for concurrent first requests to the same contract number. |
| **C — Idempotency key format** | No behavior change (keys remain `import:{sha}:row:{i}` for compatibility). Documented here as a future option: content-hash or UUID if import layout changes. |
| **D — Health** | `management.health.db.enabled: true` on **payment-service** (Actuator includes DB). **import-service** exposes `GET /health` with `{ status, service }`. |
| **v1 carryovers** (auth, cluster rate limit, ELK) | Out of scope for this pass; see README roadmap. |
