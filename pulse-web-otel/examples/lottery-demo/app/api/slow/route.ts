import { NextResponse } from "next/server";

// Deliberately slow endpoint — used to trigger poor LCP / TTFB web vitals.
// SDK Lab "Load slow hero" and "/api/slow" network span tests use this.
export async function GET() {
  await new Promise((r) => setTimeout(r, 4000));
  return NextResponse.json({
    message: "slow response",
    imageUrl: "https://placehold.co/800x400/1B2E4B/F5A623?text=Slow+Hero+Image",
  });
}
