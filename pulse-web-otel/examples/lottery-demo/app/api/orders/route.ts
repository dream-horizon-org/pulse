import { NextResponse } from "next/server";
import orders from "@/app/data/mock/orders.json";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function GET() {
  await sleep(Math.random() * 250 + 100);
  return NextResponse.json({ items: orders, total: orders.length });
}
