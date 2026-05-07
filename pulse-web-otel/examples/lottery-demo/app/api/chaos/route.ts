import { NextRequest, NextResponse } from "next/server";

// SDK Lab chaos endpoint — returns any HTTP status on demand.
// Usage: GET /api/chaos?status=404  GET /api/chaos?status=500  etc.
export async function GET(req: NextRequest) {
  const status = Number(req.nextUrl.searchParams.get("status") ?? "500");
  const delay = Number(req.nextUrl.searchParams.get("delay") ?? "0");

  if (delay > 0) {
    await new Promise((r) => setTimeout(r, Math.min(delay, 10_000)));
  }

  const messages: Record<number, string> = {
    400: "Bad request",
    401: "Unauthorised",
    403: "Forbidden",
    404: "Not found",
    429: "Too many requests",
    500: "Internal server error",
    502: "Bad gateway",
    503: "Service unavailable",
  };

  return NextResponse.json(
    { error: messages[status] ?? "Error", code: `BE${status}0` },
    { status: isNaN(status) ? 500 : status },
  );
}
