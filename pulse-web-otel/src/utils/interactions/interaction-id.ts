export function randomInteractionId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `int_${Date.now()}_${Math.random().toString(16).slice(2)}`;
}
