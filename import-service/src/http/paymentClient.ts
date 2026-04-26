import axios, { type AxiosInstance } from "axios";
import axiosRetry from "axios-retry";
import { config } from "../config.js";

export type TraceHeaders = { traceId: string; requestId: string };

export function createPaymentClient(): AxiosInstance {
  const isVitest = process.env.VITEST === "true";
  const client = axios.create({
    baseURL: config.paymentApiBaseUrl,
    timeout: config.axiosTimeoutMs,
    headers: { "Content-Type": "application/json" },
    /** Node + nock: prefer `http` over `fetch` so requests are intercepted reliably. */
    ...(isVitest ? { adapter: "http" as const } : {}),
  });

  axiosRetry(client, {
    /** Under Vitest, never retry: env can load after first `config` read; retries × timeout ≈ 15s timeouts. */
    retries: isVitest ? 0 : config.axiosRetries,
    retryDelay: axiosRetry.exponentialDelay,
    retryCondition: (error) => {
      const status = error.response?.status;
      return (
        axiosRetry.isNetworkOrIdempotentRequestError(error) ||
        status === 429 ||
        status === 503 ||
        status === 502
      );
    },
  });

  return client;
}

export async function startImportBatch(
  client: AxiosInstance,
  fileSha256: string,
  trace: TraceHeaders,
): Promise<{ status: number; data: { alreadyProcessed?: boolean; fileSha256: string } }> {
  const res = await client.post(
    "/api/v1/import-batches/start",
    { fileSha256 },
    { headers: { "X-Trace-Id": trace.traceId, "X-Request-Id": trace.requestId } },
  );
  return { status: res.status, data: res.data };
}

export async function completeImportBatch(
  client: AxiosInstance,
  fileSha256: string,
  rowsAccepted: number,
  rowsRejected: number,
  trace: TraceHeaders,
) {
  await client.post(
    `/api/v1/import-batches/${fileSha256}/complete`,
    { rowsAccepted, rowsRejected },
    { headers: { "X-Trace-Id": trace.traceId, "X-Request-Id": trace.requestId } },
  );
}

export async function resolveContract(
  client: AxiosInstance,
  contractNumber: string,
  trace: TraceHeaders,
): Promise<{ id: number; clientId: number }> {
  const res = await client.get(`/api/v1/contracts/by-number/${encodeURIComponent(contractNumber)}`, {
    headers: { "X-Trace-Id": trace.traceId, "X-Request-Id": trace.requestId },
  });
  return res.data;
}

/**
 * Caches contract number → (id, clientId) for the duration of a single file import, avoiding N HTTP
 * calls when many rows share the same contract. Concurrent first hits for the same number share one
 * in-flight request.
 */
export function createContractLookup(
  client: AxiosInstance,
  trace: TraceHeaders,
): (contractNumber: string) => Promise<{ id: number; clientId: number }> {
  const cache = new Map<string, { id: number; clientId: number }>();
  const inflight = new Map<string, Promise<{ id: number; clientId: number }>>();

  return (contractNumber: string) => {
    const key = contractNumber.trim();
    const fast = cache.get(key);
    if (fast) {
      return Promise.resolve(fast);
    }
    let pending = inflight.get(key);
    if (!pending) {
      pending = resolveContract(client, key, trace).then((c) => {
        const value = { id: c.id, clientId: c.clientId };
        cache.set(key, value);
        inflight.delete(key);
        return value;
      });
      inflight.set(key, pending);
    }
    return pending;
  };
}

export async function createPayment(
  client: AxiosInstance,
  contractId: number,
  body: { clientId: number; amount: string; type: string; paymentDate: string },
  idempotencyKey: string,
  trace: TraceHeaders,
) {
  await client.post(`/api/v1/contracts/${contractId}/payments`, body, {
    headers: {
      "Idempotency-Key": idempotencyKey,
      "X-Trace-Id": trace.traceId,
      "X-Request-Id": trace.requestId,
    },
  });
}
