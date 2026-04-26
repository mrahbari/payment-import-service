import axios from "axios";
import { describe, expect, it } from "vitest";
import { ImportHttpException, mapPaymentApiFailure } from "./errorResponse.js";

describe("mapPaymentApiFailure", () => {
  it("maps ImportHttpException", () => {
    const r = mapPaymentApiFailure(
      new ImportHttpException(400, "BAD_REQUEST", "Expected field"),
      "/payments/import",
      "req-1",
    );
    expect(r.status).toBe(400);
    expect(r.body.code).toBe("BAD_REQUEST");
    expect(r.body.requestId).toBe("req-1");
    expect(r.body.path).toBe("/payments/import");
    expect(r.body.timestamp).toMatch(/^\d{4}-/);
  });

  it("maps payment API 404 to 422 CONTRACT_NOT_FOUND", () => {
    const err = axios.AxiosError.from(
      new Error("nope"),
      "404",
      undefined,
      undefined,
      {
        status: 404,
        data: { code: "NOT_FOUND", message: "Contract not found" },
      } as never,
    );
    const r = mapPaymentApiFailure(err, "/payments/import", "r2");
    expect(r.status).toBe(422);
    expect(r.body.code).toBe("CONTRACT_NOT_FOUND");
    expect(r.body.message).toBe("Contract not found");
  });

  it("maps timeout to 503", () => {
    const err = new axios.AxiosError("timeout", "ECONNABORTED", undefined, undefined, undefined);
    const r = mapPaymentApiFailure(err, "/p", "r3");
    expect(r.status).toBe(503);
    expect(r.body.code).toBe("PAYMENT_API_TIMEOUT");
  });
});
