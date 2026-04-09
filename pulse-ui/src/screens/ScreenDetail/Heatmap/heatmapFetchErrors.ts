import { showNotification } from "@mantine/notifications";

export const HEATMAP_USER_VISIBLE_ERROR_TITLE = "Something went wrong";

export const HEATMAP_USER_VISIBLE_ERROR_BODY =
  "We couldn’t fetch heatmap data for this screen. Please try again.";

const TECHNICAL_HINTS =
  /__error__|mock|scenario|cause:|stack|ECONNREFUSED|500|internal server/i;

export function shouldToastHeatmapErrorDetail(message: string | undefined): boolean {
  if (!message?.trim()) return false;
  const t = message.trim();
  return (
    t.includes("__") ||
    t.length > 120 ||
    TECHNICAL_HINTS.test(t)
  );
}

export function notifyHeatmapTechnicalDetail(detail: string): void {
  showNotification({
    title: "Heatmap request details",
    message: detail.slice(0, 500),
    color: "gray",
    autoClose: 8000,
  });
}
