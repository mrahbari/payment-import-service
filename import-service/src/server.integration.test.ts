import { createHash } from "node:crypto";
import request from "supertest";
import nock from "nock";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

const MOCK_PAYMENT = "http://127.0.0.1:4010";
const csv =
  "payment_date,amount,type,contract_number\n2024-06-01,50.00,incoming,CNT-1001\n";
const fileSha256 = createHash("sha256").update(csv, "utf-8").digest("hex");

function parseJsonBody(requestBody: unknown): { fileSha256: string } {
  if (requestBody != null && typeof requestBody === "object") {
    return requestBody as { fileSha256: string };
  }
  if (typeof requestBody === "string") {
    return JSON.parse(requestBody) as { fileSha256: string };
  }
  if (Buffer.isBuffer(requestBody)) {
    return JSON.parse(requestBody.toString("utf-8")) as { fileSha256: string };
  }
  throw new Error("unexpected request body for nock");
}

describe("import HTTP (multipart → payment API)", () => {
  it("serves the browser upload page on GET /payments/import", async () => {
    vi.resetModules();
    process.env.VITEST = "true";
    const { app } = await import("./server.js");
    const res = await request(app).get("/payments/import").expect(200);
    expect(res.type).toMatch(/html/);
    expect(res.text).toContain('id="upload-form"');
    expect(res.text).toContain('name="file"');
  });

  beforeAll(() => {
    process.env.PAYMENT_API_BASE_URL = MOCK_PAYMENT;
    process.env.VITEST = "true";
    process.env.AXIOS_RETRIES = "0";
    process.env.AXIOS_TIMEOUT_MS = "3000";
    process.env.IMPORT_ROW_CONCURRENCY = "2";
    process.env.IMPORT_ROW_BATCH_HIGH_WATER = "32";
  });

  afterEach(() => {
    nock.cleanAll();
  });

  it(
    "returns 200 summary when payment API accepts the row",
    async () => {
      vi.resetModules();
      process.env.PAYMENT_API_BASE_URL = MOCK_PAYMENT;
      process.env.VITEST = "true";
      process.env.AXIOS_RETRIES = "0";
      process.env.AXIOS_TIMEOUT_MS = "3000";

      nock(MOCK_PAYMENT)
        .post("/api/v1/import-batches/start")
        .reply(201, (_uri, requestBody) => {
          const body = parseJsonBody(requestBody);
          return {
            fileSha256: body.fileSha256,
            status: "PENDING",
            rowsAccepted: 0,
            rowsRejected: 0,
            createdAt: "2024-01-01T00:00:00Z",
            completedAt: null,
            alreadyProcessed: false,
          };
        });

      nock(MOCK_PAYMENT)
        .get("/api/v1/contracts/by-number/CNT-1001")
        .reply(200, { id: 1, clientId: 1, contractNumber: "CNT-1001" });

      nock(MOCK_PAYMENT)
        .post("/api/v1/contracts/1/payments")
        .reply(201, { id: 100, contractId: 1, amount: 50.0, type: "INCOMING" });

      nock(MOCK_PAYMENT)
        .post(/^\/api\/v1\/import-batches\/[a-f0-9]{64}\/complete$/)
        .reply(200, (uri) => {
          const m = uri.match(/^\/api\/v1\/import-batches\/([a-f0-9]{64})\/complete$/);
          const sha = m?.[1] ?? "";
          return {
            fileSha256: sha,
            status: "COMPLETED",
            rowsAccepted: 1,
            rowsRejected: 0,
            createdAt: "2024-01-01T00:00:00Z",
            completedAt: "2024-01-02T00:00:00Z",
            alreadyProcessed: false,
          };
        });

      const { app } = await import("./server.js");

      const res = await request(app)
        .post("/payments/import")
        .attach("file", Buffer.from(csv, "utf-8"), "payments.csv")
        .expect(200);

      expect(res.body).toMatchObject({
        fileSha256,
        totalRows: 1,
        successfulRows: 1,
        failedRows: 0,
        rowsAccepted: 1,
        rowsRejected: 0,
        format: "csv",
        errors: [],
      });
      expect(res.headers["x-trace-id"]).toBeDefined();
      expect(res.headers["x-request-id"]).toBeDefined();
      expect(nock.isDone()).toBe(true);
    },
    { timeout: 15_000 },
  );

  it(
    "returns partial summary with row errors for mixed valid/invalid CSV",
    async () => {
      vi.resetModules();
      process.env.PAYMENT_API_BASE_URL = MOCK_PAYMENT;
      process.env.VITEST = "true";
      process.env.AXIOS_RETRIES = "0";
      process.env.AXIOS_TIMEOUT_MS = "3000";

      const mixedCsv = [
        "payment_date,amount,type,contract_number",
        "2024-06-01,50.00,incoming,CNT-1001",
        "2024-02-30,1.00,incoming,CNT-1001",
        "2024-06-02,10.00,outgoing,CNT-1001",
      ].join("\n");
      const sha = createHash("sha256").update(mixedCsv, "utf-8").digest("hex");

      nock(MOCK_PAYMENT)
        .post("/api/v1/import-batches/start")
        .reply(201, (uri, requestBody) => {
          const body = parseJsonBody(requestBody);
          return {
            fileSha256: body.fileSha256,
            status: "PENDING",
            rowsAccepted: 0,
            rowsRejected: 0,
            alreadyProcessed: false,
            createdAt: "2024-01-01T00:00:00Z",
            completedAt: null,
          };
        });

      // One GET per distinct contract number per file (in-memory cache deduplicates rows)
      nock(MOCK_PAYMENT).get("/api/v1/contracts/by-number/CNT-1001").once().reply(200, {
        id: 1,
        clientId: 1,
        contractNumber: "CNT-1001",
      });

      nock(MOCK_PAYMENT).post("/api/v1/contracts/1/payments").twice().reply(201, { id: 1 });

      nock(MOCK_PAYMENT)
        .post(/^\/api\/v1\/import-batches\/[a-f0-9]{64}\/complete$/)
        .reply(200, (uri) => {
          return { fileSha256: sha, status: "COMPLETED" };
        });

      const { app } = await import("./server.js");

      const res = await request(app)
        .post("/payments/import")
        .attach("file", Buffer.from(mixedCsv, "utf-8"), "mixed.csv")
        .expect(200);

      expect(res.body).toMatchObject({
        fileSha256: sha,
        totalRows: 3,
        successfulRows: 2,
        failedRows: 1,
        format: "csv",
      });
      expect(res.body.errors[0].row).toBe(2);
      expect(String(res.body.errors[0].error)).toMatch(/Invalid payment_date/);
      expect(String(res.body.errors[0].error)).toMatch(/2024-02-30/);
      expect(nock.isDone()).toBe(true);
    },
    { timeout: 20_000 },
  );

  it(
    "all invalid rows yields zero success and per-row errors",
    async () => {
      vi.resetModules();
      process.env.PAYMENT_API_BASE_URL = MOCK_PAYMENT;
      process.env.VITEST = "true";
      process.env.AXIOS_RETRIES = "0";
      process.env.AXIOS_TIMEOUT_MS = "3000";

      const badCsv = [
        "payment_date,amount,type,contract_number",
        "nope,1,incoming,CNT-1001",
        "2024-01-01,0,incoming,CNT-1001",
      ].join("\n");
      const sha = createHash("sha256").update(badCsv, "utf-8").digest("hex");

      nock(MOCK_PAYMENT).post("/api/v1/import-batches/start").reply(201, (uri, requestBody) => {
        const body = parseJsonBody(requestBody);
        return { fileSha256: body.fileSha256, alreadyProcessed: false, status: "PENDING" };
      });
      nock(MOCK_PAYMENT).post(/^\/api\/v1\/import-batches\/[a-f0-9]{64}\/complete$/).reply(200, {});

      const { app } = await import("./server.js");
      const res = await request(app)
        .post("/payments/import")
        .attach("file", Buffer.from(badCsv, "utf-8"), "bad.csv")
        .expect(200);

      expect(res.body).toMatchObject({
        fileSha256: sha,
        totalRows: 2,
        successfulRows: 0,
        failedRows: 2,
      });
      expect(res.body.errors).toHaveLength(2);
      expect(res.body.errors[0].row).toBe(1);
      expect(res.body.errors[0].error).toMatch(/Invalid payment_date/);
      expect(res.body.errors[0].error).toMatch(/nope/);
      expect(res.body.errors[1]).toEqual({ row: 2, error: "Amount must be greater than 0 (got 0)" });
      expect(nock.isDone()).toBe(true);
    },
    { timeout: 20_000 },
  );

  it(
    "returns ApiErrorBody when multipart has no file field",
    async () => {
      vi.resetModules();
      process.env.PAYMENT_API_BASE_URL = MOCK_PAYMENT;
      process.env.VITEST = "true";
      process.env.AXIOS_RETRIES = "0";

      const { app } = await import("./server.js");

      const res = await request(app)
        .post("/payments/import")
        .attach("other", Buffer.from("x"), "x.txt")
        .expect(400);

      expect(res.body).toMatchObject({
        code: "BAD_REQUEST",
        path: "/payments/import",
      });
      expect(res.body.timestamp).toMatch(/^\d{4}-/);
      expect(res.body.requestId).toBeDefined();
      expect(res.body.message).toContain("file");
    },
    { timeout: 15_000 },
  );
});
