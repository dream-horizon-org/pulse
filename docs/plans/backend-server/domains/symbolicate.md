# domains / symbolicate

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md)

## Purpose

Upload mapping/symbol files (ProGuard/R8, iOS dSYM, etc.) used to symbolicate
crash stack traces.

## Source

- `resources/symbolicate/MappingFileUpload.java`
  (`@Path("/v1/symbolicate")`, POST `/file/upload`)
- `errorgrouping/Symbolicator.java`,
  `errorgrouping/IosLlvmSymbolicator.java`,
  `errorgrouping/service/*` (consumers — symbolicate on crash ingest)

## Public surface

| Method | Path |
|---|---|
| POST | `/v1/symbolicate/file/upload` |

Multipart form: project, platform, version, file. Stored in S3 (via
`S3BucketClient` from `MainModule`).

## Internal design

- Resource validates auth + project, streams file to S3 under a stable key,
  records metadata in MySQL.
- Crash-ingest path (separate; see ANR consumer in `verticle/`) loads the
  matching mapping and resolves frames.

## Dependencies

S3, MySQL (mapping registry), `errorgrouping/` symbolicators.

## Data contracts

S3 keys: `symbols/{projectId}/{platform}/{version}/...`.

## Tests

`src/test/java/.../resources/symbolicate/*`,
`src/test/java/.../errorgrouping/*`.

## Rebuild recipe

1. Multipart POST resource accepting file + metadata.
2. Stream to `S3BucketClient`.
3. Insert metadata row keyed by `(projectId, platform, version)`.
4. Symbolicator implementations under `errorgrouping/` consume during crash
   processing.
