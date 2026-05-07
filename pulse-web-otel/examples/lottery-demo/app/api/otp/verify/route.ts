import { NextRequest, NextResponse } from "next/server";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function POST(req: NextRequest) {
  const scenario = req.nextUrl.searchParams.get("scenario");
  await sleep(Math.random() * 300 + 200);

  const body = await req.json().catch(() => ({}));
  const otp: string = String(body.otp ?? "");

  if (scenario === "expired") {
    return NextResponse.json(
      { error: "OTP has expired. Please request a new one.", code: "BE4002" },
      { status: 400 },
    );
  }

  if (scenario === "wrong_otp" || (otp !== "1234" && otp !== "0000")) {
    return NextResponse.json(
      { error: "Invalid OTP. Please try again.", code: "BE4001" },
      { status: 400 },
    );
  }

  return NextResponse.json({
    accessToken: "mock-jwt-token-abc123",
    user: {
      userId: "mock-user-42",
      name: "Demo User",
      mobile: body.mobile ?? "9999999999",
      onBoarding: false,
      walletBalance: 850,
    },
  });
}
