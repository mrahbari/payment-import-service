import { parse } from "csv-parse";
import type { Readable } from "node:stream";
import { config } from "../config.js";
import type { ParsingStrategy, PaymentRow } from "./ParsingStrategy.js";

export class CsvParsingStrategy implements ParsingStrategy {
  readonly format = "csv" as const;

  async parseStream(input: Readable, onRow: (row: PaymentRow, rowIndex: number) => Promise<void>): Promise<void> {
    const parser = input.pipe(
      parse({
        columns: true,
        trim: true,
        relax_column_count: true,
        bom: true,
      }),
    );
    let index = 0;
    const highWater = config.importRowBatchHighWater;
    const batch: Promise<void>[] = [];
    for await (const record of parser) {
      const r = record as Record<string, string>;
      const row: PaymentRow = {
        payment_date: r.payment_date ?? r.paymentDate ?? "",
        amount: r.amount ?? "",
        type: (r.type ?? "").toLowerCase(),
        contract_number: r.contract_number ?? r.contractNumber ?? "",
      };
      batch.push(onRow(row, ++index));
      if (batch.length >= highWater) {
        await Promise.all(batch);
        batch.length = 0;
      }
    }
    if (batch.length > 0) {
      await Promise.all(batch);
    }
  }
}
