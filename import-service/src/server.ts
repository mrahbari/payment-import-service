import { createHash, randomUUID } from "node:crypto";
import { createReadStream, createWriteStream, promises as fs } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Transform } from "node:stream";
import busboy from "busboy";
import express from "express";
import pLimit from "p-limit";
import client from "prom-client";
import { config } from "./config.js";
import {
  completeImportBatch,
  createPayment,
  createPaymentClient,
  resolveContract,
  startImportBatch,
} from "./http/paymentClient.js";
import { ImportHttpException, paymentApiErrorMessageForRow, sendImportError } from "./http/errorResponse.js";
import { type RowError, pushCappedError } from "./importResult.js";
import { ParserFactory } from "./parsing/ParserFactory.js";
import { validateRowWithMessage } from "./validateRow.js";

const isVitest = process.env.VITEST === "true";

if (!isVitest) {
  client.collectDefaultMetrics({ prefix: "import_" });
}

const importDuration = isVitest
  ? ({ startTimer: () => () => undefined } as unknown as client.Histogram<string>)
  : new client.Histogram({
        name: "import_file_processing_seconds",
        help: "End-to-end file import duration",
        buckets: [0.05, 0.1, 0.5, 1, 2, 5, 15, 60],
      });

const importRowsCounter = isVitest
  ? ({ inc: () => undefined } as unknown as client.Counter<"outcome">)
  : new client.Counter({
        name: "import_rows_total",
        help: "Rows processed by outcome",
        labelNames: ["outcome"],
      });

export const app = express();
const paymentClient = createPaymentClient();

app.get("/", (_req, res) => {
  res.json({
    service: "import-service",
    import: {
      browser: "GET /payments/import — upload CSV/XML in the browser",
      api: "POST /payments/import — multipart field name: file",
    },
    metrics: "/metrics",
    paymentApi: config.paymentApiBaseUrl,
  });
});

app.get("/metrics", async (_req, res) => {
  res.set("Content-Type", client.register.contentType);
  res.send(await client.register.metrics());
});

const importUploadPage = join(process.cwd(), "static", "import.html");

/** Browser upload UI; POST /payments/import handles the multipart upload. */
app.get("/payments/import", (_req, res) => {
  res.sendFile(importUploadPage, (err) => {
    if (err) {
      res.status(500).type("text/plain").send("Import page missing (expected static/import.html next to this service).");
    }
  });
});

app.post("/payments/import", (req, res) => {
  const endTimer = importDuration.startTimer();
  const trace = { traceId: req.header("X-Trace-Id") ?? randomUUID(), requestId: randomUUID() };
  const path = req.path;
  res.setHeader("X-Trace-Id", trace.traceId);
  res.setHeader("X-Request-Id", trace.requestId);

  const bb = busboy({ headers: req.headers });
  let filePromise: Promise<void> | undefined;

  bb.on("file", (name, fileStream, info) => {
    if (name !== "file") {
      fileStream.resume();
      return;
    }
    filePromise = handleUploadedFile(fileStream, info.mimeType, info.filename, trace).then(
      (summary) => {
        res.status(200).json(summary);
      },
      (err: unknown) => {
        sendImportError(res, err, path, trace.requestId, trace.traceId);
      },
    );
  });

  bb.on("finish", () => {
    if (!filePromise) {
      sendImportError(
        res,
        new ImportHttpException(400, "BAD_REQUEST", "Expected multipart field 'file'"),
        path,
        trace.requestId,
        trace.traceId,
      );
    } else {
      void filePromise.finally(() => endTimer());
    }
  });

  req.pipe(bb);
});

