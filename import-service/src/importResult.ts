/**
 * API contract for /payments/import success body (aligns with task8 partial processing summary).
 * Row-level error list is capped to avoid huge payloads; counts still include all rows.
 */
export const MAX_ROW_ERRORS_IN_RESPONSE = 50;

export type RowError = {
  row: number;
  error: string;
};

export function pushCappedError(errors: RowError[], row: number, message: string): void {
  if (errors.length >= MAX_ROW_ERRORS_IN_RESPONSE) {
    return;
  }
  errors.push({ row, error: message });
}
