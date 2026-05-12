# domains / screen

Brief: [`/docs/components/backend-server.md`](../../../components/backend-server.md) ·
Index: [`../index.md`](../index.md) ·
Peers: [interaction](interaction.md), [heatmap](heatmap.md)

## Purpose

Per-screen root-cause analysis endpoint.

## Source

- `resources/screen/ScreenRcaController.java` (`@Path("/v1/screens")`)
- `service/rca/` (root-cause pipeline)
- `service/rootcause/`
- `module/RcaModule.java`
- `dao/rcajob/`, `dao/rcareport/`, `dao/rootcause/`

## Public surface

| Method | Path |
|---|---|
| GET | `/v1/screens/{screenName}/root-cause` |

Query params: project, time range, optional filters.

## Internal design

- Controller calls into `service/rca/` which orchestrates job submission
  (DAO `rcajob`) and report retrieval (DAO `rcareport`).
- Underlying analysis can run on EMR Serverless / Spark; results stored in
  MySQL.
- Reuses `service/rootcause/` heuristics.

## Dependencies

ClickHouse (telemetry source), MySQL (`rca_jobs`, `rca_reports`,
`root_causes`), EMR/Spark for compute.

## Data contracts

MySQL: `rca_jobs(id, project_id, screen, status)`,
`rca_reports(job_id, payload_json)`.

## Tests

`src/test/java/.../service/rca/*`, `.../service/rootcause/*`.

## Rebuild recipe

1. `ScreenRcaController` GET.
2. `RcaService` (in `service/rca/`) — submit, poll, fetch report.
3. Bind via `RcaModule`.
4. Reuse `IAnalyticalStoreClient` for telemetry reads.
