import { RunUniversalQueryRequestBody } from "../../../hooks/useQueryResultFromQueryId/useQueryResultFromQueryId.interface";

export function transformQueryResponse(data: RunUniversalQueryRequestBody) {
  // if (!data?.schema?.fields || !data?.rows) return { columns: [], rows: [] };
  // const columns = data.schema.fields.map((field: RunUniversalQueryColumn) => ({
  //   accessor: field.name,
  //   title: field.name,
  // }));
  // const rows = data.rows.map((row) => {
  //   const rowData: {
  //     [key: RunUniversalQueryColumn["name"]]: RunUniversalQueryRow["f"][0]["v"];
  //   } = {};
  //   row.f.forEach((field, index) => {
  //     rowData[data.schema.fields[index].name] = field.v;
  //   });
  //   return rowData;
  // });
  // return { columns, rows };
}
