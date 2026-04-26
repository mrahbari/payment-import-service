import { z } from "zod";

/**
 * All settings come from the environment, validated at process startup. Invalid values (bad URL, NaN, etc.) fail
 * fast with a ZodError instead of a late runtime error.
 */
const configSchema = z
  .object({
    port: z.coerce.number().int("PORT must be an integer").min(1, "PORT must be >= 1").max(65535),
    host: z.string().trim().min(1, "HOST must be non-blank after trim"),
    paymentApiBaseUrl: z.string().url("PAYMENT_API_BASE_URL must be a valid http(s) URL"),
    axiosRetries: z.coerce.number().int().min(0),
    axiosTimeoutMs: z.coerce
      .number()
      .refine((n) => !Number.isNaN(n) && n >= 500, { message: "AXIOS_TIMEOUT_MS must be a number >= 500" }),
    // Same bounds as the old Math.max(1, …) / Math.max(64, …) — low env values (e.g. tests using 32) are clamped up.
    importRowConcurrency: z
      .coerce.number()
      .refine((n) => !Number.isNaN(n) && Number.isFinite(n), {
        message: "IMPORT_ROW_CONCURRENCY must be a finite number",
      })
      .transform((n) => Math.max(1, Math.trunc(n))),
    importRowBatchHighWater: z
      .coerce.number()
      .refine((n) => !Number.isNaN(n) && Number.isFinite(n), {
        message: "IMPORT_ROW_BATCH_HIGH_WATER must be a finite number",
      })
      .transform((n) => Math.max(64, Math.trunc(n))),
  })
  .transform((c) => ({
    port: c.port,
    host: c.host,
    /** Base URL of payment-service, without trailing slash (safe URL joins in callers). */
    paymentApiBaseUrl: c.paymentApiBaseUrl.replace(/\/+$/, ""),
    axiosRetries: c.axiosRetries,
    axiosTimeoutMs: c.axiosTimeoutMs,
    importRowConcurrency: c.importRowConcurrency,
    importRowBatchHighWater: c.importRowBatchHighWater,
  }));

const raw = {
  port: process.env.PORT ?? "3000",
  host: (process.env.HOST ?? "0.0.0.0").trim(),
  paymentApiBaseUrl: process.env.PAYMENT_API_BASE_URL ?? "http://localhost:8080",
  axiosRetries: process.env.AXIOS_RETRIES ?? "4",
  axiosTimeoutMs: process.env.AXIOS_TIMEOUT_MS ?? "30000",
  importRowConcurrency: process.env.IMPORT_ROW_CONCURRENCY ?? "5",
  importRowBatchHighWater: process.env.IMPORT_ROW_BATCH_HIGH_WATER ?? "256",
};

export const config = configSchema.parse(raw);

export type AppConfig = z.infer<typeof configSchema>;
