package org.dreamhorizon.pulses3archiver.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum ServiceError {
  SERVICE_UNKNOWN_EXCEPTION("pulse-s3-archiver-UNKNOWN", "Something went wrong", 500),
  KAFKA_CONSUMER_ERROR("S3A1001", "Kafka consumer failure", 500),
  OTLP_PARSE_ERROR("S3A1002", "OTLP protobuf decode failed", 422),
  PARQUET_WRITE_ERROR("S3A1003", "Parquet write failed", 500),
  S3_UPLOAD_ERROR("S3A1004", "S3 multipart upload failed", 502),
  SCHEMA_MISMATCH("S3A1005", "Row does not match Avro schema", 422),
  DLQ_WRITE_ERROR("S3A1006", "DLQ write failed", 500);

  private final String errorCode;
  private final String errorMessage;
  private final int httpStatusCode;
}
