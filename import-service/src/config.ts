export const config = {
  port: Number(process.env.PORT ?? 3000),
  /** Bind address (0.0.0.0 = all interfaces; helps WSL2 + Windows browser and devcontainers). */
  host: (process.env.HOST ?? "0.0.0.0").trim(),
  paymentApiBaseUrl: process.env.PAYMENT_API_BASE_URL ?? "http://localhost:8080",
  axiosRetries: Number(process.env.AXIOS_RETRIES ?? 4),
  axiosTimeoutMs: Math.max(500, Number(process.env.AXIOS_TIMEOUT_MS ?? 30_000)),
  importRowConcurrency: Math.max(1, Number(process.env.IMPORT_ROW_CONCURRENCY ?? 5)),
  /** Max row tasks queued before awaiting a batch (bounds memory; must be ≥ concurrency). */
  importRowBatchHighWater: Math.max(64, Number(process.env.IMPORT_ROW_BATCH_HIGH_WATER ?? 256)),
};
