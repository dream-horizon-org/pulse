export type OtlpSignalKind = "trace" | "log" | "metric";

export interface PersistMeta {
  contentType: string;
  contentEncoding?: "gzip";
}
