# Rigorous code review — v2

**Scope:** (payment-service + import-service, scripts, `docs/`). Same **9-axis** rubric as `docs/skills/rigorous-code-review-score.md`.  
**How to read the total (9–45):** 9 = weak across all dimensions; 45 = strong production maturity everywhere. The stack is a **credible fintech-style demo** with clear **production gaps** called out in the roadmap (auth, cluster rate limits, CI for integration profile).

---

## 1. Overall summary

The monorepo delivers a **coherent** split: **Java** as system of record (payments, idempotency, import batches, migration) and **TypeScript** for **streaming** multipart import, **SHA-256** batch identity, and **per-row** calls with stable **Idempotency-Key**s. **Tests** on both sides (unit + Testcontainers-gated Java integration, Vitest with HTTP mocks for import) and **docs** (architecture, import flow, API, QA) give reviewers confidence to run and reason about the system. **Main production risks** remain **security** (open APIs, actuator, secrets) and **operational scale** (in-process rate limit, per-row HTTP to Java). **Maintainability** is high: layering, mappers, factory/strategy parsers, and consistent error shapes.

---

## 2. Score breakdown

| # | Category | Score (1–5) | Max |
|---|----------|-------------|-----|
| 1 | Correctness | **5** | 5 |
| 2 | Code quality & readability | **5** | 5 |
| 3 | Architecture & design | **4** | 5 |
| 4 | Testing | **5** | 5 |
| 5 | Performance | **5** | 5 |
| 6 | Error handling | **5** | 5 |
| 7 | Security & dependencies | **3** | 5 |
| 8 | Code style & consistency | **5** | 5 |
| 9 | Documentation | **5** | 5 |
| | **Total** | **42 / 45** | **45** |

**Why not 5/5 on architecture (4):** distributed rate limiting and “single source of truth for quota” are **documented** (gateway/Redis) but **not** implemented; acceptable for a bounded assignment, not a full edge deployment story.  
**Why 3 on security:** intentional demo posture—**roadmap** lists auth/secrets/actuator; code matches “open local default” with eyes open.

---

## 3. Strengths

- **Idempotency and integrity:** contract-scoped payment keys, import batch by **file hash**, JPA fetches (e.g. `JOIN FETCH` to avoid N+1 when building responses).
- **Import path:** stream-oriented processing, `ParserFactory` + strategies, bounded concurrency, alignment with `docs/import-flow-node-vs-java.md`.
- **Errors:** `GlobalExceptionHandler` with consistent `ErrorResponse` and request correlation; import side maps payment API errors to a stable import contract.
- **Observability:** Actuator, Prometheus, trace/request headers across Java and Node; structured logging story in docs.
- **Test depth:** `mvn test` + `-Pintegration-tests` with Testcontainers; Vitest for parsers, payment client, and integration-style server tests.
- **Documentation:** README, `docs/qa-instruction.md`, `docs/architecture.md`, import prompt under `tasks/` for reproducible agent workflows.

---

## 4. Critical issues (high priority for production)

1. **Security:** No authentication/authorization; secrets and actuator exposure need profile-based hardening (already flagged in README roadmap).
2. **Rate limiting:** In-process (Bucket4j) does not share budget across replicas without an edge or Redis (architecture doc describes options; code is single-JVM).
3. **CI:** Integration tests are **opt-in** with `-Pintegration-tests` + Docker; pipelines must run that job or those tests are skipped silently.
4. **Import latency at scale:** Per-row HTTP to Java is correct and simple; high volume will need batch APIs or contract caching (roadmap P2 alludes to bulk/round-trip reduction).

---

## 5. Improvement suggestions (ordered)

1. **CI:** Add a job that runs `mvn -B test -Pintegration-tests` in `payment-service` with a Docker service (or document why it is not required in your pipeline).
2. **Security (when in scope):** Spring Security or API keys; lock actuator; externalize defaults.
3. **Rate limit:** Move budget to **gateway** or **Redis-backed** Bucket4j if multi-replica.
4. **Node:** Optional contract cache for the duration of one file to cut repeated `GET /contracts/by-number/...` calls (measure first).

---

## 6. Code examples (illustrative only)

**Consistent error payload (Java):** `com.example.shared.error.GlobalExceptionHandler` + `ErrorResponse` record (timestamp, code, message, path, requestId).

**CI (sketch):**

```yaml
- run: mvn -B test -Pintegration-tests
  working-directory: payment-service
  # needs Docker
```

---

## Summary

**Final score: 42 / 45.** The project scores **very high** on correctness, structure, tests, performance awareness, and documentation; the **4-point** gap to the maximum is **mostly security and distributed operational controls**, not a lack of core engineering quality.
