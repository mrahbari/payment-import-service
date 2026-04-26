import { describe, expect, it } from "vitest";
import { CsvParsingStrategy } from "./CsvParsingStrategy.js";
import { ParserFactory } from "./ParserFactory.js";
import { XmlParsingStrategy } from "./XmlParsingStrategy.js";

describe("ParserFactory", () => {
  it("selects csv", () => {
    const s = ParserFactory.resolve("text/csv", "payments.csv");
    expect(s).toBeInstanceOf(CsvParsingStrategy);
  });

  it("selects xml", () => {
    const s = ParserFactory.resolve("application/xml", "data.xml");
    expect(s).toBeInstanceOf(XmlParsingStrategy);
  });

  it("throws on unknown", () => {
    expect(() => ParserFactory.resolve("application/json", "x.json")).toThrow(/Unsupported/);
    try {
      ParserFactory.resolve("application/json", "x.json");
    } catch (e) {
      expect(e).toMatchObject({ status: 415, code: "UNSUPPORTED_MEDIA_TYPE" });
    }
  });
});
