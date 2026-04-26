import sax from "sax";
import type { Readable } from "node:stream";
import { config } from "../config.js";
import type { ParsingStrategy, PaymentRow } from "./ParsingStrategy.js";

/**
 * Minimal streaming XML: expects &lt;payments&gt;&lt;payment&gt;... elements with child tags matching CSV columns.
 */
export class XmlParsingStrategy implements ParsingStrategy {
  readonly format = "xml" as const;

  async parseStream(input: Readable, onRow: (row: PaymentRow, rowIndex: number) => Promise<void>): Promise<void> {
    const strict = true;
    const parser = sax.createStream(strict, { trim: true });

    let path: string[] = [];
    let current: Partial<PaymentRow> | null = null;
    let textBuf = "";
    let rowIndex = 0;
    let chain: Promise<void> = Promise.resolve();
    const highWater = config.importRowBatchHighWater;
    let batch: Promise<void>[] = [];

    const flushText = () => {
      const t = textBuf.trim();
      textBuf = "";
      return t;
    };

    await new Promise<void>((resolve, reject) => {
      parser.on("opentag", (node) => {
        path.push(node.name.toLowerCase());
        if (node.name.toLowerCase() === "payment") {
          current = {};
        }
      });

      parser.on("text", (t) => {
        textBuf += t;
      });

      parser.on("closetag", (name) => {
        const tag = name.toLowerCase();
        const leaf = flushText();
        if (current && path[path.length - 1] === tag) {
          if (tag === "payment_date") current.payment_date = leaf;
          if (tag === "amount") current.amount = leaf;
          if (tag === "type") current.type = leaf.toLowerCase();
          if (tag === "contract_number") current.contract_number = leaf;
        }
        if (tag === "payment" && current) {
          const row: PaymentRow = {
            payment_date: current.payment_date ?? "",
            amount: current.amount ?? "",
            type: current.type ?? "",
            contract_number: current.contract_number ?? "",
          };
          current = null;
          const idx = ++rowIndex;
          batch.push(onRow(row, idx));
          if (batch.length >= highWater) {
            const toFlush = batch;
            batch = [];
            chain = chain.then(() => Promise.all(toFlush)).then(() => undefined);
          }
        }
        path.pop();
      });

      parser.on("error", reject);
      parser.on("end", () => {
        if (batch.length > 0) {
          const tail = batch;
          batch = [];
          chain = chain.then(() => Promise.all(tail)).then(() => undefined);
        }
        chain.then(resolve).catch(reject);
      });
      input.pipe(parser);
    });
  }
}
