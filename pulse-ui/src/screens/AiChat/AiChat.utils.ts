export function toMs(ts: number): number {
  if (!ts) return Date.now();
  return ts < 1e12 ? ts * 1000 : ts;
}
