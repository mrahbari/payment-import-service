import type { PaymentRow } from "./parsing/ParsingStrategy.js";

const types = new Set(["incoming", "outgoing"]);

function shortRaw(value: string, max = 40): string {
  const t = value.trim();
  if (t.length <= max) {
    return t;
  }
  return `${t.slice(0, max)}…`;
}

/** Strict `YYYY-MM-DD` calendar date (invalid calendars e.g. 2024-02-30 → null). */
export function parseIsoCalendarDate(raw: string): string | null {
  const s = raw.trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) {
    return null;
  }
  const y = Number(s.slice(0, 4));
  const m = Number(s.slice(5, 7));
  const d = Number(s.slice(8, 10));
  if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) {
    return null;
  }
  if (m < 1 || m > 12 || d < 1 || d > 31) {
    return null;
  }
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) {
    return null;
  }
  return s;
}

export type ValidatedRow = {
  paymentDate: string;
  amount: string;
  type: "incoming" | "outgoing";
  contractNumber: string;
};

export type RowValidation = { ok: true; data: ValidatedRow } | { ok: false; error: string };

/**
 * Per-row validation with a stable, human-readable error message (for import summary and logs).
 * Does not call the payment API.
 */
export function validateRowWithMessage(row: PaymentRow, _rowIndex: number): RowValidation {
  if (!row.contract_number?.trim()) {
    return { ok: false, error: "Missing or empty contract number" };
  }
  if (!row.payment_date?.trim()) {
    return { ok: false, error: "Missing payment_date" };
  }
  const paymentDate = parseIsoCalendarDate(row.payment_date);
  if (!paymentDate) {
    const got = shortRaw(String(row.payment_date));
    return {
      ok: false,
      error: `Invalid payment_date "${got}" — use a real calendar day as YYYY-MM-DD (e.g. 2024-06-15)`,
    };
  }
  if (!row.amount?.trim()) {
    return { ok: false, error: "Missing amount" };
  }
  const t = row.type?.toLowerCase();
  if (!t || !types.has(t)) {
    const got = shortRaw(String(row.type ?? ""), 24);
    return {
      ok: false,
      error: `Invalid type "${got}" (allowed: incoming, outgoing)`,
    };
  }
  const amount = row.amount.trim();
  if (Number.isNaN(Number(amount))) {
    return { ok: false, error: `Invalid amount "${shortRaw(amount)}" (not a number)` };
  }
  if (Number(amount) <= 0) {
    return { ok: false, error: `Amount must be greater than 0 (got ${amount})` };
  }
  return {
    ok: true,
    data: {
      paymentDate,
      amount,
      type: t as "incoming" | "outgoing",
      contractNumber: row.contract_number.trim(),
    },
  };
}

export function validateRow(row: PaymentRow, rowIndex: number): ValidatedRow | null {
  const r = validateRowWithMessage(row, rowIndex);
  return r.ok ? r.data : null;
}
