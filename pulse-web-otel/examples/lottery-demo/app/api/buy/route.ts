import { NextRequest, NextResponse } from "next/server";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function POST(req: NextRequest) {
  const scenario = req.nextUrl.searchParams.get("scenario") ?? "ok";
  await sleep(Math.random() * 400 + 150);

  if (scenario === "insufficient_balance") {
    return NextResponse.json(
      {
        error: "Insufficient wallet balance",
        code: "BE4021",
        required: 300,
        available: 120,
      },
      { status: 402 },
    );
  }

  if (scenario === "sale_closed") {
    return NextResponse.json(
      { error: "Lottery sale has ended", code: "BE4221" },
      { status: 422 },
    );
  }

  if (scenario === "ticket_taken") {
    return NextResponse.json(
      { error: "Selected ticket already reserved", code: "BE4222" },
      { status: 422 },
    );
  }

  const body = await req.json().catch(() => ({}));
  const ticketCount: number = Array.isArray(body.tickets)
    ? body.tickets.length
    : 1;

  return NextResponse.json({
    orderId: `ORD-${Date.now()}`,
    tickets: body.tickets ?? ["A/0000"],
    totalAmount: ticketCount * (body.pricePerTicket ?? 150),
    status: "confirmed",
    message: "Tickets purchased successfully!",
  });
}
