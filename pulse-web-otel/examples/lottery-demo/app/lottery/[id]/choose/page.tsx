"use client";

import { use } from "react";
import Link from "next/link";
import { useLottery } from "../../../hooks/useLottery";
import { TicketGrid } from "../../../components/TicketGrid";
import { CartFooter } from "../../../components/CartFooter";
import { useCart } from "../../../context/CartContext";

export default function ChoosePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { data: lottery, isLoading } = useLottery(id);
  const { totalTickets } = useCart();

  if (isLoading) {
    return <div className="h-64 bg-white rounded-2xl animate-pulse" />;
  }

  if (!lottery) {
    return (
      <div className="text-center pt-16 text-gray-500">
        Lottery not found.{" "}
        <Link href="/" className="text-sapphire underline">
          Go back
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Link href={`/lottery/${id}`} className="text-sm text-sapphire font-medium">
          ← {lottery.name}
        </Link>
        {totalTickets > 0 && (
          <span className="ml-auto text-xs bg-sapphire text-white px-2.5 py-1 rounded-full">
            {totalTickets} selected
          </span>
        )}
      </div>

      <div className="bg-gold/10 rounded-xl px-4 py-3 text-sm text-sapphire">
        <span className="font-bold">Tap to select.</span> Tap again to deselect.
        Series {lottery.series.join(", ")} — ₹{lottery.price}/ticket.
      </div>

      <TicketGrid series={lottery.series} lotteryId={id} />

      <CartFooter
        lotteryId={id}
        pricePerTicket={lottery.price}
        saleOpen={lottery.status === "live"}
      />
    </div>
  );
}
