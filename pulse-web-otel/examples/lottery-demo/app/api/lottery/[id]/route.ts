import { NextRequest, NextResponse } from "next/server";
import lotteries from "@/app/data/mock/lotteries.json";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function GET(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  await sleep(Math.random() * 250 + 100);

  if (id === "demo-missing") {
    return NextResponse.json(
      { error: "Lottery not found", code: "BE4041" },
      { status: 404 },
    );
  }

  const lottery = lotteries.find((l) => l.id === id);
  if (!lottery) {
    return NextResponse.json(
      { error: "Lottery not found", code: "BE4041" },
      { status: 404 },
    );
  }

  if (lottery.status === "expired") {
    return NextResponse.json(
      { error: "Lottery sale has ended", code: "BE4101", lottery },
      { status: 410 },
    );
  }

  return NextResponse.json(lottery);
}