async function handleUploadedFile(
  fileStream: NodeJS.ReadableStream,
  mimeType: string,
  filename: string,
  trace: { traceId: string; requestId: string },
) {
  const tmpPath = join(tmpdir(), `pay-import-${randomUUID()}`);
  const hash = createHash("sha256");
  const write = createWriteStream(tmpPath);
  const hasher = new Transform({
    transform(chunk: Buffer, _enc, cb) {
      hash.update(chunk);
      cb(null, chunk);
    },
  });

  try {
    await new Promise<void>((resolve, reject) => {
      fileStream.pipe(hasher).pipe(write);
      write.on("finish", resolve);
      write.on("error", reject);
      fileStream.on("error", reject);
      hasher.on("error", reject);
    });
  } catch (e) {
    await fs.unlink(tmpPath).catch(() => undefined);
    const msg = e instanceof Error ? e.message : "Upload failed";
    throw new ImportHttpException(400, "UPLOAD_FAILED", msg);
  }

  const fileSha256 = hash.digest("hex");

  const start = await startImportBatch(paymentClient, fileSha256, trace);
  if (start.status === 200 && start.data.alreadyProcessed) {
    await fs.unlink(tmpPath).catch(() => undefined);
    return { fileSha256, skipped: true, message: "File already imported" };
  }

  const strategy = ParserFactory.resolve(mimeType, filename);
  const errors: RowError[] = [];
  let totalRows = 0;
  let successfulRows = 0;
  let failedRows = 0;

  const readStream = createReadStream(tmpPath);
  const limit = pLimit(config.importRowConcurrency);

  await strategy.parseStream(readStream, (row, rowIndex) =>
    limit(async () => {
      totalRows++;
      const validation = validateRowWithMessage(row, rowIndex);
      if (!validation.ok) {
        failedRows++;
        importRowsCounter.inc({ outcome: "invalid" });
        pushCappedError(errors, rowIndex, validation.error);
        console.warn(
          JSON.stringify({
            msg: "import_row_failed",
            phase: "validation",
            row: rowIndex,
            error: validation.error,
            traceId: trace.traceId,
          }),
        );
        return;
      }
      const v = validation.data;
      try {
        const contract = await resolveContract(paymentClient, v.contractNumber, trace);
        const idem = `import:${fileSha256}:row:${rowIndex}`;
        await createPayment(
          paymentClient,
          contract.id,
          {
            clientId: contract.clientId,
            amount: v.amount,
            type: v.type.toUpperCase(),
            paymentDate: v.paymentDate,
          },
          idem,
          trace,
        );
        successfulRows++;
        importRowsCounter.inc({ outcome: "accepted" });
      } catch (e) {
        failedRows++;
        importRowsCounter.inc({ outcome: "error" });
        const msg = paymentApiErrorMessageForRow(e, { contractNumber: v.contractNumber });
        pushCappedError(errors, rowIndex, msg);
        console.warn(
          JSON.stringify({
            msg: "import_row_failed",
            phase: "payment_api",
            row: rowIndex,
            error: msg,
            traceId: trace.traceId,
          }),
        );
      }
    }),
  );

  await completeImportBatch(paymentClient, fileSha256, successfulRows, failedRows, trace);
  await fs.unlink(tmpPath).catch(() => undefined);

  console.log(
    JSON.stringify({
      msg: "import_summary",
      fileSha256,
      format: strategy.format,
      totalRows,
      successfulRows,
      failedRows,
      errorsReported: errors.length,
      traceId: trace.traceId,
    }),
  );

  return {
    fileSha256,
    format: strategy.format,
    totalRows,
    successfulRows,
    failedRows,
    errors,
    rowsAccepted: successfulRows,
    rowsRejected: failedRows,
  };
}

if (!isVitest) {
  const server = app.listen(config.port, config.host as string, () => {
    const base =
      !config.host || config.host === "0.0.0.0" || config.host === "::" || config.host === "[::]"
        ? `http://localhost:${config.port}`
        : `http://${config.host}:${config.port}`;
    const lines = [
      "----------------------------------------------------------------",
      `  import-service  ${base}/payments/import  (upload UI)`,
      `  payment API     ${config.paymentApiBaseUrl}`,
      "----------------------------------------------------------------",
    ];
    console.log(lines.join("\n"));
  });

  server.on("error", (err: NodeJS.ErrnoException) => {
    if (err.code === "EADDRINUSE") {
      console.error(
        JSON.stringify({
          msg: "listen_failed",
          code: err.code,
          port: config.port,
          hint: `Port ${config.port} is in use. Stop the other Node process (e.g. run-all.sh or npm run dev) or use PORT=3001 npm start`,
        }),
      );
      process.exit(1);
    }
    throw err;
  });
}
