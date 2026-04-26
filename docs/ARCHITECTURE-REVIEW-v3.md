# Architecture and Production Readiness Review — v3

**Scope:** Validation of v2 fixes, observability depth, idempotency robustness, and security posture.  
**Reviewer:** Senior Backend Reviewer (Staff-level assessment).

---

## 1. Overall summary

This v3 review validates that the critical **transaction poisoning** issues identified in v2 have been addressed via the `PaymentIdempotencyReplayService` (using `REQUIRES_NEW`). However, a regression in the form of a `NullPointerException` was discovered in the recovery path's tests, highlighting the complexity of manual object mapping in high-integrity flows. 

On the **operability** side, while structured logging is present, a "traceability leak" was found in the `import-service` where unique Request IDs are shared across thousands of downstream calls. The **idempotency** strategy is functional but remains coupled to file ordering rather than data content.

---

## 2. Detailed issue list

### A. Observability: Traceability Leak (High)
- **Problem:** `import-service` reuses a single `X-Request-Id` for every row-level call to `payment-service` during a single file import.
- **File:** `import-service/src/server.ts` (inside `handleUploadedFile`).
- **Why it matters:** Request IDs are intended to uniquely identify a single HTTP transaction. Sharing one ID across 10,000 payment creations makes it impossible for log aggregators (ELK/Splunk) to isolate a specific failing row's request lifecycle in the Java service.
- **Fix:** Generate a fresh `randomUUID()` for `requestId` inside the `pLimit` loop for every `resolveContract` and `createPayment` call, while keeping the `traceId` stable for the whole import.

### B. Concurrency: Fragile Idempotency Key (Medium)
- **Problem:** Idempotency keys are formatted as `import:${fileSha256}:row:${rowIndex}`.
- **File:** `import-service/src/server.ts`.
- **Why it matters:** Deduplication is currently coupled to the *position* of the row in the file. If the parsing logic changes (e.g., how empty lines or comments are handled), the `rowIndex` for the same physical payment might shift, causing a duplicate payment to be created or an incorrect collision.
- **Fix:** Generate the idempotency key using a hash of the row's core business values (contract number, date, amount, type) combined with the file hash.

### C. Correctness: Missing validation in Replay Tests (Medium)
- **Problem:** A `NullPointerException` was found in `PaymentIdempotencyReplayServiceImplTest`.
- **Why it matters:** The recovery path is the most critical part of the idempotency logic. If the mapper or the service assumes a full object graph (Contract → Client) that isn't present in the mock/test data, the "recovery" itself will fail in production with a 500 instead of a graceful replay.
- **Status:** *Fixed during this review cycle.* (Ensured `Payment` in tests is initialized with a `Contract` and `Client`).

### D. Security: Wide-Open Internal Boundaries (Medium)
- **Problem:** No authentication between `import-service` and `payment-service`.
- **Why it matters:** Any service in the network can spoof payments by calling the Java API directly. Even in a private VPC, "defense in depth" suggests at least a shared secret (API Key) or mTLS.
- **Fix:** Implement a simple `ApiKeyInterceptor` in Java and pass a configured secret from the Node service.

### E. API Consistency: Status Code Mapping (Low)
- **Problem:** `import-service` maps a 404 (Contract Not Found) from the payment API to a 422 (Unprocessable Entity).
- **File:** `import-service/src/http/errorResponse.ts`.
- **Why it matters:** While 422 is semantically "correct" for a business validation error, it diverges from the upstream's explicit 404. 
- **Fix:** Align mapping or document why the transformation occurs (e.g., "all row-level resolution failures are 422").

---

## 3. Top 5 most important problems

1. **Traceability Leak:** Shared `X-Request-Id` across multiple downstream calls.
2. **Fragile Idempotency:** Index-based keys instead of content-based keys.
3. **Internal Auth:** Missing service-to-service authentication.
4. **Rate Limit Throughput:** 120 RPM default is too low for high-volume streaming imports.
5. **Memory Management:** (New observation) `import-service` relies on `p-limit` and `highWater` batches, but if the Java service slows down, the Node process will back-pressure correctly; however, many promises stay in memory.

---

## 4. Prioritized roadmap

1. **Fix Traceability:** Ensure unique `requestId` per downstream call.
2. **Harden Idempotency:** Switch to content-hashing for keys.
3. **Service Auth:** Add API Key requirement to `payment-service`.
4. **Tune Rate Limits:** Increase capacity for internal/batch traffic.

---

## 5. Production-readiness note

The system has matured significantly. The use of `REQUIRES_NEW` for idempotency recovery is a high-quality pattern. The configuration is well-validated (Zod in Node, `@Validated` in Java), and structured logging is the default. The next level of maturity requires moving from "functional isolation" to "secure isolation" (Auth) and "granular observability" (Unique Request IDs).

---

## 6. Dimensions Checklist (Summary)

| Dimension | Status | Note |
|-----------|--------|------|
| **Correctness** | Strong | Fixed NPE in recovery path. |
| **Transaction** | Excellent | Correct use of `REQUIRES_NEW` to avoid rollback-only poisoning. |
| **Observability** | Needs Work | Shared Request ID is a significant log-correlation issue. |
| **Security** | Weak | No service-to-service auth. |
| **Performance** | Good | Streaming and batching implemented; rate-limit is the bottleneck. |
| **Idempotency** | Functional | Vulnerable to row-shuffling/parser changes. |
