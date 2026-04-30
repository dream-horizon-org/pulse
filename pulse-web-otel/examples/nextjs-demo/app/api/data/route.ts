/**
 * App Router API route — /api/data
 * Returns mock stats. Randomly fails ~30% of the time to demo error tracking.
 */
import { NextResponse } from "next/server";

export async function GET(): Promise<NextResponse> {
  await new Promise((r) => setTimeout(r, 80)); // simulate latency

  if (Math.random() < 0.3) {
    return NextResponse.json(
      { error: "Upstream service unavailable" },
      { status: 503 },
    );
  }

  return NextResponse.json({
    activeUsers: Math.floor(Math.random() * 500) + 100,
    ordersToday: Math.floor(Math.random() * 80) + 20,
    revenue: (Math.random() * 5000 + 1000).toFixed(2),
    timestamp: new Date().toISOString(),
  });
}
