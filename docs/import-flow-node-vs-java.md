# Import pipeline: import-service vs payment-service

This document describes where file bytes, SHA-256 fingerprints, and row-level parsing occur in this repository, and which requests **payment-service (Java)** handles. It complements [`architecture.md`](architecture.md) with a concrete end-to-end flow.

---

## Summary

| Responsibility | Service |
|----------------|---------|
| Multipart intake, temporary storage, streaming hash, CSV/XML parsing, per-row HTTP calls to the payment API | **import-service** (Node.js / TypeScript) |
| Import batch metadata by `file_sha256`, payment idempotency, persistence of Payment entities in PostgreSQL | **payment-service** (Java / Spring Boot) |

The **binary file is not sent to the JVM.** The API receives the hex-encoded hash and JSON payment payloads (and read-only contract responses) only.

---

## 1. Entry point for the upload

- Clients use **POST /payments/import** on **import-service** with form field **file**.
- **payment-service** does not expose a multipart endpoint for the full import file. Batch lifecycle is exposed as JSON REST under **/api/v1/import-batches**.

---

## 2. Hash computation (import-service)

Implementation: `handleUploadedFile` in `import-service/src/server.ts`.

- The incoming stream is piped through a Transform so that bytes are (a) written to a **temporary file** under the OS temp directory, and (b) fed to **createHash("sha256")** incrementally.
- When the stream completes, `fileSha256 = hash.digest("hex")`.
- The hash is **not** calculated in Java; **payment-service** stores and references this value only as text.

---

## 3. Import batch registration (payment-service)

After `fileSha256` is known, import-service calls:

- **POST /api/v1/import-batches/start** with body `{ "fileSha256": "<64 hex characters>" }`.

This creates or resumes import batch state. **No file bytes** are present in the request. See `ImportBatchController` and `ImportBatchStartRequest`.

If the same hash was already **completed**, the response may indicate **already processed** and downstream steps can short-circuit.

---

## 4. Parsing and per-row work (import-service)

- The **temporary file** is read with `createReadStream(tmpPath)`.
- **ParserFactory** selects a CSV or XML strategy from MIME type and filename.
- **parseStream** processes rows; concurrency is limited (p-limit, aligned with `IMPORT_ROW_*` configuration). For each valid row the service validates the row, then calls contract resolution and payment creation on **payment-service** (including an **Idempotency-Key** such as `import:{fileSha256}:row:{index}`).

Row-to-domain **persistence** of payments is performed by **Java**; **parsing** is performed by **Node**.

---

## 5. Completion

- **import-service** calls **POST /api/v1/import-batches/{fileSha256}/complete** with accepted and rejected row counts.
- The temporary file is **removed**.

**payment-service** does not read file contents; it records batch completion and payment rows only.

---

## 6. Rationale

- **Java** remains the **system of record** for payments and business rules.
- **Node** owns file ingress and streaming parse I/O. The file hash provides idempotent batch semantics across process restarts without re-uploading bytes to the payment API.

---

## Implementation references

| Concern | Location |
|---------|----------|
| Upload, hash, parse, row calls | `import-service/src/server.ts` — `handleUploadedFile` |
| Batch HTTP API | `payment-service/.../ImportBatchController.java` |
| Batch domain logic | `payment-service/.../ImportBatchServiceImpl.java` |
| Payment HTTP and service | `payment-service/.../PaymentController.java`, `PaymentServiceImpl.java` |

---

*Update this document when import or batch routes change.*
