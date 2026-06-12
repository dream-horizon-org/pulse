export const normalizeHeaderKey = (value: string) =>
  value.trim().toLowerCase().replace(/-/g, "_");
