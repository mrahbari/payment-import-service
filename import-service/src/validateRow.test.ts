import { describe, expect, it } from "vitest";
import { parseIsoCalendarDate, validateRow, validateRowWithMessage } from "./validateRow.js";

describe("parseIsoCalendarDate", () => {
  it("accepts valid calendar dates", () => {
    expect(parseIsoCalendarDate("2024-01-02")).toBe("2024-01-02");
    expect(parseIsoCalendarDate(" 2024-12-31 ")).toBe("2024-12-31");
  });

  it("rejects invalid calendar and format", () => {
    expect(parseIsoCalendarDate("2024-02-30")).toBeNull();
    expect(parseIsoCalendarDate("24-01-02")).toBeNull();
    expect(parseIsoCalendarDate("2024-1-02")).toBeNull();
    expect(parseIsoCalendarDate("2024-00-10")).toBeNull();
    expect(parseIsoCalendarDate("not-a-date")).toBeNull();
  });
});

describe("validateRow", () => {
  it("accepts valid row", () => {
    const v = validateRow(
      {
        payment_date: "2024-01-02",
        amount: "10.50",
        type: "incoming",
        contract_number: "C-1",
      },
      1,
    );
    expect(v).not.toBeNull();
    expect(v?.type).toBe("incoming");
  });

  it("rejects impossible payment_date", () => {
    expect(
      validateRow(
        {
          payment_date: "2024-06-31",
          amount: "10",
          type: "incoming",
          contract_number: "C-1",
        },
        1,
      ),
    ).toBeNull();
  });

  it("rejects bad type", () => {
    expect(
      validateRow(
        {
          payment_date: "2024-01-02",
          amount: "10",
          type: "wire",
          contract_number: "C-1",
        },
        1,
      ),
    ).toBeNull();
  });
});

describe("validateRowWithMessage", () => {
  it("returns specific reasons for common mistakes", () => {
    const missContract = validateRowWithMessage(
      { payment_date: "2024-01-01", amount: "1", type: "incoming", contract_number: "  " },
      1,
    );
    expect(missContract).toEqual({ ok: false, error: "Missing or empty contract number" });

    const badDate = validateRowWithMessage(
      { payment_date: "2024-02-30", amount: "1", type: "incoming", contract_number: "C-1" },
      2,
    );
    expect(badDate.ok).toBe(false);
    if (!badDate.ok) {
      expect(badDate.error).toMatch(/Invalid payment_date/);
    }

    const neg = validateRowWithMessage(
      { payment_date: "2024-01-02", amount: "0", type: "incoming", contract_number: "C-1" },
      3,
    );
    expect(neg).toEqual({ ok: false, error: "Amount must be greater than 0 (got 0)" });
  });
});
