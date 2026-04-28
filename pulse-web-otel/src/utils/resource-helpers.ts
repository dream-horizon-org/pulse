// Shared helpers for resource construction (project id from API key, screen aspect ratio).

export function extractProjectId(apiKey: string): string {
  // Format: 'proj_XXXX_...' → 'proj_XXXX' (first two segments)
  const parts = apiKey.split("_");
  if (parts.length >= 2 && parts[0] === "proj") {
    return `${parts[0]}_${parts[1]}`;
  }
  return apiKey;
}

function gcd(a: number, b: number): number {
  while (b !== 0) {
    const t = b;
    b = a % b;
    a = t;
  }
  return a;
}

export function computeAspectRatio(w: number, h: number): string {
  if (w === 0 || h === 0) return "0:0";
  const divisor = gcd(w, h);
  return `${w / divisor}:${h / divisor}`;
}
