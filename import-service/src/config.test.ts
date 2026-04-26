import { describe, expect, it, vi } from "vitest";

describe("config (zod validation)", () => {
  it("fails at load when PAYMENT_API_BASE_URL is not a valid URL", async () => {
    vi.resetModules();
    process.env.PAYMENT_API_BASE_URL = "definitely-not-a-valid-url";
    await expect(import("./config.js")).rejects.toThrow();
  });
});
