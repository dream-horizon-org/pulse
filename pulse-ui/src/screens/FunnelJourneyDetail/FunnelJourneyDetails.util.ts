export function mapDetailFilters(detail: any) {
  return (detail.filters || []).map((f: any) => ({
    field: f.field,
    operator: "EQ" as const,
    value: f.value,
  }));
}
