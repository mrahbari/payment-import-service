import { describe, expect, it } from "vitest";
import { MAX_ROW_ERRORS_IN_RESPONSE, pushCappedError, type RowError } from "./importResult.js";

describe("pushCappedError", () => {
  it("stops at MAX_ROW_ERRORS_IN_RESPONSE", () => {
    const errors: RowError[] = [];
    for (let i = 1; i <= MAX_ROW_ERRORS_IN_RESPONSE + 10; i++) {
      pushCappedError(errors, i, `err ${i}`);
    }
    expect(errors).toHaveLength(MAX_ROW_ERRORS_IN_RESPONSE);
    expect(errors[0]).toEqual({ row: 1, error: "err 1" });
    expect(errors[MAX_ROW_ERRORS_IN_RESPONSE - 1]?.row).toBe(MAX_ROW_ERRORS_IN_RESPONSE);
  });
});
