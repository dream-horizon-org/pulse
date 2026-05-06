"use client";

import { use } from "react";
import Link from "next/link";
import { PulseWeb } from "@dreamhorizon/pulse-web";
import { useLottery } from "../../hooks/useLottery";
import { PrizeBreakupTable } from "../../components/PrizeBreakupTable";
import { CartFooter } from "../../components/CartFooter";
import { ApiError } from "../../lib/api";

export default function LotteryDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { data: lottery, isLoading, isError, error } = useLottery(id);

  if (isLoading) {
    return (
      <div className="space-y-3 animate-pulse">
        <div className="h-10 bg-white rounded-xl" />
        <div className="h-32 bg-white rounded-xl" />
        <div className="h-48 bg-white rounded-xl" />
      </div>
    );
  }

  if (isError) {
    const apiErr = error as ApiError;
    const isExpired = apiErr?.status === 410;
    const isNotFound = apiErr?.status === 404;

    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] text-center">
        <div className="text-5xl mb-4">{isExpired ? "🔒" : "🔍"}</div>
        <h2 className="text-xl font-bold text-sapphire mb-2">
          {isExpired ? "Draw has ended" : isNotFound ? "Lottery not found" : "Failed to load"}
        </h2>
        <p className="text-sm text-gray-500 mb-6">
          {isExpired
            ? "This lottery's sale window has closed."
            : isNotFound
              ? "This lottery doesn't exist or the link is broken."
              : (error as Error)?.message}
        </p>
        <Link
          href="/"
          className="px-6 py-2.5 bg-sapphire text-white rounded-xl text-sm font-semibold"
        >
          Browse active lotteries
        </Link>
      </div>
    );
  }

  if (!lottery) return null;

  const isLive = lottery.status === "live";

  function handleRandomPick() {
    const series = lottery!.series[Math.floor(Math.random() * lottery!.series.length)];
    const num = String(Math.floor(Math.random() * 10_000)).padStart(4, "0");
    PulseWeb.trackEvent("ticket_pick_random", {
      lottery_id: lottery!.id,
      ticket: `${series}/${num}`,
      series,
    });
    alert(`🎟 Random ticket: ${series}/${num} added!`);
  }

  return (
    <div className="space-y-4">
      {/* Back */}
      <Link href="/" className="inline-flex items-center gap-1 text-sm text-sapphire font-medium">
        ← Back
      </Link>

      {/* Header */}
      <div
        className={`rounded-2xl p-5 text-white ${
          lottery.tagVariant === "warning"
            ? "bg-gradient-to-br from-amber-500 to-orange-600"
            : "bg-gradient-to-br from-emerald-500 to-teal-600"
        }`}
      >
        <div className="text-xs font-semibold uppercase tracking-widest opacity-80 mb-1">
          {isLive ? lottery.timeLeft : "Draw closed"}
        </div>
        <h1 className="text-2xl font-extrabold mb-1">{lottery.name}</h1>
        <div className="text-3xl font-black">₹{lottery.prize}</div>
        <div className="text-sm opacity-80 mt-1">{lottery.prizePool}</div>
        <div className="flex items-center gap-4 mt-3 text-sm opacity-90">
          <span>🏆 {lottery.winnersCount}</span>
          <span>👥 {lottery.buyerCount.toLocaleString("en-IN")} joined</span>
        </div>
      </div>

      {/* Actions */}
      {isLive && (
        <div className="flex gap-3">
          <button
            onClick={handleRandomPick}
            className="flex-1 py-3 bg-gold text-sapphire rounded-xl font-bold text-sm active:scale-95 transition-transform"
          >
            🎲 Random pick
          </button>
          <Link
            href={`/lottery/${id}/choose`}
            onClick={() =>
              PulseWeb.trackEvent("ticket_choose_screen_open", { lottery_id: id })
            }
            className="flex-1 py-3 bg-sapphire text-white rounded-xl font-bold text-sm text-center active:scale-95 transition-transform"
          >
            Choose tickets
          </Link>
        </div>
      )}

      {/* Prize table */}
      <PrizeBreakupTable items={lottery.prizeBreakup} />

      {/* Draw info */}
      <div className="bg-white rounded-xl shadow-card p-4 text-sm">
        <div className="font-semibold text-sapphire mb-2">Draw details</div>
        <div className="flex justify-between text-gray-600 py-1.5 border-b border-gray-50">
          <span>Draw date</span>
          <span className="font-medium">{lottery.drawDate}</span>
        </div>
        <div className="flex justify-between text-gray-600 py-1.5 border-b border-gray-50">
          <span>Draw time</span>
          <span className="font-medium">{lottery.drawTime}</span>
        </div>
        <div className="flex justify-between text-gray-600 py-1.5">
          <span>Price per ticket</span>
          <span className="font-bold text-sapphire">₹{lottery.price}</span>
        </div>
      </div>

      <CartFooter
        lotteryId={id}
        pricePerTicket={lottery.price}
        saleOpen={isLive}
      />
    </div>
  );
}
