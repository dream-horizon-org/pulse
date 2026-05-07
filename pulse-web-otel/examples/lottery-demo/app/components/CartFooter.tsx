"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Pulse } from "@dreamhorizon/pulse-web";
import { useCart } from "../context/CartContext";
import { api, ApiError } from "../lib/api";

interface Props {
  lotteryId: string;
  pricePerTicket: number;
  saleOpen: boolean;
}

export function CartFooter({ lotteryId, pricePerTicket, saleOpen }: Props) {
  const { totalTickets, totalAmount, items, clearCart } = useCart();
  const [expanded, setExpanded] = useState(false);
  const [buying, setBuying] = useState(false);
  const [scenario, setScenario] = useState<string>("ok");
  const router = useRouter();

  if (totalTickets === 0) return null;

  async function handleBuy() {
    setBuying(true);
    Pulse.trackEvent("cart_checkout_click", {
      lottery_id: lotteryId,
      ticket_count: totalTickets,
      total_amount: totalAmount(pricePerTicket),
    });

    try {
      await api.post(`/api/buy?scenario=${scenario}`, {
        lotteryId,
        tickets: items.map((i) => `${i.series}/${i.number}`),
        pricePerTicket,
      });

      Pulse.trackEvent("ticket_purchased", {
        lottery_id: lotteryId,
        ticket_count: totalTickets,
        amount: totalAmount(pricePerTicket),
      });

      clearCart();
      router.push("/orders");
    } catch (err) {
      const code =
        err instanceof ApiError ? err.code : "UNKNOWN";
      Pulse.trackEvent("purchase_failed", {
        lottery_id: lotteryId,
        error_code: code,
        scenario,
      });
    } finally {
      setBuying(false);
    }
  }

  return (
    <div className="fixed bottom-16 left-0 right-0 z-30">
      <div className="max-w-2xl mx-auto px-4">
        <div className="bg-sapphire rounded-2xl shadow-xl overflow-hidden">
          {/* Collapsed summary row */}
          <button
            onClick={() => setExpanded((e) => !e)}
            className="w-full px-4 py-3 flex items-center justify-between text-white"
          >
            <span className="font-semibold text-sm">
              🎟 {totalTickets} ticket{totalTickets !== 1 ? "s" : ""} added
            </span>
            <span className="text-gold font-bold">
              ₹{totalAmount(pricePerTicket).toLocaleString("en-IN")}
            </span>
          </button>

          {expanded && (
            <div className="border-t border-white/10 px-4 pb-3">
              {/* Ticket list */}
              <div className="mt-2 max-h-32 overflow-y-auto space-y-1">
                {items.map((item) => (
                  <div
                    key={`${item.series}/${item.number}`}
                    className="flex justify-between text-xs text-white/80"
                  >
                    <span>
                      {item.series}/{item.number}
                    </span>
                    <span>×{item.quantity}</span>
                  </div>
                ))}
              </div>

              {/* Scenario selector for SDK testing */}
              <div className="mt-3">
                <div className="text-[10px] text-white/40 mb-1 uppercase tracking-wider">
                  Simulate checkout scenario
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {["ok", "insufficient_balance", "sale_closed", "ticket_taken"].map(
                    (s) => (
                      <button
                        key={s}
                        onClick={() => setScenario(s)}
                        className={`text-[10px] px-2 py-0.5 rounded-full font-mono transition-colors ${
                          scenario === s
                            ? "bg-gold text-sapphire font-bold"
                            : "bg-white/10 text-white/60"
                        }`}
                      >
                        {s}
                      </button>
                    ),
                  )}
                </div>
              </div>

              <button
                onClick={handleBuy}
                disabled={buying || (!saleOpen && scenario === "ok")}
                className="mt-3 w-full py-3 rounded-xl font-bold text-sm bg-gold text-sapphire disabled:opacity-50 active:scale-[0.98] transition-all"
              >
                {buying
                  ? "Processing…"
                  : !saleOpen && scenario === "ok"
                    ? "Sale Closed"
                    : `Buy Now · ₹${totalAmount(pricePerTicket).toLocaleString("en-IN")}`}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
