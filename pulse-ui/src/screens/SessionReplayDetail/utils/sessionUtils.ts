import dayjs from "dayjs";

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

export function formatTimestamp(ms: number, sessionStart: Date): string {
  const time = new Date(sessionStart.getTime() + ms);
  return dayjs(time).format("HH:mm:ss.SSS");
}

export function formatPlayerTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

export function getQualityColor(score: number): "teal" | "yellow" | "red" {
  if (score >= 8) return "teal";
  if (score >= 6) return "yellow";
  return "red";
}
