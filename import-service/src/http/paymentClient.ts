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
