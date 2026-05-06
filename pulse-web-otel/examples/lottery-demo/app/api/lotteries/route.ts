import { NextRequest, NextResponse } from "next/server";
import lotteries from "@/app/data/mock/lotteries.json";

const sleep = (ms: number) =>
  new Promise((r) => setTimeout(r, ms));

export async function GET(req: NextRequest) {
  const scenario = req.nextUrl.searchParams.get("scenario");

  await sleep(Math.random() * 300 + 100);

  if (scenario === "server_error") {
    return NextResponse.json(
      { error: "Internal server error", code: "BE5001" },
      { status: 500 },
    );
  }

  if (scenario === "slow") {
    await sleep(3500);
  }

  return NextResponse.json({ items: lotteries, total: lotteries.length });
}
