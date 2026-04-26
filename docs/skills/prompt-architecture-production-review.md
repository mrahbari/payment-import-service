# Prompt: Architecture and production readiness review

Use this document as the **system or user prompt** when you want a **deep, production-grade** review of a backend system—typically Java/Spring plus companion services (e.g. Node)—with emphasis on design, data integrity, concurrency, and operability.

---

## Goal

Identify design flaws, hidden bugs, scalability risks, and violations of clean code and modern backend practice. Think like a strict staff-level reviewer: prioritize **correctness, boundaries, and operability** over style nitpicks.

This checklist is aligned with the **technical** dimensions often used in a scored review (correctness, architecture, testing, performance, errors, security, style, documentation). For a **fixed 9-category 1–5 scorecard**, totals, and the matching **output sections** in `docs/`, use [`../rigorous-code-review-score.md`](../rigorous-code-review-score.md).

---

## Persisting review outputs (optional)

If the user wants the review saved in-repo, write the full result under **`docs/ARCHITECTURE-REVIEW-v1.md`**, **`docs/ARCHITECTURE-REVIEW-v2.md`**, etc. Bump the version for each new pass or material codebase change; do **not** overwrite prior versions.

---

## Instructions for the assistant

1. **Read before judging**: trace main flows (API → service → persistence; cross-service calls if present).
2. **Be specific**: tie findings to files, types, or endpoints when possible.
3. **Separate severity from preference**: “Critical” means wrongness, data loss risk, or security; “Low” is polish.
4. For **polyglot repos**, explicitly review **contract consistency** (status codes, error shape, timeouts, idempotency headers) across services.

---

## Review dimensions

Analyze step by step across the following (merge overlapping points; skip N/A areas briefly).

### 1. Correctness and requirements fit

- Implementation matches the stated requirements (explicit and implied).
- Edge cases: nulls, empty collections, oversize payloads, invalid or ambiguous input, boundary values.
- Logical bugs, off-by-one, wrong defaults, incorrect status transitions.

### 2. Architecture and design

- Layering (e.g. controller / service / repository); separation of concerns.
- Coupling vs useful abstractions; avoid “interface theater” and god classes/services.
- Domain model health (anemic vs rich where it matters); extensibility (open/closed) without over-engineering.

### 3. Cross-service contracts and integration

- API consistency, versioning assumptions, error payload shape.
- Timeouts, retries, and idempotency across process boundaries.
- Duplicated business rules vs single source of truth.

### 4. Transaction management (JPA/Spring)

- Correct `@Transactional` usage and proxy limitations; boundary size; isolation assumptions.
- Long-running transactions; rollback behavior; consistency when calling external systems.

### 5. Performance (algorithms, throughput, and persistence)

- Algorithm and data-structure choices; time/space where relevant.
- Batching, streaming, back-pressure; unnecessary full-buffering of large inputs.
- N+1 queries, fetch plans, pagination, indexing assumptions; hot paths and over-fetching.

### 6. Memory and resources

- Unclosed streams/clients; cache eviction; `ThreadLocal`; unbounded collections.
- Avoidable allocations on hot paths.

### 7. Concurrency and idempotency

- Race conditions, lost updates, optimistic/pessimistic locking, unique constraints.
- Safe retries; deduplication keys where required.

### 8. Data integrity and validation

- Validation at system boundaries; normalization (IDs, emails, keys).
- Null-safety; DB constraints vs application assumptions.

### 9. Error handling

- Domain vs generic exceptions; no silent failures.
- Mapping to HTTP/API responses; validation error structure (multi-field).

### 10. Time and determinism

- Injectable `Clock` vs `Instant.now()` everywhere; time zones; flaky tests.

### 11. Logging and observability

- Structured logging; levels; **no secrets or PII** in logs.
- Correlation/trace IDs where appropriate; metrics (e.g. Prometheus) if relevant.

### 12. API and contract design

- DTO immutability; entities vs API models; null vs `Optional` vs explicit types.
- Booleans vs polymorphism where behavior branches.

### 13. Configuration and environment

- Externalized config; startup validation; no magic constants in business logic.

### 14. Security

- Injection (SQL, command, template); sanitization; least exposure of identifiers; authn/authz boundaries if present.
- Secrets management; safe defaults.

### 15. Dependencies and supply chain

- Necessary vs redundant dependencies; outdated or vulnerable libraries; lockfiles and reproducible builds where applicable.
- Trust boundaries for third-party code and build plugins.

### 16. Code quality, style, and consistency

- Duplication, naming, class/method size; SOLID as **guidance**, not dogma.
- Formatting, linters, idioms per language (e.g. Java vs TypeScript); consistency across modules.

### 17. Documentation and developer experience

- Root `README` and `docs/`: accurate run/test instructions, ports, env vars, integration-test requirements.
- API or architecture notes where they reduce wrong assumptions; comments where behavior is non-obvious (avoid noise).

### 18. Testing and testability

- Deterministic tests; seams for time and I/O; unit vs integration boundaries.
- Failure scenarios (validation, conflicts, downstream errors).

---

## For each issue (required)

- **Why** it matters (production impact or maintenance risk).
- **Severity**: Critical / High / Medium / Low.
- **Example** or failure scenario where it clarifies the risk.
- **Fix** or refactor direction (concrete, not vague).

---

## Final output (required)

1. Consolidated issue list (or grouped by dimension).
2. **Top 5** most important problems (not necessarily the most numerous).
3. **Prioritized roadmap** (quick wins vs larger refactors).
4. Short **production-readiness** note: externalized config, structured logging, metrics, test confidence—what is already strong vs missing.

---

## Production-ready expectations (checklist)

Comment briefly on whether the system is moving toward (not necessarily perfect):

- Clear boundaries and SRP-friendly modules.
- DRY **business** rules (not DRY at the cost of wrong abstractions).
- Structured logging with sensitive data avoided.
- Observable behavior (metrics/health) where appropriate.
- Configuration validated at startup.
- Automated tests that increase confidence without flakiness.
