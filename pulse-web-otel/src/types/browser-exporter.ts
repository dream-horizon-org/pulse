import type { IdbSignalBuffer } from "../persistence/indexed-db";
import type { OtlpSignalKind } from "./otlp-transport";

export interface PulseBrowserExporterOptions {
  useProtobuf: boolean;
  useGzip: boolean;
  diskBuffer: { enabled: boolean; buffer: IdbSignalBuffer };
  signalKind: OtlpSignalKind;
}
