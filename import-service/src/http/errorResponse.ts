import axios from "axios";
import type { Response } from "express";

/** Aligns with payment-service `ApiErrorResponse` (timestamp as ISO-8601 string for JSON). */
export type ApiErrorBody = {
  timestamp: string;
  code: string;
  message: string;
  path: string;
  requestId: string;
};

export function apiErrorBody(
  code: string,
  message: string,
  path: string,
  requestId: string,
): ApiErrorBody {
  return {
    timestamp: new Date().toISOString(),
    code,
    message,
    path,
    requestId,
  };
}

/** Client / validation errors for the import HTTP layer (before payment API mapping). */
export class ImportHttpException extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ImportHttpException";
  }
}

type ParsedUpstream = { code?: string; message?: string };

export function parseUpstreamBody(data: unknown): ParsedUpstream {
  if (data && typeof data === "object") {
    const o = data as Record<string, unknown>;
    const code = typeof o.code === "string" ? o.code : undefined;
    const message = typeof o.message === "string" ? o.message : undefined;
    return { code, message };
  }
  return {};
}

/**
 * Maps payment-service errors and network failures to import-service HTTP responses.
 */
/** Best-effort message for row-level import reporting (import-service → JSON summary). */
export function paymentApiErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const { code: upCode, message: upMsg } = parseUpstreamBody(err.response?.data);
    const s = err.response?.status;
    if (upMsg) {
      const prefix = upCode && upCode !== "INTERNAL_ERROR" ? `[${upCode}] ` : "";
      return `${prefix}${upMsg}`.trim();
    }
    if (s === 404) {
      return "Contract not found for the given number";
    }
    if (s != null) {
      return `Payment API returned HTTP ${s}`;
    }
    return err.message || "Payment API request failed";
  }
  if (err instanceof Error) {
    return err.message;
  }
  return "Unknown error";
}

/**
 * Enriches the generic payment-API error with the contract number from the file row, so operators see
 * *which* contract failed (e.g. 404) without reading server logs.
 */
export function paymentApiErrorMessageForRow(
  err: unknown,
  context: { contractNumber: string },
): string {
  const base = paymentApiErrorMessage(err);
  if (axios.isAxiosError(err) && err.response?.status === 404) {
    return `No contract with number '${context.contractNumber}' (payment API returned 404)`;
  }
  return base;
}

export function mapPaymentApiFailure(
  err: unknown,
  path: string,
  requestId: string,
): { status: number; body: ApiErrorBody } {
  if (err instanceof ImportHttpException) {
    return {
      status: err.status,
      body: apiErrorBody(err.code, err.message, path, requestId),
    };
  }

  if (axios.isAxiosError(err)) {
    const { code: axCode, message: axMsg } = err;
    const status = err.response?.status;
    const { code: upCode, message: upMsg } = parseUpstreamBody(err.response?.data);
    const message = upMsg ?? axMsg ?? "Payment API request failed";

    if (!err.response) {
      if (axCode === "ECONNABORTED" || axMsg.toLowerCase().includes("timeout")) {
        return {
          status: 503,
          body: apiErrorBody("PAYMENT_API_TIMEOUT", "Payment API request timed out", path, requestId),
        };
      }
      if (axCode === "ECONNREFUSED" || axCode === "ENOTFOUND" || axCode === "ECONNRESET") {
        return {
          status: 503,
          body: apiErrorBody("PAYMENT_API_UNAVAILABLE", "Cannot reach payment API", path, requestId),
        };
      }
      return {
        status: 503,
        body: apiErrorBody("PAYMENT_API_UNAVAILABLE", message, path, requestId),
      };
    }

    if (status === 400) {
      return {
        status: 400,
        body: apiErrorBody(upCode ?? "PAYMENT_API_BAD_REQUEST", message, path, requestId),
      };
    }
    if (status === 404) {
      return {
        status: 422,
        body: apiErrorBody("CONTRACT_NOT_FOUND", message, path, requestId),
      };
    }
    if (status === 409) {
      return {
        status: 409,
        body: apiErrorBody(upCode ?? "CONFLICT", message, path, requestId),
      };
    }
    if (status === 429) {
      return {
        status: 429,
        body: apiErrorBody(upCode ?? "RATE_LIMITED", message, path, requestId),
      };
    }
    if (status === 503 || status === 502) {
      return {
        status,
        body: apiErrorBody(upCode ?? "PAYMENT_API_ERROR", message, path, requestId),
      };
    }
    if (status != null && status >= 500) {
      return {
        status: 502,
        body: apiErrorBody(upCode ?? "PAYMENT_API_ERROR", message, path, requestId),
      };
    }
    if (status != null) {
      return {
        status: 502,
        body: apiErrorBody(upCode ?? "PAYMENT_API_ERROR", message, path, requestId),
      };
    }
  }

  const msg = err instanceof Error ? err.message : "An unexpected error occurred";
  return {
    status: 500,
    body: apiErrorBody("INTERNAL_ERROR", msg, path, requestId),
  };
}

export function sendImportError(
  res: Response,
  err: unknown,
  path: string,
  requestId: string,
  traceId: string,
): void {
  const { status, body } = mapPaymentApiFailure(err, path, requestId);
  const payload = JSON.stringify({ msg: "import_http_error", status, code: body.code, traceId });
  if (status >= 500) {
    console.error(payload);
  } else {
    console.warn(payload);
  }
  res.status(status).json(body);
}
