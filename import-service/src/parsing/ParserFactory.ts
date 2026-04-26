import { ImportHttpException } from "../http/errorResponse.js";
import type { ParsingStrategy } from "./ParsingStrategy.js";
import { CsvParsingStrategy } from "./CsvParsingStrategy.js";
import { XmlParsingStrategy } from "./XmlParsingStrategy.js";

export type SupportedFormat = "csv" | "xml";

const strategies: Record<SupportedFormat, ParsingStrategy> = {
  csv: new CsvParsingStrategy(),
  xml: new XmlParsingStrategy(),
};

export class ParserFactory {
  static resolve(mimeType: string | undefined, filename: string | undefined): ParsingStrategy {
    const lower = (filename ?? "").toLowerCase();
    const mime = (mimeType ?? "").toLowerCase();

    if (mime.includes("csv") || lower.endsWith(".csv")) {
      return strategies.csv;
    }
    if (mime.includes("xml") || lower.endsWith(".xml")) {
      return strategies.xml;
    }
    throw new ImportHttpException(415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported file format; use .csv or .xml");
  }
}
