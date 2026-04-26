import { describe, it, expect, vi, beforeEach } from "vitest";
import axios from "axios";
import { createContractLookup } from "./paymentClient.js";

vi.mock("axios");

describe("createContractLookup (caching)", () => {
  const mockAxios = axios.create() as any;
  const trace = { traceId: "t1", requestId: "r1" };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should cache results and deduplicate concurrent requests", async () => {
    const lookup = createContractLookup(mockAxios, trace);

    // Mock response
    mockAxios.get = vi.fn().mockResolvedValue({
      data: { id: 100, clientId: 1 },
    });

    // Fire multiple concurrent requests for the same contract
    const p1 = lookup("CNT-1");
    const p2 = lookup("CNT-1");
    const p3 = lookup("  CNT-1  "); // Check trimming

    const [r1, r2, r3] = await Promise.all([p1, p2, p3]);

    expect(r1.id).toBe(100);
    expect(r2.id).toBe(100);
    expect(r3.id).toBe(100);

    // Should only have called the API ONCE
    expect(mockAxios.get).toHaveBeenCalledTimes(1);
    expect(mockAxios.get).toHaveBeenCalledWith("/api/v1/contracts/by-number/CNT-1", {
      headers: { "X-Trace-Id": "t1", "X-Request-Id": "r1" },
    });

    // Subsequent call should hit cache immediately
    const r4 = await lookup("CNT-1");
    expect(r4.id).toBe(100);
    expect(mockAxios.get).toHaveBeenCalledTimes(1);
  });

  it("should allow different contracts separately", async () => {
    const lookup = createContractLookup(mockAxios, trace);

    mockAxios.get = vi.fn()
      .mockResolvedValueOnce({ data: { id: 101, clientId: 1 } })
      .mockResolvedValueOnce({ data: { id: 102, clientId: 1 } });

    const r1 = await lookup("CNT-1");
    const r2 = await lookup("CNT-2");

    expect(r1.id).toBe(101);
    expect(r2.id).toBe(102);
    expect(mockAxios.get).toHaveBeenCalledTimes(2);
  });
});
