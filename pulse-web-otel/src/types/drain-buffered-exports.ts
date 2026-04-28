import type { IdbSignalBuffer } from "../persistence/indexed-db";

export interface DrainBufferedExportsParams {
  tracesUrl: string;
  logsUrl: string;
  metricsUrl: string;
  apiKey: string;
  meteringSessionId: string;
  buffer: IdbSignalBuffer;
}
