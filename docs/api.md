# API reference

Interactive docs: run **payment-service** and open Swagger UI at `/swagger-ui.html`.

## Payments

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/contracts/{contractId}/payments` | List payments for a contract |
| POST | `/api/v1/contracts/{contractId}/payments` | Create payment; optional header `Idempotency-Key` (scoped to `contractId`; replay → **200**) |

## Contracts

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/contracts/by-number/{contractNumber}` | Resolve numeric id + client for imports |

## Import coordination (used by import-service)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/import-batches/start` | Body `{"fileSha256":"..."}` — **201** new batch, **200** if file already completed, **409** if another import is in progress for same hash |
| POST | `/api/v1/import-batches/{fileSha256}/complete` | Body `{"rowsAccepted":n,"rowsRejected":m}` |

## Import service (Node)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/payments/import` | Small HTML page with a file upload form (same URL as POST) |
| POST | `/payments/import` | `multipart/form-data` field **`file`** (`.csv` or `.xml`) |
| GET | `/metrics` | Prometheus text format |

Errors from **`/payments/import`** use the same JSON shape as **payment-service** `ApiErrorResponse`: `timestamp` (ISO-8601 string), `code`, `message`, `path`, `requestId`. Examples: **400** `BAD_REQUEST` (missing `file`), **415** `UNSUPPORTED_MEDIA_TYPE` (not CSV/XML), **422** `CONTRACT_NOT_FOUND` (payment API returned 404 for contract lookup), **409** `CONFLICT`, **429** `RATE_LIMITED`, **502/503** for payment API failures/timeouts/unreachable.
