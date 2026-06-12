export type BufferedSignalType = "trace" | "log" | "metric";

/** Serialized OTLP request body + HTTP metadata for replay. */
export interface BufferedOtlpEnvelope {
  bodyB64: string;
  contentType: string;
  contentEncoding?: "gzip";
}

export interface BufferedSignalRow {
  id?: number;
  signalType: BufferedSignalType;
  envelope: BufferedOtlpEnvelope;
  timestamp: number;
  retryCount: number;
}
