# QA instructions

**Role:** this file is the **local runbook** (commands, import, tests). The root **[`README.md`](../README.md)** stays a **short** demo map.

**How to use this page:** run **[Automated](#qa-demo-walkthrough) → [Tests / coverage](#tests-and-coverage-detail)** first, then use **[Manual](#manual-start-without-run-all) → [Import / API](#import-a-file)** when you need per-service control or explicit `curl` / Postman / browser. Use **[Reference and further reading](#reference-and-further-reading)** for API snippets and doc links.

---

## Contents

- [Automated: full stack](#qa-demo-walkthrough)
- [Automated: tests, smoke, and coverage](#tests-and-coverage-detail)
- [Manual: start services without `run-all.sh`](#manual-start-without-run-all)
- [Manual: import, browser, Postman, `curl`](#import-a-file)
- [Reference and further reading](#reference-and-further-reading)

---

<a id="qa-demo-walkthrough"></a>

## Automated: full stack

**Prereqs:** Docker (for PostgreSQL), **JDK 21**, **Maven**, **Node 20+**, **Bash** (Linux, macOS, or Git Bash on Windows). Prefer a **normal local path** for the clone (not a flaky shared/VM mount; `npm` can hit `EACCES` on some shared folders).

**Run from repo root:**

```bash
sed -i 's/\r$//' scripts/*.sh          # if Windows saved CRLF (macOS: sed -i '' 's/\r$//' scripts/*.sh)
chmod +x scripts/*.sh
./scripts/build.sh                    # first time or after code changes; optional
./scripts/run-all.sh
```

- Starts **PostgreSQL** (Docker), **payment-service** (background), then **idempotent** [`seed.sql`](seed.sql) when **CNT-1001** is missing (after the API is healthy), then **import-service** in the **foreground** (**`Ctrl+C`** stops import + background Java).
- **Ports:** `8080` (Java), `3000` (import), `5432` (Postgres).

**Sanity check (order):** [health](http://localhost:8080/actuator/health) → [import UI](http://localhost:3000/payments/import) → [Swagger](http://localhost:8080/swagger-ui.html) → [example contract CNT-1001](http://localhost:8080/api/v1/contracts/by-number/CNT-1001).

**Logs (optional, second terminal):**

```bash
tail -f payment-service/target/logs/payment-service.log
tail -f import-service/logs/import-service.log
```

A fuller **URL** list (including metrics) is in the README: **[`README.md` — Service URLs](../README.md#service-urls-local-defaults)**.

---

<a id="tests-and-coverage-detail"></a>

## Automated: tests, smoke, and coverage

**Unit tests (no Docker for default Java profile):**

```bash
cd payment-service && mvn test
cd import-service && npm test
```

- **import-service** tests include **HTTP** integration (no Docker).
- **payment-service**
  - **Default** `mvn test` (from `payment-service/`) runs **unit** tests only; **`PaymentApiIntegrationTest`** is **excluded** (Surefire + file exclude) so the stack is fast and does not need containers.
  - **Integration** profile: `mvn test -Pintegration-tests` runs the same **plus** `PaymentApiIntegrationTest`, which needs **Docker** (Testcontainers starts Postgres `postgres:16-alpine`). If Docker is not running, that class is **skipped** and behavior is like the default run. On **WSL** or a VM, use the environment where `docker` / Docker Desktop is actually available.
- **JaCoCo**
  - Report paths (relative to the module): **`payment-service/target/site/jacoco/index.html`** (HTML) and **`payment-service/target/site/jacoco/jacoco.xml`** (XML; same counters, useful for automation or quick search). The plugin runs in the `test` phase; if the phase **fails** (e.g. integration or unit error), the later goals in that phase may not run, so the reports can look **missing or outdated**.
  - If `payment-service/target/jacoco.exec` exists after a run, you can still generate or refresh the report:  
    `mvn -f payment-service/pom.xml jacoco:report` (writes `index.html` and `jacoco.xml`)  
  - A **default** `mvn test` report reflects **unit / controller** coverage, not the extra paths exercised by `PaymentApiIntegrationTest`. To **raise** coverage: add `@WebMvcTest` / `MockMvc`, filters, remaining `GlobalExceptionHandler` cases; use the integration profile with Docker; extend **import-service** `vitest` (parsers, `paymentClient`).
- **Mockito** “could not self-attach” on some JDKs: run once with  
  `MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true" mvn test`, or use the project’s `mockito-extensions` setup under `payment-service/src/test/resources/`.
- If console output is thin, some lines may be in **`payment-service/target/surefire-reports/`** when Surefire redirects output.

**Smoke (no Docker):** builds both sides, runs payment-service on **H2 (in-memory)** and import-service, then **`POST` import** with **`samples/import-valid-small.csv`**. It checks that the import path responds (rejections are expected without seeded Postgres data).

```bash
./scripts/smoke-test.sh
```

---

<a id="manual-start-without-run-all"></a>

## Manual: start services without `run-all.sh`

**PostgreSQL only**

```bash
docker compose up -d postgres
```

You still need a **migrated** DB and **seed** data for realistic flows. Prefer **`./scripts/run-all.sh`** once, or apply [`seed.sql`](seed.sql) yourself:

```bash
docker compose exec -T postgres psql -U user -d payment_db < docs/seed.sql
```

**Windows PowerShell:**  
`Get-Content docs/seed.sql -Raw | docker compose exec -T postgres psql -U user -d payment_db`

**Payment service only (repo root helpers)**

```bash
./scripts/run-payment-service.sh
# or: cd payment-service && mvn spring-boot:run
```

**Import service only**

```bash
./scripts/run-import-service.sh
# or: cd import-service && npm install && npm run dev
```

`PORT` (default `3000`), `PAYMENT_API_BASE_URL` (default `http://localhost:8080`). Tuning: `IMPORT_ROW_CONCURRENCY`, `IMPORT_ROW_BATCH_HIGH_WATER`. After a build: `npm run build && npm start`.

**Two terminals (minimal split)**

1. `docker compose up -d postgres`
2. `./scripts/run-payment-service.sh`
3. `./scripts/run-import-service.sh`

---

<a id="import-a-file"></a>

## Manual: import, browser, Postman, `curl`

`run-all.sh` already **seeds** when **CNT-1001** is missing—use this section to **verify** or **import** explicitly.

1. **Contract**

   ```bash
   curl -s http://localhost:8080/api/v1/contracts/by-number/CNT-1001
   ```

2. **Create a payment** (first call **201**; same `Idempotency-Key` → **200** replay)

   ```bash
   curl -X POST http://localhost:8080/api/v1/contracts/1/payments \
     -H "Idempotency-Key: test-key-001" \
     -H "Content-Type: application/json" \
     -d '{"clientId":1,"amount":"50.00","type":"INCOMING","paymentDate":"2024-06-01"}'
   ```

3. **File import** — multipart form field name must be **`file`**. **Both** services must be up.

   - **Browser:** [http://localhost:3000/payments/import](http://localhost:3000/payments/import) (attach CSV/XML, submit).
   - **Postman:** import from **`samples/`** fixtures. Variables: **`importBaseUrl`** = `http://localhost:3000`, **`paymentBaseUrl`** = `http://localhost:8080` (unless you changed ports). **POST** `…/payments/import` → form-data: key **`file`**, type **File**, use **`samples/import-valid-small.csv`**, **`samples/import-valid-small.xml`**, or other **[`../samples/`](../samples/)** fixtures.
   - **curl (repo root):**

     ```bash
     curl -X POST http://localhost:3000/payments/import -F "file=@samples/import-valid-small.csv;type=text/csv"
     curl -X POST http://localhost:3000/payments/import -F "file=@samples/import-valid-small.xml;type=text/xml"
     ```

4. **Stop / reset** — **`Ctrl+C`** in the terminal where **`run-all.sh`** runs, or run **`./scripts/cleanup.sh`** (frees **3000/8080**, **`docker compose down -v`**, cleans builds; **wipes DB volumes**—see the script). Use **`./scripts/kill-dev-ports.sh`** if you only need to free ports.

---

<a id="reference-and-further-reading"></a>

## Reference and further reading

**HTTP (payment API)** — base **`/api/v1`**

```http
GET /api/v1/contracts/1/payments
```

```http
GET /api/v1/contracts/by-number/CNT-1001
```

```http
POST /api/v1/contracts/1/payments
Idempotency-Key: pay-001
Content-Type: application/json

{"clientId":1,"amount":"100.00","type":"INCOMING","paymentDate":"2024-06-01"}
```

**Observability (local):** [Java metrics](http://localhost:8080/actuator/prometheus), [import metrics](http://localhost:3000/metrics); response headers **X-Trace-Id** / **X-Request-Id** on the import service.

**More:** [`api.md`](api.md), [`architecture.md`](architecture.md), [`CHEAT-SHEET.md`](CHEAT-SHEET.md) (ports, env, `curl`), [`import-flow-node-vs-java.md`](import-flow-node-vs-java.md) (import path). **[`README.md`](../README.md)** — short demo, scripts, Service URLs, roadmap. **[`skills/prompt-maintain-readme.md`](skills/prompt-maintain-readme.md)** — how to keep `README` and this file aligned.
