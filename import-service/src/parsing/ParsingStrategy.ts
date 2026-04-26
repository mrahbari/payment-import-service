import type { Readable } from "node:stream";

export type PaymentRow = {
  payment_date: string;
  amount: string;
  type: string;
  contract_number: string;
};

/**
 * Streaming parser: emits validated logical rows; does not load whole file.
 */
export interface ParsingStrategy {
  readonly format: "csv" | "xml";

  parseStream(input: Readable, onRow: (row: PaymentRow, rowIndex: number) => Promise<void>): Promise<void>;
}
