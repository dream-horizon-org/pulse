import { NextRequest, NextResponse } from "next/server";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// In-memory attempt tracker (resets on server restart — fine for demo)
const attempts = new Map<string, number>();

export async function POST(req: NextRequest) {
  const scenario = req.nextUrl.searchParams.get("scenario");
  await sleep(Math.random() * 300 + 200);

  const body = await req.json().catch(() => ({}));
  const mobile: string = body.mobile ?? "unknown";

  if (scenario === "rate_limited") {
    return NextResponse.json(
      { error: "Too many OTP requests. Try again in 10 minutes.", code: "BE4291" },
      { status: 429 },
    );
  }

  const count = (attempts.get(mobile) ?? 0) + 1;
  attempts.set(mobile, count);

  if (count >= 4) {
    attempts.delete(mobile);
    return NextResponse.json(
      { error: "Too many OTP requests. Try again in 10 minutes.", code: "BE4291" },
      { status: 429 },
    );
  }

  return NextResponse.json({
    requestId: `REQ-${Date.now()}`,
    maskedMobile: `******${String(mobile).slice(-4)}`,
    expiresInSeconds: 120,
  });
}
