/** Pulse session flags — isolated so trace/errorHandler avoid importing `config` (breaks cycles). */

let isShutdown = false;
let isStarted = false;

export function getIsShutdown(): boolean {
  return isShutdown;
}

export function getIsStarted(): boolean {
  return isStarted;
}

export function markPulseSessionStarted(): void {
  isStarted = true;
}

export function markPulseSessionShutdown(): void {
  isShutdown = true;
}
