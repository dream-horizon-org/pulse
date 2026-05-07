"use client";

import Link from "next/link";
import { Pulse } from "@dreamhorizonorg/pulse-web";
import type { Lottery } from "../types/lottery";

export function LotteryCard({ lottery }: { lottery: Lottery }) {
  const isExpired = lottery.status === "expired";

  return (
    <Link
      href={`/lottery/${lottery.id}`}
      onClick={() =>
        Pulse.trackEvent("lottery_card_clicked", {
          lottery_id: lottery.id,
          lottery_name: lottery.name,
          prize_amount: lottery.prize,
          status: lottery.status,
        })
      }
      className="block bg-white rounded-2xl shadow-card overflow-hidden hover:shadow-card-hover transition-shadow active:scale-[0.98]"
    >
      {/* Header band */}
      <div
        className={`px-4 py-3 flex items-center justify-between ${
          lottery.tagVariant === "warning"
            ? "bg-gradient-to-r from-gold to-amber-400"
            : "bg-gradient-to-r from-emerald-500 to-teal-400"
        }`}
      >
        <span className="font-bold text-white text-sm tracking-wide">
          {lottery.name}
        </span>
        {isExpired ? (
          <span className="text-xs bg-white/30 text-white px-2 py-0.5 rounded-full">
            Ended
          </span>
        ) : (
          <span className="text-xs bg-white/30 text-white px-2 py-0.5 rounded-full">
            {lottery.timeLeft}
          </span>
        )}
      </div>

      {/* Body */}
      <div className="px-4 py-4">
        <div className="text-3xl font-extrabold text-sapphire mb-1">
          ₹{lottery.prize}
        </div>
        <div className="text-xs text-gray-500 mb-3">{lottery.prizePool}</div>

        <div className="flex items-center justify-between text-xs text-gray-500">
          <span>🏆 {lottery.winnersCount}</span>
          <span>👥 {lottery.buyerCount.toLocaleString("en-IN")} joined</span>
        </div>

        <div className="mt-3 flex items-center justify-between">
          <span className="text-sm font-semibold text-sapphire">
            ₹{lottery.price}/ticket
          </span>
          <span
            className={`text-xs font-semibold px-3 py-1 rounded-full ${
              isExpired
                ? "bg-gray-100 text-gray-400"
                : "bg-sapphire text-white"
            }`}
          >
            {isExpired ? "Draw closed" : "Buy Now"}
          </span>
        </div>
      </div>
    </Link>
  );
}
